package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.entity.ContentGenerationJob;
import com.aicp.module.contentproject.mapper.ContentGenerationJobMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final ContextAssembler contextAssembler;
    private final ContentGenerationExecutor executor;

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
        if (!"pending".equals(job.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能取消 pending 状态的任务");
        }
        job.setStatus("cancelled");
        job.setFinishedAt(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    private GenerationJobView toView(ContentGenerationJob job) {
        return new GenerationJobView(
                job.getId(), job.getUuid(), job.getJobType(),
                job.getTargetType(), job.getTargetId(), job.getStatus(),
                job.getEstimatedCredits(), 0, 2000,
                job.getCreatedBy(), job.getCreatedAt(), job.getFinishedAt());
    }
}
