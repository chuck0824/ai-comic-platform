package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.CreateSceneAssetRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.RestoreSceneAssetRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.UpdateSceneAssetRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetImpactView;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetView;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetVersionView;
import com.aicp.module.contentproject.service.ProjectSceneAssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content-projects/{projectId}/scene-assets")
@RequiredArgsConstructor
public class ProjectSceneAssetController {

    private final ProjectSceneAssetService sceneAssets;

    @GetMapping
    public ApiResponse<List<SceneAssetView>> list(@PathVariable Long projectId,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(name = "space_type", required = false) String spaceType,
                                                   @RequestParam(required = false) String reusability,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Boolean referenced) {
        return ApiResponse.success(sceneAssets.list(SecurityUtil.requireCurrentUserId(), projectId,
                keyword, spaceType, reusability, status, referenced));
    }

    @PostMapping
    public ApiResponse<SceneAssetView> create(@PathVariable Long projectId,
                                               @Valid @RequestBody CreateSceneAssetRequest request) {
        return ApiResponse.success(sceneAssets.create(SecurityUtil.requireCurrentUserId(), projectId, request));
    }

    @GetMapping("/{assetId}")
    public ApiResponse<SceneAssetView> get(@PathVariable Long projectId, @PathVariable Long assetId) {
        return ApiResponse.success(sceneAssets.get(SecurityUtil.requireCurrentUserId(), projectId, assetId));
    }

    @PatchMapping("/{assetId}")
    public ApiResponse<SceneAssetView> update(@PathVariable Long projectId, @PathVariable Long assetId,
                                               @Valid @RequestBody UpdateSceneAssetRequest request) {
        return ApiResponse.success(sceneAssets.update(SecurityUtil.requireCurrentUserId(), projectId, assetId, request));
    }

    @PostMapping("/{assetId}/versions/{versionId}/restore")
    public ApiResponse<SceneAssetVersionView> restore(@PathVariable Long projectId, @PathVariable Long assetId,
                                                       @PathVariable Long versionId,
                                                       @RequestBody(required = false) RestoreSceneAssetRequest request) {
        return ApiResponse.success(sceneAssets.restore(SecurityUtil.requireCurrentUserId(), projectId, assetId,
                versionId, request));
    }

    @PostMapping("/{assetId}/archive")
    public ApiResponse<Void> archive(@PathVariable Long projectId, @PathVariable Long assetId) {
        sceneAssets.archive(SecurityUtil.requireCurrentUserId(), projectId, assetId);
        return ApiResponse.success();
    }

    @GetMapping("/{assetId}/impact")
    public ApiResponse<SceneAssetImpactView> impact(@PathVariable Long projectId, @PathVariable Long assetId) {
        return ApiResponse.success(sceneAssets.impact(SecurityUtil.requireCurrentUserId(), projectId, assetId));
    }
}
