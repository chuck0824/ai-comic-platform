package com.aicp.module.contentproject.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.CreateSceneAssetRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.CreateSceneVariantRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.FromWorldLocationRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.RestoreSceneAssetRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.UpdateSceneVariantRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.UpdateSceneAssetRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetImpactView;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetView;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetVersionView;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetMarkdownView;
import com.aicp.module.contentproject.service.ProjectSceneAssetService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content-projects/{projectId}/scene-assets")
@RequiredArgsConstructor
public class ProjectSceneAssetController {

    private final ProjectSceneAssetService sceneAssets;
    private final ObjectMapper objectMapper;

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

    @PostMapping("/from-location")
    public ApiResponse<SceneAssetView> fromLocation(@PathVariable Long projectId,
                                                     @Valid @RequestBody FromWorldLocationRequest request) {
        return ApiResponse.success(sceneAssets.fromLocation(SecurityUtil.requireCurrentUserId(), projectId, request));
    }

    @GetMapping("/{assetId}")
    public ApiResponse<SceneAssetView> get(@PathVariable Long projectId, @PathVariable Long assetId) {
        return ApiResponse.success(sceneAssets.get(SecurityUtil.requireCurrentUserId(), projectId, assetId));
    }

    @PatchMapping("/{assetId}")
    public ApiResponse<SceneAssetView> update(@PathVariable Long projectId, @PathVariable Long assetId,
                                               @RequestBody JsonNode payload) {
        if (payload.has("variants")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "场景变体只能通过专用变体接口修改");
        }
        UpdateSceneAssetRequest request = objectMapper.convertValue(payload, UpdateSceneAssetRequest.class);
        return ApiResponse.success(sceneAssets.update(SecurityUtil.requireCurrentUserId(), projectId, assetId, request));
    }

    @PostMapping("/{assetId}/variants")
    public ApiResponse<SceneAssetView> createVariant(@PathVariable Long projectId, @PathVariable Long assetId,
                                                      @Valid @RequestBody CreateSceneVariantRequest request) {
        return ApiResponse.success(sceneAssets.createVariant(SecurityUtil.requireCurrentUserId(), projectId, assetId, request));
    }

    @PatchMapping("/{assetId}/variants/{variantId}")
    public ApiResponse<SceneAssetView> updateVariant(@PathVariable Long projectId, @PathVariable Long assetId,
                                                      @PathVariable String variantId,
                                                      @Valid @RequestBody UpdateSceneVariantRequest request) {
        return ApiResponse.success(sceneAssets.updateVariant(SecurityUtil.requireCurrentUserId(), projectId, assetId,
                variantId, request));
    }

    @GetMapping("/{assetId}/markdown")
    public ApiResponse<SceneAssetMarkdownView> markdown(@PathVariable Long projectId, @PathVariable Long assetId) {
        return ApiResponse.success(sceneAssets.markdown(SecurityUtil.requireCurrentUserId(), projectId, assetId));
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

    @PostMapping("/{assetId}/disable")
    public ApiResponse<SceneAssetView> disable(@PathVariable Long projectId, @PathVariable Long assetId) {
        return ApiResponse.success(sceneAssets.disable(SecurityUtil.requireCurrentUserId(), projectId, assetId));
    }

    @PostMapping("/{assetId}/activate")
    public ApiResponse<SceneAssetView> activate(@PathVariable Long projectId, @PathVariable Long assetId) {
        return ApiResponse.success(sceneAssets.activate(SecurityUtil.requireCurrentUserId(), projectId, assetId));
    }

    @GetMapping("/{assetId}/impact")
    public ApiResponse<SceneAssetImpactView> impact(@PathVariable Long projectId, @PathVariable Long assetId) {
        return ApiResponse.success(sceneAssets.impact(SecurityUtil.requireCurrentUserId(), projectId, assetId));
    }
}
