package com.aicp.module.storyboard.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.storyboard.dto.StoryboardRequests.*;
import com.aicp.module.storyboard.dto.StoryboardViews.JobView;
import com.aicp.module.storyboard.dto.StoryboardViews.CanvasSnapshotView;
import com.aicp.module.storyboard.entity.StoryboardJob;
import com.aicp.module.storyboard.service.StoryboardJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StoryboardJobController {

    private final StoryboardJobService jobService;

    // ===== Jobs under a version =====
    @PostMapping("/api/v1/content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}/jobs/export")
    public ApiResponse<JobView> startExport(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId,
            @Valid @RequestBody ExportRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        String jobType = "export_pdf".equals(request.exportMode()) ? "export_pdf" : "export_xlsx";
        StoryboardJob job = jobService.createJob(projectId, storyboardId, versionId,
                jobType, request.idempotencyKey(), userId);
        job = jobService.executeExport(projectId, job.getId(), userId);
        return ApiResponse.success(toJobView(job));
    }

    @PostMapping("/api/v1/content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}/jobs/canvas-snapshot")
    public ApiResponse<JobView> createCanvasSnapshot(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId,
            @Valid @RequestBody CreateCanvasSnapshotRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        StoryboardJob job = jobService.createCanvasSnapshot(projectId, versionId, userId,
                request.snapshotType(), request.idempotencyKey());
        return ApiResponse.success(toJobView(job));
    }

    // ===== Global job queries =====
    @GetMapping("/api/v1/storyboard-jobs/{jobId}")
    public ApiResponse<JobView> getJob(@PathVariable Long jobId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(toJobView(jobService.getJob(jobId, userId)));
    }

    @GetMapping("/api/v1/content-projects/{projectId}/storyboard-jobs")
    public ApiResponse<List<JobView>> listJobs(@PathVariable Long projectId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(jobService.listJobs(projectId, userId).stream()
                .map(StoryboardJobController::toJobView).toList());
    }

    static JobView toJobView(StoryboardJob j) {
        return new JobView(j.getId(), j.getUuid(), j.getProjectId(), j.getStoryboardId(),
                j.getVersionId(), j.getJobType(), j.getStatus(), j.getIdempotencyKey(),
                j.getProgressPercent(), j.getCurrentStage(), j.getErrorCode(), j.getErrorMessage(),
                j.getStartedAt(), j.getFinishedAt(), j.getCreatedAt());
    }
}
