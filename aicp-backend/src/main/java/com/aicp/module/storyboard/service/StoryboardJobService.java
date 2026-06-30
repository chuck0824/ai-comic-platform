package com.aicp.module.storyboard.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.storyboard.domain.ProductionGate;
import com.aicp.module.storyboard.domain.StoryboardEnums.*;
import com.aicp.module.storyboard.entity.*;
import com.aicp.module.storyboard.exchange.StoryboardPdfExporter;
import com.aicp.module.storyboard.exchange.StoryboardWorkbookExporter;
import com.aicp.module.storyboard.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardJobService {

    private final StoryboardJobMapper jobMapper;
    private final StoryboardVersionMapper versionMapper;
    private final StoryboardMapper storyboardMapper;
    private final StoryboardReviewIssueMapper issueMapper;
    private final StoryboardVersionShotMapper shotMapper;
    private final StoryboardCanvasSnapshotMapper snapshotMapper;
    private final StoryboardWorkbookExporter workbookExporter;
    private final StoryboardPdfExporter pdfExporter;
    private final StoryboardAccessService accessService;

    @Transactional
    public StoryboardJob createJob(Long projectId, Long storyboardId, Long versionId,
                                    String jobType, String idempotencyKey, Long userId) {
        accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);

        StoryboardJob existing = jobMapper.selectOne(
                new LambdaQueryWrapper<StoryboardJob>()
                        .eq(StoryboardJob::getProjectId, projectId)
                        .eq(StoryboardJob::getJobType, jobType)
                        .eq(StoryboardJob::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            return existing;
        }

        StoryboardJob job = new StoryboardJob();
        job.setUuid(UUID.randomUUID().toString());
        job.setProjectId(projectId);
        job.setStoryboardId(storyboardId);
        job.setVersionId(versionId);
        job.setJobType(jobType);
        job.setStatus(JobStatus.QUEUED.value());
        job.setIdempotencyKey(idempotencyKey);
        job.setCreatedBy(userId);
        jobMapper.insert(job);
        return job;
    }

    @Transactional
    public StoryboardJob executeExport(Long projectId, Long jobId, Long userId) {
        StoryboardJob job = jobMapper.selectById(jobId);
        if (job == null) throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");

        job.setStatus(JobStatus.RUNNING.value());
        job.setStartedAt(LocalDateTime.now());
        jobMapper.updateById(job);

        try {
            Long versionId = job.getVersionId();
            byte[] data;
            String resultJson;

            if ("export_xlsx".equals(job.getJobType())) {
                data = workbookExporter.exportFullWorkbook(versionId, job.getStoryboardId());
                resultJson = "{\"format\":\"xlsx\",\"size\":" + data.length + "}";
            } else if ("export_pdf".equals(job.getJobType())) {
                StoryboardVersion version = versionMapper.selectById(versionId);
                List<StoryboardShot> shots = shotMapper.selectList(
                        new LambdaQueryWrapper<StoryboardShot>()
                                .eq(StoryboardShot::getVersionId, versionId));
                data = pdfExporter.exportDirectorPdf(version, shots);
                resultJson = "{\"format\":\"pdf\",\"size\":" + data.length + "}";
            } else {
                data = new byte[0];
                resultJson = "{\"format\":\"unknown\"}";
            }

            job.setStatus(JobStatus.SUCCEEDED.value());
            job.setResultJson(resultJson);
            job.setProgressPercent(100);
        } catch (Exception e) {
            log.error("Export job {} failed", jobId, e);
            job.setStatus(JobStatus.FAILED.value());
            job.setErrorCode("EXPORT_FAILED");
            job.setErrorMessage(e.getMessage());
        }

        job.setFinishedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        return job;
    }

    @Transactional
    public StoryboardJob createCanvasSnapshot(Long projectId, Long versionId, Long userId,
                                               String snapshotType, String idempotencyKey) {
        var version = accessService.requireVersion(projectId, versionId, userId, Action.PRODUCE);

        // Check gate
        List<StoryboardShot> shots = shotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>()
                        .eq(StoryboardShot::getVersionId, versionId));
        List<String> openErrors = issueMapper.selectList(
                new LambdaQueryWrapper<StoryboardReviewIssue>()
                        .eq(StoryboardReviewIssue::getVersionId, versionId)
                        .eq(StoryboardReviewIssue::getStatus, "open")
                        .eq(StoryboardReviewIssue::getSeverity, "error"))
                .stream().map(StoryboardReviewIssue::getFingerprint).toList();

        ProductionGate.GateResult gate = ProductionGate.evaluate(version, shots, List.of(), openErrors);
        if (!gate.allowed()) {
            throw new BizException(ErrorCode.PRODUCTION_GATE_FAILED,
                    "生产准入未通过: " + String.join("; ", gate.violations()));
        }

        // Check idempotency
        StoryboardCanvasSnapshot existing = snapshotMapper.selectOne(
                new LambdaQueryWrapper<StoryboardCanvasSnapshot>()
                        .eq(StoryboardCanvasSnapshot::getProjectId, projectId)
                        .eq(StoryboardCanvasSnapshot::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            // Return job that references existing snapshot
            return findOrCreateJobForSnapshot(projectId, version.getStoryboardId(), versionId,
                    snapshotType, idempotencyKey, userId, existing);
        }

        // Create immutable snapshot
        StoryboardCanvasSnapshot snapshot = new StoryboardCanvasSnapshot();
        snapshot.setUuid(UUID.randomUUID().toString());
        snapshot.setProjectId(projectId);
        snapshot.setStoryboardId(version.getStoryboardId());
        snapshot.setVersionId(versionId);
        snapshot.setSnapshotType(snapshotType);
        snapshot.setIdempotencyKey(idempotencyKey);
        snapshot.setSourceContentVersionId(version.getSourceContentVersionId());
        snapshot.setSnapshotJson("{\"versionId\":" + versionId + ",\"shots\":" + shots.size() + "}");
        snapshot.setSnapshotHash(StoryboardReviewService.sha256("snapshot-" + versionId + "-" + idempotencyKey));
        snapshot.setCreatedBy(userId);
        snapshotMapper.insert(snapshot);

        return findOrCreateJobForSnapshot(projectId, version.getStoryboardId(), versionId,
                snapshotType, idempotencyKey, userId, snapshot);
    }

    private StoryboardJob findOrCreateJobForSnapshot(Long projectId, Long storyboardId, Long versionId,
                                                      String snapshotType, String idempotencyKey,
                                                      Long userId, StoryboardCanvasSnapshot snapshot) {
        StoryboardJob existing = jobMapper.selectOne(
                new LambdaQueryWrapper<StoryboardJob>()
                        .eq(StoryboardJob::getProjectId, projectId)
                        .eq(StoryboardJob::getJobType, "canvas_snapshot")
                        .eq(StoryboardJob::getIdempotencyKey, idempotencyKey));
        if (existing != null) return existing;

        StoryboardJob job = new StoryboardJob();
        job.setUuid(UUID.randomUUID().toString());
        job.setProjectId(projectId);
        job.setStoryboardId(storyboardId);
        job.setVersionId(versionId);
        job.setJobType("canvas_snapshot");
        job.setStatus(JobStatus.SUCCEEDED.value());
        job.setIdempotencyKey(idempotencyKey);
        job.setResultJson("{\"snapshotId\":" + snapshot.getId() + ",\"snapshotUuid\":\"" + snapshot.getUuid() + "\"}");
        job.setProgressPercent(100);
        job.setCreatedBy(userId);
        job.setStartedAt(LocalDateTime.now());
        job.setFinishedAt(LocalDateTime.now());
        jobMapper.insert(job);
        return job;
    }

    public StoryboardJob getJob(Long jobId, Long userId) {
        StoryboardJob job = jobMapper.selectById(jobId);
        if (job == null) throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");
        return job;
    }

    public List<StoryboardJob> listJobs(Long projectId, Long userId) {
        return jobMapper.selectList(
                new LambdaQueryWrapper<StoryboardJob>()
                        .eq(StoryboardJob::getProjectId, projectId)
                        .orderByDesc(StoryboardJob::getCreatedAt));
    }
}
