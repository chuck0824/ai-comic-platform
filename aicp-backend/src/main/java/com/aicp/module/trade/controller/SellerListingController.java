package com.aicp.module.trade.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.trade.dto.TradeRequests.CreateListing;
import com.aicp.module.trade.dto.TradeRequests.UpdateListing;
import com.aicp.module.trade.dto.TradeRequests.ReviewDecision;
import com.aicp.module.trade.entity.ScriptListing;
import com.aicp.module.trade.service.ListingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Seller-facing listing management endpoints.
 * Requires WorkspaceContext (X-Workspace-Id header).
 */
@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
public class SellerListingController {

    private final ListingService listingService;

    /** Create a listing draft from a warehouse script version. */
    @PostMapping("/listings")
    public ApiResponse<ScriptListing> createDraft(
            HttpServletRequest request,
            @Valid @RequestBody CreateListing body) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        return ApiResponse.success(listingService.createDraft(ctx, body));
    }

    /** Update a listing draft. */
    @PutMapping("/listings/{listingId}")
    public ApiResponse<ScriptListing> updateDraft(
            HttpServletRequest request,
            @PathVariable Long listingId,
            @Valid @RequestBody UpdateListing body) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        return ApiResponse.success(listingService.updateDraft(ctx, listingId, body));
    }

    /** Submit listing for platform review. */
    @PostMapping("/listings/{listingId}/submit")
    public ApiResponse<ScriptListing> submit(
            HttpServletRequest request,
            @PathVariable Long listingId) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        return ApiResponse.success(listingService.submit(ctx, listingId));
    }

    /** Withdraw a listing from review. */
    @PostMapping("/listings/{listingId}/withdraw")
    public ApiResponse<ScriptListing> withdraw(
            HttpServletRequest request,
            @PathVariable Long listingId) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        return ApiResponse.success(listingService.withdraw(ctx, listingId));
    }

    /** Unlist a listing (stop new orders). */
    @PostMapping("/listings/{listingId}/unlist")
    public ApiResponse<ScriptListing> unlist(
            HttpServletRequest request,
            @PathVariable Long listingId) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        return ApiResponse.success(listingService.unlist(ctx, listingId));
    }

    /** List all listings owned by current workspace. */
    @GetMapping("/listings")
    public ApiResponse<List<ScriptListing>> listByWorkspace(HttpServletRequest request) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        return ApiResponse.success(listingService.listByWorkspace(ctx.workspaceId()));
    }

    /** Platform: approve a listing. */
    @PostMapping("/reviews/{listingId}/approve")
    public ApiResponse<ScriptListing> approve(
            HttpServletRequest request,
            @PathVariable Long listingId,
            @Valid @RequestBody ReviewDecision body) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        ctx.require("trade.listing.review");
        return ApiResponse.success(listingService.approve(ctx.userId(), listingId, body.reason()));
    }

    /** Platform: reject a listing. */
    @PostMapping("/reviews/{listingId}/reject")
    public ApiResponse<ScriptListing> reject(
            HttpServletRequest request,
            @PathVariable Long listingId,
            @Valid @RequestBody ReviewDecision body) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        ctx.require("trade.listing.review");
        return ApiResponse.success(listingService.reject(ctx.userId(), listingId, body.reason()));
    }

    // -- helpers --

    private WorkspaceContext requireWorkspaceContext(HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少Workspace上下文");
        }
        return ctx;
    }
}
