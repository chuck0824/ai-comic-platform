package com.aicp.module.trade.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.trade.wallet.WalletClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Wallet facade endpoints for the frontend.
 * Balance uses the signed internal client; top-up endpoints
 * forward to 3001 user endpoints.
 */
@RestController
@RequestMapping("/api/v1/trade/wallet")
@RequiredArgsConstructor
public class TradeWalletController {

    private final WalletClient walletClient;

    /** Get balance for the current workspace owner. */
    @GetMapping("/balance")
    public ApiResponse<WalletClient.BalanceResult> getBalance(HttpServletRequest request) {
        WorkspaceContext ctx = requireWorkspaceContext(request);
        String ownerType = "PERSONAL".equals(ctx.workspaceType()) ? "USER" : "WORKSPACE";
        return ApiResponse.success(walletClient.getBalance(ownerType, ctx.workspaceId()));
    }

    /** Get available top-up channels from 3001. */
    @GetMapping("/topup-info")
    public ApiResponse<Map<String, Object>> getTopUpInfo() {
        // Forward to 3001 /api/user/topup/info — stubbed for now
        return ApiResponse.success(Map.of(
                "channels", java.util.List.of("epay", "stripe"),
                "min_amount_cents", 100
        ));
    }

    /** Create a top-up order (forwards to 3001). */
    @PostMapping("/topups")
    public ApiResponse<Map<String, Object>> createTopUp(@RequestBody Map<String, Object> body) {
        // Forward to 3001 — stubbed for now
        return ApiResponse.success(Map.of(
                "trade_no", "TOPUP-" + System.currentTimeMillis(),
                "status", "pending"
        ));
    }

    private WorkspaceContext requireWorkspaceContext(HttpServletRequest request) {
        WorkspaceContext ctx = WorkspaceContext.get(request);
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少Workspace上下文");
        }
        return ctx;
    }
}
