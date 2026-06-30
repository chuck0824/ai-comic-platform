package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingExtractionService {

    private final SettingExtractionBatchMapper batchMapper;
    private final SettingExtractionCandidateMapper candidateMapper;
    private final ProjectSettingEntityMapper settingEntityMapper;
    private final ProjectSettingVersionMapper settingVersionMapper;
    private final ProjectSettingService settingService;
    private final ProjectAccessService accessService;
    private final ProjectContextPublisher contextPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    static final Set<String> VALID_STATUSES = Set.of("queued", "running", "review_ready",
            "partially_failed", "failed", "applied", "cancelled");

    // ===== Create Batch =====

    @Transactional
    public Map<String, Object> createExtraction(Long userId, Long projectId, Map<String, Object> body) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);

        String idempotencyKey = (String) body.get("idempotency_key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "idempotency_key 不能为空");
        }

        // 幂等：已有相同键则直接返回
        SettingExtractionBatch existing = batchMapper.selectOne(
                new LambdaQueryWrapper<SettingExtractionBatch>()
                        .eq(SettingExtractionBatch::getProjectId, projectId)
                        .eq(SettingExtractionBatch::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            return toBatchMap(existing);
        }

        SettingExtractionBatch batch = new SettingExtractionBatch();
        batch.setProjectId(projectId);
        batch.setSourceVersionId(toLong(body.get("source_version_id")));
        batch.setChapterVersionIdsJson(toJson(body.get("chapter_version_ids")));
        batch.setTargetSettingTypes(toJson(body.get("target_setting_types")));
        batch.setIdempotencyKey(idempotencyKey);
        batch.setStatus("queued");
        batch.setModelId((String) body.getOrDefault("model_id", "deepseek-v3"));
        batch.setPromptVersion((String) body.getOrDefault("prompt_version", "v1"));
        batch.setExtractionConfigJson(toJson(body.get("config")));
        batch.setRevision(0);
        batch.setCreatedBy(userId);
        batchMapper.insert(batch);

        log.info("创建提取批次 batchId={} projectId={} idempotencyKey={}", batch.getId(), projectId, idempotencyKey);
        return toBatchMap(batch);
    }

    // ===== Get Batch =====

    public Map<String, Object> getExtraction(Long userId, Long projectId, Long batchId) {
        accessService.require(projectId, userId, Action.VIEW);
        SettingExtractionBatch batch = getBatchOrFail(batchId, projectId);

        Map<String, Object> result = toBatchMap(batch);

        // 附候选项计数
        Long candidateCount = candidateMapper.selectCount(
                new LambdaQueryWrapper<SettingExtractionCandidate>()
                        .eq(SettingExtractionCandidate::getBatchId, batchId));
        Long pendingCount = candidateMapper.selectCount(
                new LambdaQueryWrapper<SettingExtractionCandidate>()
                        .eq(SettingExtractionCandidate::getBatchId, batchId)
                        .eq(SettingExtractionCandidate::getReviewStatus, "pending"));
        result.put("candidate_count", candidateCount.intValue());
        result.put("pending_count", pendingCount.intValue());

        // 附候选项列表
        List<SettingExtractionCandidate> candidates = candidateMapper.selectList(
                new LambdaQueryWrapper<SettingExtractionCandidate>()
                        .eq(SettingExtractionCandidate::getBatchId, batchId));
        result.put("candidates", candidates.stream().map(this::toCandidateMap).toList());

        return result;
    }

    // ===== Save Decisions =====

    @Transactional
    public Map<String, Object> saveDecisions(Long userId, Long projectId, Long batchId, Map<String, Object> body) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        SettingExtractionBatch batch = getBatchOrFail(batchId, projectId);

        if (!"review_ready".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "批次状态不允许保存决策，当前: " + batch.getStatus());
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) body.get("decisions");
        if (decisions == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "decisions 不能为空");
        }

        for (Map<String, Object> decision : decisions) {
            Long candidateId = toLong(decision.get("candidate_id"));
            if (candidateId == null) continue;

            SettingExtractionCandidate candidate = candidateMapper.selectById(candidateId);
            if (candidate == null || !candidate.getBatchId().equals(batchId)) continue;

            candidate.setFieldDecisionsJson(toJson(decision.get("field_decisions")));
            candidate.setReviewStatus((String) decision.getOrDefault("review_status", "accepted"));
            candidateMapper.updateById(candidate);
        }

        return toBatchMap(batch);
    }

    // ===== Apply =====

    @Transactional
    public Map<String, Object> applyExtraction(Long userId, Long projectId, Long batchId) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        SettingExtractionBatch batch = getBatchOrFail(batchId, projectId);

        if (!"review_ready".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "批次状态不允许应用，当前: " + batch.getStatus());
        }

        List<SettingExtractionCandidate> candidates = candidateMapper.selectList(
                new LambdaQueryWrapper<SettingExtractionCandidate>()
                        .eq(SettingExtractionCandidate::getBatchId, batchId)
                        .eq(SettingExtractionCandidate::getReviewStatus, "accepted"));

        int applied = 0;
        for (SettingExtractionCandidate candidate : candidates) {
            applyCandidate(projectId, userId, candidate);
            applied++;
        }

        // 标记批次为已应用
        batch.setStatus("applied");
        batch.setAppliedAt(LocalDateTime.now());
        batch.setAppliedBy(userId);
        batchMapper.updateById(batch);

        // 发布上下文
        contextPublisher.publish(projectId, userId);

        log.info("提取批次 {} 应用完成，创建/更新 {} 条设定", batchId, applied);

        Map<String, Object> result = toBatchMap(batch);
        result.put("applied_count", applied);
        return result;
    }

    // ===== Retry =====

    public Map<String, Object> retryExtraction(Long userId, Long projectId, Long batchId) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        SettingExtractionBatch batch = getBatchOrFail(batchId, projectId);

        if (!"failed".equals(batch.getStatus()) && !"partially_failed".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "只有失败或部分失败的批次可以重试，当前: " + batch.getStatus());
        }

        batch.setStatus("queued");
        batch.setErrorMessage(null);
        batchMapper.updateById(batch);

        log.info("提取批次 {} 重新入队", batchId);
        return toBatchMap(batch);
    }

    // ===== Internal Helpers =====

    private void applyCandidate(Long projectId, Long userId, SettingExtractionCandidate candidate) {
        if (candidate.getMatchedEntityId() != null) {
            // 更新已有设定
            ProjectSettingEntity entity = settingEntityMapper.selectById(candidate.getMatchedEntityId());
            if (entity != null && entity.getProjectId().equals(projectId)) {
                mergeFieldValues(entity, candidate);
                entity.setSourceType("merged");
                entity.setRevision(entity.getRevision() + 1);
                entity.setUpdatedBy(userId);
                settingEntityMapper.updateById(entity);

                // 创建版本
                settingService.createSettingVersion(entity, "ai_extracted", userId, candidate.getEvidenceText());
            }
        } else {
            // 新建设定
            ProjectSettingEntity entity = new ProjectSettingEntity();
            entity.setProjectId(projectId);
            entity.setSettingType(candidate.getSettingType());
            entity.setCanonicalName(candidate.getCanonicalName());
            entity.setAliasesJson(candidate.getAliasesJson());
            entity.setDetailsJson(candidate.getFieldValuesJson());
            entity.setStatus("confirmed");
            entity.setSourceType("ai_extracted");
            entity.setCurrentVersionNo(0);
            entity.setRevision(0);
            entity.setCreatedBy(userId);
            entity.setUpdatedBy(userId);
            settingEntityMapper.insert(entity);

            settingService.createSettingVersion(entity, "ai_extracted", userId, candidate.getEvidenceText());
        }
    }

    private void mergeFieldValues(ProjectSettingEntity entity, SettingExtractionCandidate candidate) {
        // 简单合并：新值覆盖 details_json
        if (candidate.getFieldValuesJson() != null) {
            entity.setDetailsJson(candidate.getFieldValuesJson());
        }
        if (candidate.getAliasesJson() != null) {
            entity.setAliasesJson(candidate.getAliasesJson());
        }
        if (candidate.getEvidenceText() != null) {
            entity.setSummary(entity.getSummary() != null ? entity.getSummary() : candidate.getEvidenceText());
        }
    }

    private SettingExtractionBatch getBatchOrFail(Long batchId, Long projectId) {
        SettingExtractionBatch batch = batchMapper.selectById(batchId);
        if (batch == null || !batch.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "提取批次不存在: " + batchId);
        }
        return batch;
    }

    private Map<String, Object> toBatchMap(SettingExtractionBatch batch) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", batch.getId());
        m.put("project_id", batch.getProjectId());
        m.put("source_version_id", batch.getSourceVersionId());
        m.put("target_setting_types", parseJson(batch.getTargetSettingTypes()));
        m.put("idempotency_key", batch.getIdempotencyKey());
        m.put("status", batch.getStatus());
        m.put("model_id", batch.getModelId());
        m.put("error_message", batch.getErrorMessage());
        m.put("applied_at", batch.getAppliedAt());
        m.put("created_at", batch.getCreatedAt());
        return m;
    }

    private Map<String, Object> toCandidateMap(SettingExtractionCandidate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("batch_id", c.getBatchId());
        m.put("setting_type", c.getSettingType());
        m.put("canonical_name", c.getCanonicalName());
        m.put("field_values", parseJson(c.getFieldValuesJson()));
        m.put("evidence_text", c.getEvidenceText());
        m.put("confidence", c.getConfidence());
        m.put("matched_entity_id", c.getMatchedEntityId());
        m.put("match_status", c.getMatchStatus());
        m.put("field_decisions", parseJson(c.getFieldDecisionsJson()));
        m.put("review_status", c.getReviewStatus());
        return m;
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { return String.valueOf(value); }
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, Object.class); }
        catch (Exception e) { return json; }
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return value == null ? null : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return null; }
    }
}
