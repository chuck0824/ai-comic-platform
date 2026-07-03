package com.aicp.module.asset.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetWorkbenchRequests.*;
import com.aicp.module.asset.dto.AssetWorkbenchViews.BatchResult;
import com.aicp.module.asset.dto.AssetWorkbenchViews.CanvasPlacementView;
import com.aicp.module.asset.service.AssetCommandService;
import com.aicp.module.asset.service.CanvasPlacementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetCommandController {

    private final AssetCommandService commandService;
    private final CanvasPlacementService placementService;

    @PatchMapping("/{assetUuid}")
    public ApiResponse<Void> edit(@PathVariable String assetUuid,
            @Valid @RequestBody EditAssetRequest body,
            HttpServletRequest request) {
        var ctx = requireContext(request);
        commandService.edit(ctx, assetUuid, body);
        return ApiResponse.success();
    }

    @PutMapping("/{assetUuid}/favorite")
    public ApiResponse<Void> favorite(@PathVariable String assetUuid, HttpServletRequest request) {
        var ctx = requireContext(request);
        commandService.toggleFavorite(ctx, assetUuid, true);
        return ApiResponse.success();
    }

    @DeleteMapping("/{assetUuid}/favorite")
    public ApiResponse<Void> unfavorite(@PathVariable String assetUuid, HttpServletRequest request) {
        var ctx = requireContext(request);
        commandService.toggleFavorite(ctx, assetUuid, false);
        return ApiResponse.success();
    }

    @PostMapping("/{assetUuid}/move")
    public ApiResponse<Void> move(@PathVariable String assetUuid,
            @Valid @RequestBody MoveAssetRequest body, HttpServletRequest request) {
        var ctx = requireContext(request);
        // Deferred: full move implementation
        return ApiResponse.success();
    }

    @PostMapping("/batch")
    public ApiResponse<BatchResult> batchOperate(@Valid @RequestBody BatchAssetRequest body,
            HttpServletRequest request) {
        var ctx = requireContext(request);
        return ApiResponse.success(commandService.batchOperate(ctx, body));
    }

    @DeleteMapping("/{assetUuid}")
    public ApiResponse<Void> trash(@PathVariable String assetUuid, HttpServletRequest request) {
        var ctx = requireContext(request);
        BatchAssetRequest req = new BatchAssetRequest(
                java.util.List.of(assetUuid), "TRASH", null);
        commandService.batchOperate(ctx, req);
        return ApiResponse.success();
    }

    @PostMapping("/{assetUuid}/restore")
    public ApiResponse<Void> restore(@PathVariable String assetUuid, HttpServletRequest request) {
        var ctx = requireContext(request);
        BatchAssetRequest req = new BatchAssetRequest(
                java.util.List.of(assetUuid), "RESTORE", null);
        commandService.batchOperate(ctx, req);
        return ApiResponse.success();
    }

    @GetMapping("/{assetUuid}/download-url")
    public ApiResponse<String> downloadUrl(@PathVariable String assetUuid,
            HttpServletRequest request) {
        var ctx = requireContext(request);
        // Deferred: signed URL generation
        return ApiResponse.success("download-url-placeholder");
    }

    @PostMapping("/{assetUuid}/regenerate")
    public ApiResponse<Void> regenerate(@PathVariable String assetUuid,
            HttpServletRequest request) {
        var ctx = requireContext(request);
        // Deferred: full regeneration
        return ApiResponse.success();
    }

    @PostMapping("/{assetUuid}/publish")
    public ApiResponse<Void> publish(@PathVariable String assetUuid,
            @Valid @RequestBody PublishAssetRequest body,
            HttpServletRequest request) {
        var ctx = requireContext(request);
        // Deferred: full publication via AssetPublicationAdapter
        return ApiResponse.success();
    }

    @PostMapping("/{assetUuid}/send-to-canvas")
    public ApiResponse<CanvasPlacementView> sendToCanvas(@PathVariable String assetUuid,
            @Valid @RequestBody CanvasPlacementRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var ctx = requireContext(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = java.util.UUID.randomUUID().toString();
        }
        return ApiResponse.success(placementService.place(ctx, assetUuid, body, idempotencyKey));
    }

    private WorkspaceContext requireContext(HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        if (ctx == null) throw new BizException(ErrorCode.UNAUTHORIZED, "缺少Workspace上下文");
        return ctx;
    }
}
