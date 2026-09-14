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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * R2-A.3 局部 AI 改写：真实 ContentGenerationJob → candidate Patch → 采用校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalRewriteService {

    private final ContentUnitMapper unitMapper;
    private final ContentVersionMapper versionMapper;
    private final ProjectAccessService accessService;
    private final ContentGenerationJobService generationJobService;
    private final ContentGenerationExecutor generationExecutor;
    private final ContentUnitService contentUnitService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> createRewrite(Long userId, Long unitId, LocalRewriteRequest request) {
        ContentUnit unit = requireUnit(unitId);
        accessService.require(unit.getProjectId(), userId, Action.RUN_CONTENT_AI);
        requireRevision(unit, request.contentUnitRevision());

        String plain = currentPlainText(unit);
        validateSelection(plain, request);
        markStaleCandidatesIfBaseChanged(unitId, request.contentHash());

        String selected = plain.substring(request.startOffset(), request.endOffset());
        Map<String, Object> rewriteCtx = new LinkedHashMap<>();
        rewriteCtx.put("startOffset", request.startOffset());
        rewriteCtx.put("endOffset", request.endOffset());
        rewriteCtx.put("selectedText", selected);
        rewriteCtx.put("selectedTextHash", request.selectedTextHash());
        rewriteCtx.put("operation", request.operation() == null ? "rewrite" : request.operation());
        rewriteCtx.put("prompt", request.prompt());
        rewriteCtx.put("model", request.model());
        rewriteCtx.put("baseVersionId", request.baseVersionId());
        rewriteCtx.put("baseContentHash", request.contentHash());
        rewriteCtx.put("contentUnitRevision", request.contentUnitRevision());

        Map<String, Object> strategy = new LinkedHashMap<>();
        strategy.put("local_rewrite", rewriteCtx);
        strategy.put("allow_unconfirmed_bible", true);

        GenerationJobRequest jobRequest = new GenerationJobRequest(
                "local_rewrite",
                "content_unit",
                unitId,
                Map.of(),
                toJson(strategy),
                "v1-local-rewrite");
        String idempotencyKey = "local-rewrite-" + unitId + "-" + UUID.randomUUID();
        GenerationJobView job = generationJobService.createJob(
                userId, unit.getProjectId(), jobRequest, idempotencyKey, false);

        ContentVersion candidate;
        try {
            candidate = generationExecutor.completeLocalRewrite(job.id());
        } catch (Exception e) {
            generationExecutor.markJobFailed(job.id(), "AI_ERROR");
            log.error("local rewrite job {} failed", job.id(), e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "局部改写生成失败，正文未修改");
        }

        Map<String, Object> payload = parseJsonMap(candidate.getContentJson());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("job", generationJobService.getJob(userId, job.id()));
        result.put("candidate_version_id", candidate.getId());
        result.put("diff", Map.of(
                "before", payload.getOrDefault("before", selected),
                "after", payload.getOrDefault("after", candidate.getPlainText())));
        result.put("patches", payload.getOrDefault("patches", List.of()));
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
            markStaleCandidatesIfBaseChanged(unitId, sha256(plain));
            throw new BizException(ErrorCode.AI_CANDIDATE_STALE, "正文已变化，候选不可直接采用");
        }

        if (request.candidateVersionId() != null) {
            ContentVersion candidate = versionMapper.selectById(request.candidateVersionId());
            if (candidate == null || !unitId.equals(candidate.getContentUnitId())) {
                throw new BizException(ErrorCode.NOT_FOUND, "改写候选不存在");
            }
            if ("stale".equalsIgnoreCase(candidate.getStatus())
                    || "discarded".equalsIgnoreCase(candidate.getStatus())) {
                throw new BizException(ErrorCode.AI_CANDIDATE_STALE, "候选已过期或已丢弃");
            }
            Map<String, Object> payload = parseJsonMap(candidate.getContentJson());
            Object baseHash = payload.get("baseContentHash");
            if (baseHash != null && request.contentHash() != null
                    && !String.valueOf(baseHash).equals(request.contentHash())) {
                candidate.setStatus("stale");
                versionMapper.updateById(candidate);
                throw new BizException(ErrorCode.AI_CANDIDATE_STALE, "候选基准 hash 已失效");
            }
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
        ContentVersion existingDraft = versionMapper.selectOne(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unitId)
                        .eq(ContentVersion::getStatus, "draft")
                        .last("limit 1"));
        String nextJson = mergePlainIntoContentJson(
                existingDraft == null ? null : existingDraft.getContentJson(), plain, nextPlain);

        int revision = unit.getRevision() == null ? 0 : unit.getRevision();
        int claimed = unitMapper.update(null, new UpdateWrapper<ContentUnit>()
                .eq("id", unitId)
                .eq("revision", revision)
                .set("revision", revision + 1));
        if (claimed == 0) {
            contentUnitService.raiseEditConflict(unitId, unit, request.contentUnitRevision());
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
            draft.setContentJson(nextJson);
            draft.setPlainText(nextPlain);
            draft.setContentHash(sha256(nextPlain));
            versionMapper.updateById(draft);
        }

        if (request.candidateVersionId() != null) {
            versionMapper.update(null, new UpdateWrapper<ContentVersion>()
                    .eq("id", request.candidateVersionId())
                    .eq("status", "candidate")
                    .set("status", "accepted"));
        }

        return new DraftView(draft.getId(), unitId, revision + 1,
                draft.getContentJson(), draft.getPlainText(), draft.getCreatedAt());
    }

    private String mergePlainIntoContentJson(String existingJson, String previousPlain, String nextPlain) {
        if (existingJson == null || existingJson.isBlank()) {
            return nextPlain;
        }
        try {
            JsonNode root = objectMapper.readTree(existingJson);
            if (root.isTextual()) {
                return nextPlain;
            }
            if (root.isObject()) {
                ObjectNode obj = (ObjectNode) root;
                if (obj.has("plainText") || obj.has("plain_text")) {
                    if (obj.has("plainText")) {
                        obj.put("plainText", nextPlain);
                    }
                    if (obj.has("plain_text")) {
                        obj.put("plain_text", nextPlain);
                    }
                    return objectMapper.writeValueAsString(obj);
                }
                if (obj.has("content") && obj.get("content").isTextual()) {
                    obj.put("content", nextPlain);
                    return objectMapper.writeValueAsString(obj);
                }
            }
            // 结构化剧本等：保留结构，由前端随后 persistUnit 回写；此处同步 plain 兜底
            if (existingJson.equals(previousPlain)) {
                return nextPlain;
            }
            return existingJson;
        } catch (Exception e) {
            return nextPlain;
        }
    }

    private void markStaleCandidatesIfBaseChanged(Long unitId, String currentHash) {
        if (currentHash == null || currentHash.isBlank()) {
            return;
        }
        List<ContentVersion> candidates = versionMapper.selectList(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentUnitId, unitId)
                        .eq(ContentVersion::getStatus, "candidate")
                        .eq(ContentVersion::getSource, "ai_local_rewrite"));
        for (ContentVersion candidate : candidates) {
            Map<String, Object> payload = parseJsonMap(candidate.getContentJson());
            Object baseHash = payload.get("baseContentHash");
            if (baseHash != null && !currentHash.equals(String.valueOf(baseHash))) {
                candidate.setStatus("stale");
                versionMapper.updateById(candidate);
            }
        }
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
            contentUnitService.raiseEditConflict(unit.getId(), unit, expected);
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
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
