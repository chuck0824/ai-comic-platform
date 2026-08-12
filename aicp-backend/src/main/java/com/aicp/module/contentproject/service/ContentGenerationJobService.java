package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.entity.ContentGenerationJob;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.entity.GenerationContextSnapshot;
import com.aicp.module.contentproject.mapper.ContentGenerationJobMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.GenerationContextSnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentGenerationJobService {

    private final ContentGenerationJobMapper jobMapper;
    private final GenerationContextSnapshotMapper contextSnapshotMapper;
    private final ContextAssembler contextAssembler;
    private final ContentGenerationExecutor executor;
    private final ObjectMapper objectMapper;
    private final ContentVersionMapper versionMapper;
    private final ContentUnitMapper unitMapper;
    private final ProjectAccessService projectAccessService;

    @Transactional
    public GenerationJobView createJob(Long userId, Long projectId, GenerationJobRequest request,
                                        String idempotencyKey) {
        // check idempotency
        ContentGenerationJob existing = jobMapper.selectOne(
                new LambdaQueryWrapper<ContentGenerationJob>()
                        .eq(ContentGenerationJob::getProjectId, projectId)
                        .eq(ContentGenerationJob::getIdempotencyKey, idempotencyKey));

        if (existing != null) {
            // same key: return existing if request matches
            // In production, compare input hashes; for M0, just return existing
            return toView(existing);
        }

        // assemble context snapshot
        ContextSnapshot snapshot = contextAssembler.assemble(projectId, request);
        JobSnapshot jobSnapshot = snapshotForJob(projectId, request, snapshot);

        ContentGenerationJob job = new ContentGenerationJob();
        job.setUuid(UUID.randomUUID().toString());
        job.setProjectId(projectId);
        job.setJobType(request.jobType());
        job.setTargetType(request.targetType());
        job.setTargetId(request.targetId());
        job.setStatus("pending");
        job.setInputSnapshotJson(jobSnapshot.payload());
        job.setInputSnapshotHash(jobSnapshot.contentHash());
        job.setSchemaVersion(request.schemaVersion() != null ? request.schemaVersion() : "v1");
        job.setEstimatedCredits(0);
        job.setActualCredits(0);
        job.setIdempotencyKey(idempotencyKey);
        job.setCreatedBy(userId);
        jobMapper.insert(job);

        // Persist generation context snapshot
        try {
            GenerationContextSnapshot persisted = new GenerationContextSnapshot();
            persisted.setGenerationJobId(job.getId());
            persisted.setProjectId(projectId);
            persisted.setBibleVersionId(snapshot.bibleVersionId());
            persisted.setProjectGuideId(snapshot.projectGuideId());
            persisted.setCharacterGuideIdsJson(
                    snapshot.characterGuideIds() != null
                            ? objectMapper.writeValueAsString(snapshot.characterGuideIds())
                            : null);
            persisted.setUnitGuideId(snapshot.unitGuideId());
            persisted.setSelectedVersionsJson(objectMapper.writeValueAsString(snapshot.selectedVersions()));
            persisted.setResolvedGuideJson(snapshot.resolvedGuideJson());
            persisted.setPayloadJson(jobSnapshot.payload());
            persisted.setPayloadHash(jobSnapshot.contentHash());
            contextSnapshotMapper.insert(persisted);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成上下文快照保存失败");
        }

        // M1: trigger async AI execution
        executor.execute(job.getId());

        return toView(job);
    }

    public GenerationJobView getJob(Long userId, Long jobId) {
        ContentGenerationJob job = requireAuthorizedJob(userId, jobId, Action.VIEW);
        return toView(job);
    }

    @Transactional
    public void cancelJob(Long userId, Long jobId) {
        ContentGenerationJob job = requireAuthorizedJob(userId, jobId, Action.EDIT_CONTENT);
        if ("cancelled".equals(job.getStatus())) {
            return;
        }
        int changed = jobMapper.update(null, new UpdateWrapper<ContentGenerationJob>()
                .eq("id", jobId)
                .in("status", "pending", "processing")
                .set("status", "cancelled")
                .set("finished_at", LocalDateTime.now()));
        if (changed == 0) {
            ContentGenerationJob current = jobMapper.selectById(jobId);
            if (current != null && "cancelled".equals(current.getStatus())) return;
            throw new BizException(ErrorCode.PARAM_INVALID, "只能取消 pending 或 processing 状态的任务");
        }
        job.setStatus("cancelled");
    }

    @Transactional
    public GenerationJobView acceptJob(Long userId, Long jobId) {
        ContentGenerationJob job = requireCompleted(userId, jobId, Action.EDIT_CONTENT);
        ContentUnit unit = requireTargetUnit(job);
        ContentVersion candidate = requireResultVersion(jobId);
        if (!candidate.getContentUnitId().equals(unit.getId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "生成结果对应的内容单元不存在");
        }
        if ("discarded".equals(candidate.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已丢弃的生成结果不能再采用");
        }
        if ("accepted".equals(candidate.getStatus()) && candidate.getId().equals(unit.getCurrentVersionId())) {
            return toView(job);
        }
        if ("accepted".equals(candidate.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "生成结果已被采用，但当前版本已变更");
        }
        if (!"candidate".equals(candidate.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "生成结果不是可采用的候选版本");
        }
        TargetBaseline baseline = targetBaseline(job, unit);
        int revision = baseline.revision();
        UpdateWrapper<ContentUnit> unitClaim = new UpdateWrapper<ContentUnit>()
                .eq("id", unit.getId())
                .eq("revision", revision)
                .set("current_version_id", candidate.getId())
                .set("revision", revision + 1);
        if (baseline.currentVersionId() == null) unitClaim.isNull("current_version_id");
        else unitClaim.eq("current_version_id", baseline.currentVersionId());
        if (unitMapper.update(null, unitClaim) == 0) {
            throw new BizException(ErrorCode.EDIT_CONFLICT, "当前内容版本已变更，请刷新后重新选择候选结果");
        }
        int claimed = versionMapper.update(null, new UpdateWrapper<ContentVersion>()
                .eq("id", candidate.getId())
                .eq("status", "candidate")
                .set("status", "accepted"));
        if (claimed == 0) {
            ContentVersion current = versionMapper.selectById(candidate.getId());
            if (current != null && "accepted".equals(current.getStatus()) && candidate.getId().equals(unit.getCurrentVersionId())) {
                return toView(job);
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "生成候选版本已被其他操作处理");
        }
        candidate.setStatus("accepted");
        unit.setCurrentVersionId(candidate.getId());
        unit.setRevision(revision + 1);
        return toView(job);
    }

    @Transactional
    public GenerationJobView discardJob(Long userId, Long jobId) {
        ContentGenerationJob job = requireCompleted(userId, jobId, Action.EDIT_CONTENT);
        ContentUnit unit = requireTargetUnit(job);
        ContentVersion candidate = requireResultVersion(jobId);
        if ("discarded".equals(candidate.getStatus())) return toView(job);
        if (!candidate.getContentUnitId().equals(unit.getId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "生成结果对应的内容单元不存在");
        }
        if ("accepted".equals(candidate.getStatus()) || (unit != null && candidate.getId().equals(unit.getCurrentVersionId()))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已采用的生成结果不能丢弃");
        }
        if (!"candidate".equals(candidate.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "生成结果不是可丢弃的候选版本");
        }
        int claimed = versionMapper.update(null, new UpdateWrapper<ContentVersion>()
                .eq("id", candidate.getId())
                .eq("status", "candidate")
                .set("status", "discarded"));
        if (claimed == 0) {
            ContentVersion current = versionMapper.selectById(candidate.getId());
            if (current != null && "discarded".equals(current.getStatus())) return toView(job);
            throw new BizException(ErrorCode.PARAM_INVALID, "生成候选版本已被其他操作处理");
        }
        candidate.setStatus("discarded");
        return toView(job);
    }

    private ContentGenerationJob requireCompleted(Long userId, Long jobId, Action action) {
        ContentGenerationJob job = requireAuthorizedJob(userId, jobId, action);
        if (!"completed".equals(job.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能处理已完成的生成结果");
        }
        return job;
    }

    private ContentGenerationJob requireAuthorizedJob(Long userId, Long jobId, Action action) {
        ContentGenerationJob job = jobMapper.selectById(jobId);
        if (job == null) throw new BizException(ErrorCode.NOT_FOUND);
        projectAccessService.require(job.getProjectId(), userId, action);
        if (isContentUnitTarget(job.getTargetType())) requireTargetUnit(job);
        return job;
    }

    private ContentUnit requireTargetUnit(ContentGenerationJob job) {
        if (!isContentUnitTarget(job.getTargetType()) || job.getTargetId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "生成任务目标不是内容单元");
        }
        ContentUnit unit = unitMapper.selectById(job.getTargetId());
        if (unit == null || !job.getProjectId().equals(unit.getProjectId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "生成任务目标与项目不匹配");
        }
        return unit;
    }

    private ContentVersion requireResultVersion(Long jobId) {
        ContentVersion version = findResultVersion(jobId);
        if (version == null) throw new BizException(ErrorCode.NOT_FOUND, "生成候选版本不存在");
        return version;
    }

    private ContentVersion findResultVersion(Long jobId) {
        return versionMapper.selectOne(new LambdaQueryWrapper<ContentVersion>()
                .eq(ContentVersion::getGenerationJobId, jobId)
                .last("limit 1"));
    }

    private JobSnapshot snapshotForJob(Long projectId, GenerationJobRequest request, ContextSnapshot snapshot) {
        if (!isContentUnitTarget(request.targetType()) || request.targetId() == null) {
            return new JobSnapshot(snapshot.payload(), snapshot.contentHash());
        }
        ContentUnit unit = unitMapper.selectById(request.targetId());
        if (unit == null || !projectId.equals(unit.getProjectId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "生成任务目标与项目不匹配");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(snapshot.payload(), LinkedHashMap.class);
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("unit_id", unit.getId());
            target.put("revision", unit.getRevision() == null ? 0 : unit.getRevision());
            target.put("current_version_id", unit.getCurrentVersionId());
            payload.put("_generation_target", target);
            String json = objectMapper.writeValueAsString(payload);
            return new JobSnapshot(json, sha256(json));
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成目标快照保存失败");
        }
    }

    private TargetBaseline targetBaseline(ContentGenerationJob job, ContentUnit fallback) {
        try {
            JsonNode root = objectMapper.readTree(job.getInputSnapshotJson());
            JsonNode target = root != null ? root.path("_generation_target") : null;
            JsonNode unitId = target != null ? target.get("unit_id") : null;
            JsonNode revision = target != null ? target.get("revision") : null;
            JsonNode current = target != null ? target.get("current_version_id") : null;
            if (target != null && target.isObject()
                    && unitId != null && unitId.isIntegralNumber() && unitId.asLong() == fallback.getId()
                    && revision != null && revision.isIntegralNumber() && revision.canConvertToInt()
                    && revision.asInt() >= 0
                    && current != null && (current.isNull() || (current.isIntegralNumber() && current.asLong() > 0))) {
                return new TargetBaseline(
                        revision.asInt(), current.isNull() ? null : current.asLong());
            }
        } catch (Exception e) {
            log.warn("Generation job {} has no readable target baseline", job.getId());
        }
        throw new BizException(ErrorCode.GENERATION_BASELINE_REQUIRED,
                "GENERATION_BASELINE_REQUIRED：该旧候选缺少可验证的生成基线，请重新生成后再采用");
    }

    private boolean isContentUnitTarget(String targetType) {
        return "content_unit".equals(targetType) || "unit".equals(targetType)
                || "content-unit".equals(targetType) || "contentUnit".equals(targetType);
    }

    private String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record JobSnapshot(String payload, String contentHash) {}
    private record TargetBaseline(int revision, Long currentVersionId) {}

    private String errorMessage(String code) {
        if (code == null) return null;
        return switch (code) {
            case "SCHEMA_VALIDATION_FAILED" -> "生成结果结构校验失败，请调整内容或重试";
            case "AI_ERROR" -> "模型生成失败，请稍后重试";
            default -> "生成任务失败：" + code;
        };
    }

    private GenerationJobView toView(ContentGenerationJob job) {
        ContentVersion result = "completed".equals(job.getStatus()) ? findResultVersion(job.getId()) : null;
        Long resultVersionId = result != null ? result.getId() : null;
        String artifactRef = result != null
                ? "/content-units/" + result.getContentUnitId() + "/versions/" + result.getId()
                : null;
        return new GenerationJobView(
                job.getId(), job.getUuid(), job.getJobType(),
                job.getTargetType(), job.getTargetId(), job.getStatus(),
                job.getEstimatedCredits(), job.getActualCredits(), 0, 2000,
                job.getErrorCode(), errorMessage(job.getErrorCode()),
                resultVersionId, artifactRef, result != null ? result.getStatus() : null,
                job.getCreatedBy(), job.getCreatedAt(), job.getFinishedAt());
    }
}
