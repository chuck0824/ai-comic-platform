package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.dto.ContentProjectRequests.GenerationJobRequest;
import com.aicp.module.contentproject.dto.ContentProjectViews.DraftView;
import com.aicp.module.contentproject.dto.ContentProjectViews.GenerationJobView;
import com.aicp.module.contentproject.dto.LocalRewriteRequests.LocalRewriteAdoptRequest;
import com.aicp.module.contentproject.dto.LocalRewriteRequests.LocalRewriteRequest;
import com.aicp.module.contentproject.dto.LocalRewriteRequests.PatchOp;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * R2-A.3 局部 AI 改写：创建候选任务 + 服务端 Patch 采用（尾部向前应用，hash 不匹配整批失败）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalRewriteService {

    private final ContentUnitMapper unitMapper;
    private final ContentVersionMapper versionMapper;
    private final ProjectAccessService accessService;
    private final ContentGenerationJobService generationJobService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> createRewrite(Long userId, Long unitId, LocalRewriteRequest request) {
        ContentUnit unit = requireUnit(unitId);
        accessService.require(unit.getProjectId(), userId, Action.RUN_CONTENT_AI);
        requireRevision(unit, request.contentUnitRevision());

        String plain = currentPlainText(unit);
        validateSelection(plain, request);

        GenerationJobRequest jobRequest = new GenerationJobRequest(
                "local_rewrite",
                "content_unit",
                unitId,
                Map.of(),
                request.operation() == null ? "rewrite" : request.operation(),
                "v1-local-rewrite");
        String idempotencyKey = "local-rewrite-" + unitId + "-" + UUID.randomUUID();
        GenerationJobView job = generationJobService.createJob(
                userId, unit.getProjectId(), jobRequest, idempotencyKey);

        // 候选版本：保存 before/after 与建议 Patch，供前端展示；不直接改正文
        String selected = plain.substring(request.startOffset(), request.endOffset());
        String replacement = "[AI改写建议] " + selected;
        Map<String, Object> candidatePayload = new LinkedHashMap<>();
        candidatePayload.put("before", selected);
        candidatePayload.put("after", replacement);
        candidatePayload.put("baseVersionId", request.baseVersionId());
        candidatePayload.put("baseContentHash", request.contentHash());
        candidatePayload.put("patches", List.of(Map.of(
                "startOffset", request.startOffset(),
                "endOffset", request.endOffset(),
                "expectedTextHash", sha256(selected),
                "replacement", replacement,
                "reason", request.operation() == null ? "rewrite" : request.operation())));

        ContentVersion candidate = new ContentVersion();
        candidate.setProjectId(unit.getProjectId());
        candidate.setContentUnitId(unitId);
        candidate.setVersionNo(0);
        candidate.setStatus("candidate");
        candidate.setSource("ai_local_rewrite");
        candidate.setGenerationJobId(job.id());
        candidate.setContentJson(toJson(candidatePayload));
        candidate.setPlainText(replacement);
        candidate.setContentHash(sha256(toJson(candidatePayload)));
        candidate.setCreatedBy(userId);
        versionMapper.insert(candidate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("job", job);
        result.put("candidate_version_id", candidate.getId());
        result.put("diff", Map.of("before", selected, "after", replacement));
        result.put("patches", candidatePayload.get("patches"));
        return result;
    }

    @Transactional
    public DraftView adoptPatches(Long userId, Long unitId, LocalRewriteAdoptRequest request) {
        ContentUnit unit = requireUnit(unitId);
        accessService.require(unit.getProjectId(), userId, Action.EDIT_CONTENT);
        requireRevision(unit, request.contentUnitRevision());

        String plain = currentPlainText(unit);
        if (request.contentHash() != null && !request.contentHash().isBlank()
                && !request.contentHash().equals(sha256(plain))) {
            throw new BizException(ErrorCode.AI_CANDIDATE_STALE, "正文已变化，候选不可直接采用");
        }

        List<PatchOp> patches = request.patches() == null ? List.of() : new ArrayList<>(request.patches());
        if (patches.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "patches 不能为空");
        }
        patches.sort(Comparator.comparing(PatchOp::startOffset));
        for (int i = 1; i < patches.size(); i++) {
            if (patches.get(i).startOffset() < patches.get(i - 1).endOffset()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "Patch 不得重叠");
            }
        }

        // 从尾部向前应用，避免偏移漂移
        StringBuilder buffer = new StringBuilder(plain);
        List<PatchOp> reverse = new ArrayList<>(patches);
        reverse.sort(Comparator.comparing(PatchOp::startOffset).reversed());
        for (PatchOp patch : reverse) {
            if (patch.startOffset() == null || patch.endOffset() == null
                    || patch.startOffset() < 0 || patch.endOffset() > buffer.length()
                    || patch.startOffset() > patch.endOffset()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "Patch 偏移非法");
            }
            String expected = buffer.substring(patch.startOffset(), patch.endOffset());
            if (patch.expectedTextHash() != null && !patch.expectedTextHash().equals(sha256(expected))) {
                throw new BizException(ErrorCode.AI_CANDIDATE_STALE, "expectedTextHash 不匹配，整批采用失败");
            }
            buffer.replace(patch.startOffset(), patch.endOffset(),
                    patch.replacement() == null ? "" : patch.replacement());
        }

        String nextPlain = buffer.toString();
        // 结构化剧本正文（episodes/scenes/blocks）由前端按 Patch 回写 content_json；
        // 服务端只可靠更新 plain_text，避免把 JSON 结构压扁成 wrap。
        String nextJson = null;
        ContentVersion existingDraft = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unitId)
                        .eq(ContentVersion::getStatus, "draft")
                        .last("limit 1"));
        if (existingDraft != null && existingDraft.getContentJson() != null
                && !existingDraft.getContentJson().isBlank()) {
            try {
                objectMapper.readTree(existingDraft.getContentJson());
                nextJson = existingDraft.getContentJson();
            } catch (Exception ignored) {
                nextJson = null;
            }
        }
        if (nextJson == null) {
            nextJson = nextPlain;
        }

        int revision = unit.getRevision() == null ? 0 : unit.getRevision();
        int claimed = unitMapper.update(null, new UpdateWrapper<ContentUnit>()
                .eq("id", unitId)
                .eq("revision", revision)
                .set("revision", revision + 1));
        if (claimed == 0) {
            throw new BizException(ErrorCode.EDIT_CONFLICT);
        }

        ContentVersion draft = existingDraft;
        if (draft == null) {
            draft = new ContentVersion();
            draft.setProjectId(unit.getProjectId());
            draft.setContentUnitId(unitId);
            draft.setVersionNo(0);
            draft.setStatus("draft");
            draft.setSource("manual_edit");
            draft.setCreatedBy(userId);
            draft.setContentJson(nextJson);
            draft.setPlainText(nextPlain);
            draft.setContentHash(sha256(nextPlain));
            versionMapper.insert(draft);
        } else {
            // 保留既有 content_json 结构，仅推进 plain_text
            draft.setPlainText(nextPlain);
            draft.setContentHash(sha256(nextPlain));
            versionMapper.updateById(draft);
        }

        return new DraftView(draft.getId(), unitId, revision + 1,
                draft.getContentJson(), draft.getPlainText(), draft.getCreatedAt());
    }

    private void validateSelection(String plain, LocalRewriteRequest request) {
        if (request.startOffset() == null || request.endOffset() == null
                || request.startOffset() < 0 || request.endOffset() > plain.length()
                || request.startOffset() >= request.endOffset()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "选区非法");
        }
        String selected = plain.substring(request.startOffset(), request.endOffset());
        if (request.selectedTextHash() != null && !request.selectedTextHash().isBlank()
                && !request.selectedTextHash().equals(sha256(selected))) {
            throw new BizException(ErrorCode.AI_CANDIDATE_STALE, "选区文本已变化");
        }
        if (request.contentHash() != null && !request.contentHash().isBlank()
                && !request.contentHash().equals(sha256(plain))) {
            throw new BizException(ErrorCode.AI_CANDIDATE_STALE, "正文 hash 已变化");
        }
    }

    private String currentPlainText(ContentUnit unit) {
        ContentVersion draft = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unit.getId())
                        .eq(ContentVersion::getStatus, "draft")
                        .last("limit 1"));
        if (draft != null) {
            if (draft.getPlainText() != null && !draft.getPlainText().isBlank()) {
                return draft.getPlainText();
            }
            return draft.getContentJson() == null ? "" : draft.getContentJson();
        }
        if (unit.getCurrentVersionId() != null) {
            ContentVersion current = versionMapper.selectById(unit.getCurrentVersionId());
            if (current != null) {
                if (current.getPlainText() != null && !current.getPlainText().isBlank()) {
                    return current.getPlainText();
                }
                return current.getContentJson() == null ? "" : current.getContentJson();
            }
        }
        return "";
    }

    private ContentUnit requireUnit(Long unitId) {
        ContentUnit unit = unitMapper.selectById(unitId);
        if (unit == null || Integer.valueOf(1).equals(unit.getIsDeleted())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return unit;
    }

    private void requireRevision(ContentUnit unit, Integer expected) {
        if (expected != null && !expected.equals(unit.getRevision())) {
            throw new BizException(ErrorCode.EDIT_CONFLICT);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
