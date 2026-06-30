package com.aicp.module.asset.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.dto.PageResult;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetViews;
import com.aicp.module.asset.service.AssetClaimService;
import com.aicp.module.asset.service.MarketQueryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/asset/market")
@RequiredArgsConstructor
public class AssetMarketController {

    private final MarketQueryService marketQueryService;
    private final AssetClaimService claimService;

    @GetMapping("/listings")
    public ApiResponse<PageResult<AssetViews.ListingCard>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        PageResult<AssetViews.ListingCard> result = marketQueryService.search(
                keyword, type, sort, page, pageSize, ctx);
        return ApiResponse.success(result);
    }

    @GetMapping("/listings/{listingId}")
    public ApiResponse<AssetViews.ListingDetail> getDetail(
            @PathVariable Long listingId,
            HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        AssetViews.ListingDetail detail = marketQueryService.getDetail(listingId, ctx);
        if (detail == null) {
            return ApiResponse.error(48001, "资产不存在或已下架");
        }
        return ApiResponse.success(detail);
    }

    @PostMapping("/listings/{listingId}/claim")
    public ApiResponse<AssetViews.ClaimView> claim(@PathVariable Long listingId,
                                                    HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        return ApiResponse.success(claimService.claim(ctx, listingId));
    }

    @PutMapping("/listings/{listingId}/favorite")
    public ApiResponse<Void> favorite(@PathVariable Long listingId,
                                       HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        claimService.favorite(ctx, listingId);
        return ApiResponse.success();
    }

    @DeleteMapping("/listings/{listingId}/favorite")
    public ApiResponse<Void> unfavorite(@PathVariable Long listingId,
                                         HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        claimService.unfavorite(ctx, listingId);
        return ApiResponse.success();
    }
}
