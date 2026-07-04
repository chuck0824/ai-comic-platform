package com.aicp.module.trade.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.trade.domain.TradeEnums.LicenseType;
import com.aicp.module.trade.domain.TradeEnums.OrderStatus;
import com.aicp.module.trade.dto.TradeRequests.CreateOrder;
import com.aicp.module.trade.dto.TradeViews.OrderView;
import com.aicp.module.trade.entity.*;
import com.aicp.module.trade.mapper.*;
import com.aicp.module.enterprise.service.PurchaseBudgetService;
import com.aicp.module.trade.wallet.WalletClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Order creation, payment orchestration, and state management.
 * All buyer mutations require trusted {@link WorkspaceContext}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final TradeOrderMapper orderMapper;
    private final TradeOrderItemMapper orderItemMapper;
    private final ScriptListingMapper listingMapper;
    private final ListingLicenseOptionMapper licenseOptionMapper;
    private final DeliveryService deliveryService;
    private final WalletClient walletClient;
    private final PurchaseBudgetService budgetService;
    private final PurchaseRequestMapper purchaseRequestMapper;

    /**
     * Idempotent order creation with server-derived price snapshot.
     */
    @Transactional
    public OrderView create(WorkspaceContext ctx, CreateOrder request) {
        // Idempotency check
        var existing = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getBuyerWorkspaceId, ctx.workspaceId())
                .eq(TradeOrder::getCreateIdempotencyKey, request.idempotencyKey()));
        if (existing != null) {
            return toView(existing);
        }

        // Validate listing
        ScriptListing listing = listingMapper.selectById(request.listingId());
        if (listing == null || !listing.getListingStatus().equals("LISTED")
                && !listing.getListingStatus().equals("EXCLUSIVE_RESERVED")) {
            throw new BizException(ErrorCode.LISTING_NOT_AVAILABLE);
        }

        // Validate license option (server-side price)
        var option = licenseOptionMapper.selectOne(new LambdaQueryWrapper<ListingLicenseOption>()
                .eq(ListingLicenseOption::getListingId, request.listingId())
                .eq(ListingLicenseOption::getLicenseType, request.licenseType())
                .eq(ListingLicenseOption::getEnabled, 1));
        if (option == null) {
            throw new BizException(ErrorCode.LICENSE_OPTION_NOT_AVAILABLE);
        }

        long amountCents = option.getPriceCents();
        LicenseType licenseType = LicenseType.valueOf(request.licenseType());

        // Exclusive inventory lock
        if (licenseType.requiresInventoryLock()) {
            int updated = listingMapper.reserveExclusive(
                    listing.getId(), null, LocalDateTime.now().plusMinutes(30));
            if (updated != 1) {
                throw new BizException(ErrorCode.EXCLUSIVE_LICENSE_RESERVED);
            }
        }

        // Create order
        TradeOrder order = new TradeOrder();
        order.setOrderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setBuyerUserId(ctx.userId());
        order.setBuyerWorkspaceId(ctx.workspaceId());
        order.setBuyerWorkspaceType(ctx.workspaceType());
        order.setSellerUserId(listing.getSellerUserId());
        order.setSellerWorkspaceId(listing.getWorkspaceId());
        order.setTotalAmountCents(amountCents);
        order.setPlatformFeeCents(amountCents > 0 ? amountCents / 10 : 0); // 10% platform fee
        order.setSellerIncomeCents(amountCents > 0 ? amountCents - order.getPlatformFeeCents() : 0);
        order.setCreateIdempotencyKey(request.idempotencyKey());
        order.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        orderMapper.insert(order);

        // Create order item (snapshot)
        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(order.getId());
        item.setListingId(listing.getId());
        item.setScriptId(listing.getScriptId());
        item.setScriptVersionId(listing.getScriptVersionId());
        item.setLicenseType(request.licenseType());
        item.setPriceCents(amountCents);
        item.setTitleSnapshot(listing.getTitle());
        item.setAuthorSnapshot(listing.getAuthorDisplayName());
        item.setTagsSnapshot(listing.getTagsJson());
        item.setAgreementText(option.getAgreementText());
        item.setAgreementVersion(option.getAgreementVersion());
        item.setAgreementHash(option.getAgreementHash());
        item.setHistoricalNormalCount(listing.getHistoricalNormalCount());
        orderItemMapper.insert(item);

        // For free orders, auto-deliver immediately
        if (amountCents == 0) {
            createAndFulfillFree(order, item);
        }

        log.info("Order created: orderNo={}, amount={} cents, license={}",
                order.getOrderNo(), amountCents, request.licenseType());
        return toView(order);
    }

    /**
     * Auto-fulfill a free (zero-price) order in the same transaction.
     */
    private void createAndFulfillFree(TradeOrder order, TradeOrderItem item) {
        order.setStatus(OrderStatus.PAID_PENDING_DELIVERY.name());
        order.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(order);
        deliveryService.deliver(order, item);
        order.setStatus(OrderStatus.FULFILLED.name());
        order.setFulfilledAt(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * Get order by order number, scoped to buyer workspace.
     */
    public OrderView getByOrderNo(WorkspaceContext ctx, String orderNo) {
        TradeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo)
                .eq(TradeOrder::getBuyerWorkspaceId, ctx.workspaceId()));
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toView(order);
    }

    /**
     * List buyer's orders scoped to workspace.
     */
    public List<OrderView> listByBuyer(WorkspaceContext ctx) {
        List<TradeOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getBuyerWorkspaceId, ctx.workspaceId())
                        .orderByDesc(TradeOrder::getCreatedAt));
        return orders.stream().map(this::toView).toList();
    }

    /**
     * Pay for a pending order. For zero-price orders this is a no-op (already fulfilled).
     * For paid orders, calls 3001 wallet to execute the purchase transfer.
     */
    @Transactional
    public OrderView pay(WorkspaceContext ctx, String orderNo) {
        TradeOrder order = requireBuyerOwnership(ctx, orderNo);

        if (!order.getStatus().equals(OrderStatus.PENDING_PAYMENT.name())
                && !order.getStatus().equals(OrderStatus.PAYMENT_FAILED.name())) {
            throw new BizException(ErrorCode.ORDER_STATE_CONFLICT, "当前订单状态不允许支付");
        }

        // Free orders are already fulfilled at creation time
        if (order.getTotalAmountCents() == 0) {
            return toView(order);
        }

        // Transition to PAYING
        order.setStatus(OrderStatus.PAYING.name());
        orderMapper.updateById(order);

        try {
            // Call 3001 wallet
            String idempotencyKey = "trade:purchase:" + orderNo;
            WalletClient.PurchaseRequest walletReq = new WalletClient.PurchaseRequest(
                    orderNo,
                    "PERSONAL".equals(order.getBuyerWorkspaceType()) ? "USER" : "WORKSPACE",
                    order.getBuyerWorkspaceId(),
                    "USER",
                    String.valueOf(order.getSellerUserId()),
                    order.getTotalAmountCents(),
                    order.getPlatformFeeCents(),
                    order.getCurrency());

            WalletClient.PurchaseResult result = walletClient.purchase(walletReq, idempotencyKey);

            // Payment succeeded
            order.setWalletTransferNo(result.transferNo());
            order.setWalletStatus(result.status());
            order.setStatus(OrderStatus.PAID_PENDING_DELIVERY.name());
            order.setPaidAt(LocalDateTime.now());
            orderMapper.updateById(order);

            // Consume purchase budget (if this was an enterprise purchase)
            consumeEnterpriseBudget(orderNo, result.transferNo());

            // Deliver
            var items = orderItemMapper.selectList(new LambdaQueryWrapper<TradeOrderItem>()
                    .eq(TradeOrderItem::getOrderId, order.getId()));
            if (!items.isEmpty()) {
                deliveryService.deliver(order, items.get(0));
            }
            order.setStatus(OrderStatus.FULFILLED.name());
            order.setFulfilledAt(LocalDateTime.now());
            orderMapper.updateById(order);

            log.info("Paid order fulfilled: orderNo={}, transferNo={}", orderNo, result.transferNo());
            return toView(order);

        } catch (Exception e) {
            log.error("Payment failed for order {}: {}", orderNo, e.getMessage());
            order.setStatus(OrderStatus.PAYMENT_FAILED.name());
            order.setFailureReason(e.getMessage());
            orderMapper.updateById(order);
            throw new BizException(ErrorCode.PAY_FAILED, "支付失败: " + e.getMessage());
        }
    }

    /**
     * Cancel a pending payment order.
     */
    @Transactional
    public void cancel(WorkspaceContext ctx, String orderNo) {
        TradeOrder order = requireBuyerOwnership(ctx, orderNo);
        if (!order.getStatus().equals(OrderStatus.PENDING_PAYMENT.name())) {
            throw new BizException(ErrorCode.ORDER_STATE_CONFLICT, "仅待支付订单可以取消");
        }
        order.setStatus(OrderStatus.CANCELLED.name());
        orderMapper.updateById(order);
        log.info("Order cancelled: {}", orderNo);
    }

    // -- helpers --

    private TradeOrder requireBuyerOwnership(WorkspaceContext ctx, String orderNo) {
        TradeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!order.getBuyerWorkspaceId().equals(ctx.workspaceId())) {
            throw new BizException(ErrorCode.ASSET_PERMISSION_DENIED);
        }
        return order;
    }

    private void consumeEnterpriseBudget(String orderNo, String transferNo) {
        try {
            var pr = purchaseRequestMapper.selectOne(new LambdaQueryWrapper<PurchaseRequest>()
                    .eq(PurchaseRequest::getOrderNo, orderNo));
            if (pr != null && pr.getBudgetReservationEntryId() != null) {
                budgetService.consume(
                        null, // ctx not needed for consume (source-based lookup)
                        pr.getBudgetReservationEntryId(),
                        pr.getAmountCents(),
                        transferNo,
                        "purchase:" + pr.getBudgetReservationEntryId() + ":consume:" + transferNo);
                log.info("Budget consumed for purchase {}: {} cents", pr.getId(), pr.getAmountCents());
            }
        } catch (Exception e) {
            log.warn("Budget consume skipped for order {}: {}", orderNo, e.getMessage());
        }
    }

    private OrderView toView(TradeOrder order) {
        return new OrderView(
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmountCents(),
                order.getCurrency(),
                order.getPlatformFeeCents(),
                order.getSellerIncomeCents(),
                null, // licenseType from item
                null, // titleSnapshot from item
                order.getBuyerWorkspaceId(),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getFulfilledAt(),
                order.getFailureReason());
    }
}
