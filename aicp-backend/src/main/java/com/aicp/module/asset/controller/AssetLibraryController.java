package com.aicp.module.asset.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.dto.PageResult;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetRequests;
import com.aicp.module.asset.dto.AssetViews;
import com.aicp.module.asset.service.AssetApplicationService;
import com.aicp.module.asset.service.AssetLibraryService;
import com.aicp.module.asset.service.AssetPublicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/asset")
@RequiredArgsConstructor
public class AssetLibraryController {

    private final AssetLibraryService libraryService;
    private final AssetPublicationService publicationService;
    private final AssetApplicationService applicationService;

    // ---- Library CRUD ----

    @GetMapping("/library")
    public ApiResponse<PageResult<AssetViews.AssetView>> listLibrary(
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        return ApiResponse.success(libraryService.listLibrary(ctx, assetType, sourceType, page, pageSize));
    }

    @PostMapping("/library")
    public ApiResponse<AssetViews.AssetView> create(
            @Valid @RequestBody AssetRequests.CreateAssetRequest req, HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        return ApiResponse.success(libraryService.create(ctx, req));
    }

    @GetMapping("/library/{assetId}")
    public ApiResponse<AssetViews.AssetView> getAsset(@PathVariable Long assetId, HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        return ApiResponse.success(libraryService.getAsset(ctx, assetId));
    }

    @PutMapping("/library/{assetId}")
    public ApiResponse<AssetViews.AssetView> edit(@PathVariable Long assetId,
                                                   @Valid @RequestBody AssetRequests.EditAssetRequest req,
                                                   HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        return ApiResponse.success(libraryService.edit(ctx, assetId, req));
    }

    @PostMapping("/library/{assetId}/versions")
    public ApiResponse<AssetViews.VersionView> createVersion(@PathVariable Long assetId,
                                                              @Valid @RequestBody AssetRequests.CreateVersionRequest req,
                                                              HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        return ApiResponse.success(libraryService.createVersion(ctx, assetId, req));
    }

    @PostMapping("/library/{assetId}/archive")
    public ApiResponse<Void> archive(@PathVariable Long assetId,
                                      @RequestParam Integer rowVersion,
                                      HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        libraryService.archive(ctx, assetId, rowVersion);
        return ApiResponse.success();
    }

    // ---- Publishing ----

    @PostMapping("/library/{assetId}/publish")
    public ApiResponse<AssetViews.ListingView> publish(@PathVariable Long assetId,
                                                        @Valid @RequestBody AssetRequests.PublishAssetRequest req,
                                                        HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        return ApiResponse.success(publicationService.publishPersonal(ctx, assetId, req));
    }

    @PostMapping("/library/{assetId}/unlist")
    public ApiResponse<Void> unlist(@PathVariable Long assetId,
                                     @RequestParam Integer rowVersion,
                                     HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        publicationService.unlist(ctx, assetId, rowVersion);
        return ApiResponse.success();
    }

    @PostMapping("/library/{assetId}/publish-requests")
    public ApiResponse<AssetViews.PublishRequestView> requestPublish(@PathVariable Long assetId,
                                                                      @Valid @RequestBody AssetRequests.PublishAssetRequest req,
                                                                      HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        return ApiResponse.success(publicationService.requestEnterprisePublish(ctx, assetId, req));
    }

    // ---- Application ----

    @PostMapping("/library/{assetId}/applications")
    public ApiResponse<AssetViews.ApplyView> apply(@PathVariable Long assetId,
                                                    @Valid @RequestBody AssetRequests.ApplyAssetRequest req,
                                                    HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        return ApiResponse.success(applicationService.apply(ctx, assetId, req));
    }

    @PostMapping("/applications/{applicationId}/undo")
    public ApiResponse<Void> undo(@PathVariable Long applicationId,
                                   @Valid @RequestBody AssetRequests.UndoRequest req,
                                   HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        applicationService.undo(ctx, applicationId, req);
        return ApiResponse.success();
    }
}
