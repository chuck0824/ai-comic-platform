package com.aicp.module.trade.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.trade.dto.TradeRequests.CreateOrder;
import com.aicp.module.trade.dto.TradeViews.OrderView;
import com.aicp.module.trade.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Buyer-facing order endpoints.
 * Requires WorkspaceContext (X-Workspace-Id header).
 */
@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** Idempotent order creation. Free orders auto-fulfill. */
    @PostMapping("/orders")
    public ApiResponse<OrderView> createOrder(
            HttpServletRequest request,
            @Valid @RequestBody CreateOrder body) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        return ApiResponse.success(orderService.create(ctx, body));
    }

    /** Get order by order number. */
    @GetMapping("/orders/{orderNo}")
    public ApiResponse<OrderView> getOrder(
            HttpServletRequest request,
            @PathVariable String orderNo) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        return ApiResponse.success(orderService.getByOrderNo(ctx, orderNo));
    }

    /** List buyer's orders. */
    @GetMapping("/orders")
    public ApiResponse<List<OrderView>> listOrders(HttpServletRequest request) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        return ApiResponse.success(orderService.listByBuyer(ctx));
    }

    /** Confirm payment for a pending order. */
    @PostMapping("/orders/{orderNo}/pay")
    public ApiResponse<OrderView> payOrder(
            HttpServletRequest request,
            @PathVariable String orderNo) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        return ApiResponse.success(orderService.pay(ctx, orderNo));
    }

    /** Cancel a pending payment order. */
    @PostMapping("/orders/{orderNo}/cancel")
    public ApiResponse<Void> cancelOrder(
            HttpServletRequest request,
            @PathVariable String orderNo) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        orderService.cancel(ctx, orderNo);
        return ApiResponse.success();
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
