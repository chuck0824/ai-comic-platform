package com.aicp.module.trade.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.trade.dto.TradeRequests.*;
import com.aicp.module.trade.dto.TradeViews.PurchaseRequestView;
import com.aicp.module.trade.service.PurchaseApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
public class PurchaseApprovalController {

    private final PurchaseApprovalService service;

    @PostMapping("/purchase-requests")
    public ApiResponse<PurchaseRequestView> submit(HttpServletRequest request,
            @Valid @RequestBody CreatePurchaseRequest body) {
        return ApiResponse.success(service.submit(requireCtx(request), body));
    }

    @PostMapping("/purchase-requests/{id}/approve")
    public ApiResponse<PurchaseRequestView> approve(HttpServletRequest request,
            @PathVariable Long id, @Valid @RequestBody ApprovalDecision body) {
        return ApiResponse.success(service.approve(requireCtx(request), id, body));
    }

    @PostMapping("/purchase-requests/{id}/reject")
    public ApiResponse<PurchaseRequestView> reject(HttpServletRequest request,
            @PathVariable Long id, @Valid @RequestBody ApprovalDecision body) {
        return ApiResponse.success(service.reject(requireCtx(request), id, body));
    }

    @GetMapping("/purchase-requests")
    public ApiResponse<List<PurchaseRequestView>> listByWorkspace(HttpServletRequest request) {
        return ApiResponse.success(service.listByWorkspace(requireCtx(request).workspaceId()));
    }

    private WorkspaceContext requireCtx(HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        if (ctx == null) throw new BizException(ErrorCode.UNAUTHORIZED, "缺少Workspace上下文");
        return ctx;
    }
}
