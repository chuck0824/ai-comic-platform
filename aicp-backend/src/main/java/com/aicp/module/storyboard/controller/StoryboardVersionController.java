package com.aicp.module.storyboard.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.storyboard.dto.StoryboardRequests.*;
import com.aicp.module.storyboard.dto.StoryboardViews.*;
import com.aicp.module.storyboard.service.StoryboardVersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content-projects/{projectId}/storyboards/{storyboardId}/versions")
@RequiredArgsConstructor
public class StoryboardVersionController {

    private final StoryboardVersionService versionService;

    @GetMapping
    public ApiResponse<List<VersionSummary>> list(
            @PathVariable Long projectId,
            @PathVariable Long storyboardId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(versionService.listVersions(projectId, storyboardId, userId));
    }

    @GetMapping("/{versionId}")
    public ApiResponse<VersionDetail> get(
            @PathVariable Long projectId,
            @PathVariable Long storyboardId,
            @PathVariable Long versionId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(versionService.getVersion(projectId, versionId, userId));
    }

    @GetMapping("/{versionId}/diff")
    public ApiResponse<VersionDiff> diff(
            @PathVariable Long projectId,
            @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @RequestParam Long against) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(versionService.getDiff(projectId, versionId, against, userId));
    }

    @PostMapping("/{versionId}/submit-review")
    public ApiResponse<VersionDetail> submitReview(
            @PathVariable Long projectId,
            @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @Valid @RequestBody SubmitReviewRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(versionService.submitForReview(
                projectId, versionId, userId, request.revision()));
    }

    @PostMapping("/{versionId}/lock")
    public ApiResponse<VersionDetail> lock(
            @PathVariable Long projectId,
            @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @Valid @RequestBody LockRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = SecurityUtil.requireCurrentUserId();
        String key = idempotencyKey != null ? idempotencyKey : java.util.UUID.randomUUID().toString();
        return ApiResponse.success(versionService.lockVersion(
                projectId, versionId, userId, request.revision(), key));
    }

    @PostMapping("/{versionId}/fork")
    public ApiResponse<VersionDetail> fork(
            @PathVariable Long projectId,
            @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = SecurityUtil.requireCurrentUserId();
        String key = idempotencyKey != null ? idempotencyKey : java.util.UUID.randomUUID().toString();
        return ApiResponse.success(versionService.forkVersion(projectId, versionId, userId, key));
    }

    @PostMapping("/{versionId}/upgrade")
    public ApiResponse<VersionDetail> upgrade(
            @PathVariable Long projectId,
            @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @Valid @RequestBody UpgradeRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(versionService.upgradeVersion(
                projectId, versionId, userId, request.targetTier(), request.idempotencyKey()));
    }
}
