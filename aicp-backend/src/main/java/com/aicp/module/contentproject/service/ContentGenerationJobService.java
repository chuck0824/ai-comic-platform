package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

        ContentGenerationJob job = new ContentGenerationJob();
        job.setUuid(UUID.randomUUID().toString());
        job.setProjectId(projectId);
        job.setJobType(request.jobType());
        job.setTargetType(request.targetType());
        job.setTargetId(request.targetId());
        job.setStatus("pending");
        job.setInputSnapshotJson(snapshot.payload());
        job.setInputSnapshotHash(snapshot.contentHash());
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
            persisted.setPayloadJson(snapshot.payload());
            persisted.setPayloadHash(snapshot.contentHash());
            contextSnapshotMapper.insert(persisted);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成上下文快照保存失败");
        }

        // M1: trigger async AI execution
        executor.execute(job.getId());

        return toView(job);
    }

    public GenerationJobView getJob(Long jobId) {
        ContentGenerationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toView(job);
    }

    @Transactional
    public void cancelJob(Long jobId) {
        ContentGenerationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
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
    public GenerationJobView acceptJob(Long jobId) {
        ContentGenerationJob job = requireCompleted(jobId);
        ContentVersion candidate = requireResultVersion(jobId);
        ContentUnit unit = unitMapper.selectById(candidate.getContentUnitId());
        if (unit == null || !job.getProjectId().equals(unit.getProjectId())) {
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
        unit.setRevision((unit.getRevision() == null ? 0 : unit.getRevision()) + 1);
        unitMapper.updateById(unit);
        return toView(job);
    }

    @Transactional
    public GenerationJobView discardJob(Long jobId) {
        ContentGenerationJob job = requireCompleted(jobId);
        ContentVersion candidate = requireResultVersion(jobId);
        if ("discarded".equals(candidate.getStatus())) return toView(job);
        ContentUnit unit = unitMapper.selectById(candidate.getContentUnitId());
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

    private ContentGenerationJob requireCompleted(Long jobId) {
        ContentGenerationJob job = jobMapper.selectById(jobId);
        if (job == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!"completed".equals(job.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能处理已完成的生成结果");
        }
        return job;
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

    private String errorMessage(String code) {
        if (code == null) return null;
        return switch (code) {
            case "SCHEMA_VALIDATION_FAILED" -> "生成结果结构校验失败，请调整内容或重试";
            case "AI_ERROR" -> "模型生成失败，请稍后重试";
            default -> "生成任务失败：" + code;
        };
    }

    private GenerationJobView toView(ContentGenerationJob job) {
        ContentVersion result = findResultVersion(job.getId());
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
