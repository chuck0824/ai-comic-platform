package com.aicp.module.storyboard.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.storyboard.dto.StoryboardRequests.*;
import com.aicp.module.storyboard.dto.StoryboardViews.*;
import com.aicp.module.storyboard.service.StoryboardEditingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}")
@RequiredArgsConstructor
public class StoryboardEditingController {

    private final StoryboardEditingService editingService;

    // ===== Scenes =====

    @GetMapping("/scenes")
    public ApiResponse<List<SceneView>> listScenes(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.listScenes(projectId, versionId, userId));
    }

    @PostMapping("/scenes")
    public ApiResponse<SceneView> createScene(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @Valid @RequestBody CreateSceneRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.createScene(projectId, versionId, userId, request));
    }

    @PatchMapping("/scenes/{sceneId}")
    public ApiResponse<SceneView> patchScene(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId, @PathVariable Long sceneId,
            @Valid @RequestBody PatchSceneRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.patchScene(projectId, versionId, sceneId, userId, request));
    }

    @DeleteMapping("/scenes/{sceneId}")
    public ApiResponse<Void> deleteScene(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId, @PathVariable Long sceneId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        editingService.deleteScene(projectId, versionId, sceneId, userId);
        return ApiResponse.success();
    }

    @PostMapping("/scenes/reorder")
    public ApiResponse<Void> reorderScenes(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @Valid @RequestBody ReorderScenesRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        editingService.reorderScenes(projectId, versionId, userId, request);
        return ApiResponse.success();
    }

    // ===== Shots =====

    @GetMapping("/shots")
    public ApiResponse<List<ShotSummary>> listShots(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @RequestParam(required = false) Long sceneId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.listShots(
                projectId, versionId, userId, sceneId, status, page, size));
    }

    @GetMapping("/shots/{shotId}")
    public ApiResponse<ShotDetail> getShot(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId, @PathVariable Long shotId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.getShotDetail(projectId, versionId, shotId, userId));
    }

    @PostMapping("/shots")
    public ApiResponse<ShotDetail> createShot(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @Valid @RequestBody CreateShotRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.createShot(projectId, versionId, userId, request));
    }

    @PatchMapping("/shots/{shotId}")
    public ApiResponse<ShotDetail> patchShot(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId, @PathVariable Long shotId,
            @Valid @RequestBody PatchShotRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.patchShot(projectId, versionId, shotId, userId, request));
    }

    @DeleteMapping("/shots/{shotId}")
    public ApiResponse<Void> deleteShot(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId, @PathVariable Long shotId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        editingService.deleteShot(projectId, versionId, shotId, userId);
        return ApiResponse.success();
    }

    @PostMapping("/shots/batch")
    public ApiResponse<List<ShotDetail>> batchPatchShots(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @Valid @RequestBody BatchPatchShotsRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.batchPatchShots(projectId, versionId, userId, request));
    }

    @PostMapping("/shots/reorder")
    public ApiResponse<Void> reorderShots(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @Valid @RequestBody ReorderShotsRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        editingService.reorderShots(projectId, versionId, userId, request);
        return ApiResponse.success();
    }

    @PostMapping("/shots/{shotId}/split")
    public ApiResponse<List<ShotDetail>> splitShot(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId, @PathVariable Long shotId,
            @Valid @RequestBody SplitShotRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.splitShot(projectId, versionId, shotId, userId, request));
    }

    @PostMapping("/shots/merge")
    public ApiResponse<ShotDetail> mergeShots(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId,
            @Valid @RequestBody MergeShotsRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.mergeShots(projectId, versionId, userId, request));
    }

    @PostMapping("/shots/{shotId}/copy")
    public ApiResponse<ShotDetail> copyShot(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId, @PathVariable Long shotId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.copyShot(projectId, versionId, shotId, userId));
    }

    @PutMapping("/shots/{shotId}/scene-asset")
    public ApiResponse<ShotDetail> bindSceneAsset(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId, @PathVariable Long shotId,
            @Valid @RequestBody BindSceneAssetRequest request) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.bindSceneAsset(
                projectId, storyboardId, versionId, shotId, userId, request));
    }

    @PostMapping("/continuity-check")
    public ApiResponse<ContinuityCheckView> continuityCheck(
            @PathVariable Long projectId, @PathVariable Long storyboardId,
            @PathVariable Long versionId) {
        Long userId = SecurityUtil.requireCurrentUserId();
        return ApiResponse.success(editingService.continuityCheck(
                projectId, storyboardId, versionId, userId));
    }
}
