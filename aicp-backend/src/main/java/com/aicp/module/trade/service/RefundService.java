package com.aicp.module.trade.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.trade.domain.TradeEnums.*;
import com.aicp.module.trade.dto.TradeViews.RefundView;
import com.aicp.module.trade.entity.*;
import com.aicp.module.trade.mapper.*;
import com.aicp.module.enterprise.service.PurchaseBudgetService;
import com.aicp.module.trade.wallet.WalletClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Refund requests and controlled reversal. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRequestMapper refundMapper;
    private final TradeOrderMapper orderMapper;
    private final ScriptEntitlementMapper entitlementMapper;
    private final ScriptListingMapper listingMapper;
    private final WalletClient walletClient;
    private final PurchaseBudgetService budgetService;
    private final PurchaseRequestMapper purchaseRequestMapper;

    @Transactional
    public RefundView request(WorkspaceContext ctx, String orderNo, String reasonCode, String reasonText) {
        var order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo)
                .eq(TradeOrder::getBuyerWorkspaceId, ctx.workspaceId()));
        if (order == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!order.getStatus().equals(OrderStatus.FULFILLED.name()))
            throw new BizException(ErrorCode.REFUND_NOT_ALLOWED);

        order.setStatus(OrderStatus.REFUND_REQUESTED.name());
        orderMapper.updateById(order);

        // Lock entitlement
        var ent = entitlementMapper.selectOne(new LambdaQueryWrapper<ScriptEntitlement>()
                .eq(ScriptEntitlement::getBeneficiaryWorkspaceId, ctx.workspaceId()));
        if (ent != null) {
            ent.setStatus(EntitlementStatus.REFUND_LOCKED.name());
            entitlementMapper.updateById(ent);
        }

        RefundRequest rf = new RefundRequest();
        rf.setOrderNo(orderNo);
        rf.setRequesterUserId(ctx.userId());
        rf.setReasonCode(reasonCode);
        rf.setReasonText(reasonText);
        rf.setStatus(RefundStatus.REQUESTED.name());
        rf.setRefundAmountCents(order.getTotalAmountCents());
        refundMapper.insert(rf);

        return new RefundView(rf.getId(), rf.getOrderNo(), rf.getReasonCode(), rf.getReasonText(),
                rf.getStatus(), rf.getRefundAmountCents(), null, rf.getCreatedAt());
    }

    @Transactional
    public RefundView approve(WorkspaceContext ctx, Long refundId, String comment) {
        ctx.require("trade.refund.review");

        var rf = refundMapper.selectById(refundId);
        if (rf == null) throw new BizException(ErrorCode.NOT_FOUND);

        rf.setStatus(RefundStatus.PROCESSING.name());
        refundMapper.updateById(rf);

        // 1. Reverse wallet transfer
        try {
            var order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                    .eq(TradeOrder::getOrderNo, rf.getOrderNo()));
            if (order != null && order.getWalletTransferNo() != null) {
                walletClient.reverse(order.getWalletTransferNo(), rf.getRefundAmountCents(),
                        "trade:refund:" + rf.getOrderNo());
                // Reverse purchase budget
                reverseEnterpriseBudget(order.getOrderNo(), order.getWalletTransferNo());
            }
        } catch (Exception e) {
            log.error("Wallet reversal failed for refund {}: {}", refundId, e.getMessage());
            throw new BizException(ErrorCode.PAY_FAILED, "退款冲正失败: " + e.getMessage());
        }

        // 2. Revoke entitlement
        var ent = entitlementMapper.selectOne(new LambdaQueryWrapper<ScriptEntitlement>()
                .eq(ScriptEntitlement::getBeneficiaryWorkspaceId,
                        orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                                .eq(TradeOrder::getOrderNo, rf.getOrderNo())).getBuyerWorkspaceId()));
        if (ent != null) {
            ent.setStatus(EntitlementStatus.REVOKED.name());
            entitlementMapper.updateById(ent);
        }

        // 3. Restore inventory if exclusive/buyout
        var order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, rf.getOrderNo()));
        if (order != null) {
            listingMapper.restoreListed(order.getOrderNo());
            order.setStatus(OrderStatus.REFUNDED.name());
            orderMapper.updateById(order);
        }

        rf.setStatus(RefundStatus.REFUNDED.name());
        rf.setReviewerUserId(ctx.userId());
        rf.setReviewComment(comment);
        refundMapper.updateById(rf);

        log.info("Refund approved: id={}, orderNo={}", refundId, rf.getOrderNo());
        return new RefundView(rf.getId(), rf.getOrderNo(), rf.getReasonCode(), rf.getReasonText(),
                rf.getStatus(), rf.getRefundAmountCents(), rf.getReviewComment(), rf.getCreatedAt());
    }

    private void reverseEnterpriseBudget(String orderNo, String reversalNo) {
        try {
            var pr = purchaseRequestMapper.selectOne(new LambdaQueryWrapper<PurchaseRequest>()
                    .eq(PurchaseRequest::getOrderNo, orderNo));
            if (pr != null && pr.getBudgetReservationEntryId() != null) {
                budgetService.reverse(null,
                        pr.getBudgetReservationEntryId(),
                        pr.getAmountCents(),
                        reversalNo,
                        "purchase:" + pr.getBudgetReservationEntryId() + ":reverse:" + reversalNo);
                log.info("Budget reversed for purchase {}: {} cents", pr.getId(), pr.getAmountCents());
            }
        } catch (Exception e) {
            log.warn("Budget reverse skipped for order {}: {}", orderNo, e.getMessage());
        }
    }
}
