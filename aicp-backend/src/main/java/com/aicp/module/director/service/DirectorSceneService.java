package com.aicp.module.director.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.director.domain.DirectorDocument;
import com.aicp.module.director.entity.*;
import com.aicp.module.director.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectorSceneService {

    private final DirectorSceneMapper sceneMapper;
    private final DirectorDraftMapper draftMapper;
    private final DirectorRevisionMapper revisionMapper;
    private final DirectorRevisionAssetMapper revisionAssetMapper;
    private final DirectorDocumentValidator validator;
    private final ObjectMapper objectMapper;

    /** 获取或创建 DirectorScene */
    @Transactional
    public DirectorScene getOrCreateScene(Long shotUnitId) {
        var existing = sceneMapper.selectOne(
                new LambdaQueryWrapper<DirectorScene>().eq(DirectorScene::getShotUnitId, shotUnitId));
        if (existing != null) return existing;

        DirectorScene scene = new DirectorScene();
        scene.setUuid(UUID.randomUUID().toString());
        scene.setShotUnitId(shotUnitId);
        sceneMapper.insert(scene);
        return scene;
    }

    /** 读取当前草稿 */
    public DirectorDraft getDraft(Long sceneId) {
        var scene = sceneMapper.selectById(sceneId);
        if (scene == null || scene.getCurrentDraftId() == null) return null;
        return draftMapper.selectById(scene.getCurrentDraftId());
    }

    /** 乐观锁保存草稿 */
    @Transactional
    public DirectorDraft saveDraft(Long sceneId, int expectedVersion, DirectorDocument document, Long actorId) {
        var scene = getOrCreateScene(sceneId);
        DirectorDraft draft = scene.getCurrentDraftId() != null
                ? draftMapper.selectById(scene.getCurrentDraftId()) : null;

        if (draft != null) {
            if (draft.getRowVersion() != expectedVersion) {
                throw new OptimisticLockingFailureException(
                        "草稿版本冲突: 期望 " + expectedVersion + "，实际 " + draft.getRowVersion());
            }
            try {
                draft.setDocumentJson(objectMapper.writeValueAsString(document));
            } catch (Exception e) {
                throw new RuntimeException("导演文档序列化失败", e);
            }
            draftMapper.updateById(draft);
        } else {
            draft = new DirectorDraft();
            draft.setUuid(UUID.randomUUID().toString());
            draft.setSceneId(sceneId);
            try {
                draft.setDocumentJson(objectMapper.writeValueAsString(document));
            } catch (Exception e) {
                throw new RuntimeException("导演文档序列化失败", e);
            }
            draft.setCreatedBy(actorId);
            draftMapper.insert(draft);

            scene.setCurrentDraftId(draft.getId());
            sceneMapper.updateById(scene);
        }

        log.debug("草稿已保存: scene={}, version={}", sceneId, draft.getRowVersion());
        return draft;
    }

    /** 校验当前草稿 */
    public DirectorDocumentValidator.ValidationResult validate(Long sceneId) {
        DirectorDraft draft = getDraft(sceneId);
        if (draft == null) {
            return DirectorDocumentValidator.ValidationResult.fail(List.of("没有可用的草稿"));
        }
        try {
            DirectorDocument doc = objectMapper.readValue(draft.getDocumentJson(), DirectorDocument.class);
            return validator.validate(doc);
        } catch (Exception e) {
            return DirectorDocumentValidator.ValidationResult.fail(List.of("文档解析失败: " + e.getMessage()));
        }
    }

    /** 冻结不可变 revision */
    @Transactional
    public DirectorRevision freeze(Long sceneId, String idempotencyKey, Long actorId) {
        // 幂等检查
        if (idempotencyKey != null) {
            var existing = revisionMapper.selectList(
                    new LambdaQueryWrapper<DirectorRevision>()
                            .eq(DirectorRevision::getSceneId, sceneId)
                            .eq(DirectorRevision::getIdempotencyKey, idempotencyKey));
            if (!existing.isEmpty()) return existing.get(0);
        }

        DirectorDraft draft = getDraft(sceneId);
        if (draft == null) throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "没有可冻结的草稿");

        DirectorDocument doc;
        try {
            doc = objectMapper.readValue(draft.getDocumentJson(), DirectorDocument.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "草稿 JSON 解析失败: " + e.getMessage());
        }

        // 冻结前必须通过校验（errors 阻止冻结，warnings 不阻止）
        var result = validator.validate(doc);
        if (!result.valid()) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(),
                    "草稿校验失败: " + String.join("; ", result.errors()));
        }

        // 规范化 JSON（确保哈希稳定）
        String canonical;
        try {
            canonical = objectMapper.writeValueAsString(doc);
        } catch (Exception e) {
            throw new RuntimeException("序列化失败", e);
        }

        String hash = sha256(canonical);

        // 计算 revision 号
        long count = revisionMapper.selectCount(
                new LambdaQueryWrapper<DirectorRevision>().eq(DirectorRevision::getSceneId, sceneId));
        int nextRev = (int) count + 1;

        DirectorRevision revision = new DirectorRevision();
        revision.setUuid(UUID.randomUUID().toString());
        revision.setSceneId(sceneId);
        revision.setRevision(nextRev);
        revision.setDocumentJson(canonical);
        revision.setDocumentHash(hash);
        revision.setIdempotencyKey(idempotencyKey);
        revision.setFrozenBy(actorId);
        revisionMapper.insert(revision);

        // 更新场景的当前 revision 引用
        var scene = sceneMapper.selectById(sceneId);
        scene.setCurrentRevisionId(revision.getId());
        sceneMapper.updateById(scene);

        log.info("Revision 已冻结: scene={}, revision={}, hash={}", sceneId, nextRev, hash);
        return revision;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 失败", e);
        }
    }
}
