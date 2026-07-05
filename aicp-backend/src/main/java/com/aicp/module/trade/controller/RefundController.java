package com.aicp.module.trade.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.trade.dto.TradeRequests.CreateRefundRequest;
import com.aicp.module.trade.dto.TradeRequests.ReviewDecision;
import com.aicp.module.trade.dto.TradeViews.RefundView;
import com.aicp.module.trade.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping("/orders/{orderNo}/refund-requests")
    public ApiResponse<RefundView> request(HttpServletRequest request,
            @PathVariable String orderNo, @Valid @RequestBody CreateRefundRequest body) {
        return ApiResponse.success(refundService.request(requireCtx(request),
                orderNo, body.reasonCode(), body.reasonText()));
    }

    @PostMapping("/refund-requests/{id}/approve")
    public ApiResponse<RefundView> approve(HttpServletRequest request,
            @PathVariable Long id, @Valid @RequestBody ReviewDecision body) {
        return ApiResponse.success(refundService.approve(requireCtx(request), id, body.reason()));
    }

    private WorkspaceContext requireCtx(HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        if (ctx == null) throw new BizException(ErrorCode.UNAUTHORIZED, "缺少Workspace上下文");
        return ctx;
    }
}
