package com.aicp.module.asset.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.dto.PageResult;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetRequests;
import com.aicp.module.asset.dto.AssetViews;
import com.aicp.module.asset.entity.AssetPublishRequest;
import com.aicp.module.asset.mapper.AssetPublishRequestMapper;
import com.aicp.module.asset.service.AssetPublicationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/asset")
@RequiredArgsConstructor
public class AssetPublishController {

    private final AssetPublicationService publicationService;
    private final AssetPublishRequestMapper publishRequestMapper;

    @GetMapping("/publish-requests")
    public ApiResponse<PageResult<AssetViews.PublishRequestView>> listRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        LambdaQueryWrapper<AssetPublishRequest> qw = new LambdaQueryWrapper<>();
        qw.eq(AssetPublishRequest::getWorkspaceId, ctx.workspaceId());
        if (status != null) qw.eq(AssetPublishRequest::getStatus, status);
        qw.orderByDesc(AssetPublishRequest::getCreatedAt);

        Page<AssetPublishRequest> mpPage = new Page<>(page, Math.min(pageSize, 50));
        Page<AssetPublishRequest> result = publishRequestMapper.selectPage(mpPage, qw);

        List<AssetViews.PublishRequestView> views = result.getRecords().stream()
                .map(pr -> new AssetViews.PublishRequestView(pr.getId(), pr.getAssetId(),
                        pr.getVersionId(), pr.getRequesterId(), pr.getReviewerId(),
                        pr.getStatus(), pr.getReason(), pr.getReviewComment(),
                        pr.getRowVersion(), pr.getCreatedAt(), pr.getUpdatedAt()))
                .toList();
        return ApiResponse.success(PageResult.of(views, page, Math.min(pageSize, 50), result.getTotal()));
    }

    @GetMapping("/publish-requests/{requestId}")
    public ApiResponse<AssetViews.PublishRequestView> getRequest(@PathVariable Long requestId,
                                                                  HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        AssetPublishRequest pr = publishRequestMapper.selectById(requestId);
        if (pr == null || !ctx.workspaceId().equals(pr.getWorkspaceId())) {
            return ApiResponse.error(48001, "发布申请不存在");
        }
        return ApiResponse.success(new AssetViews.PublishRequestView(pr.getId(), pr.getAssetId(),
                pr.getVersionId(), pr.getRequesterId(), pr.getReviewerId(),
                pr.getStatus(), pr.getReason(), pr.getReviewComment(),
                pr.getRowVersion(), pr.getCreatedAt(), pr.getUpdatedAt()));
    }

    @PostMapping("/publish-requests/{requestId}/approve")
    public ApiResponse<AssetViews.ListingView> approve(@PathVariable Long requestId,
                                                        @Valid @RequestBody AssetRequests.ReviewRequest body,
                                                        HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        return ApiResponse.success(publicationService.approve(ctx, requestId, body));
    }

    @PostMapping("/publish-requests/{requestId}/reject")
    public ApiResponse<AssetViews.PublishRequestView> reject(@PathVariable Long requestId,
                                                              @Valid @RequestBody AssetRequests.ReviewRequest body,
                                                              HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        return ApiResponse.success(publicationService.reject(ctx, requestId, body));
    }

    @PostMapping("/publish-requests/{requestId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long requestId, HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        publicationService.cancel(ctx, requestId);
        return ApiResponse.success();
    }
}
