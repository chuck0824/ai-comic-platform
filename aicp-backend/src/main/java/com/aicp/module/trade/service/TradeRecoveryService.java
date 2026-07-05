package com.aicp.module.trade.service;

import com.aicp.module.trade.domain.TradeEnums.OrderStatus;
import com.aicp.module.trade.domain.TradeEnums.OutboxStatus;
import com.aicp.module.trade.entity.*;
import com.aicp.module.trade.mapper.*;
import com.aicp.module.trade.wallet.WalletClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Outbox dispatch, payment reconciliation, expiration, and settlement release. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeRecoveryService {

    private final TradeOrderMapper orderMapper;
    private final TradeOutboxEventMapper outboxMapper;
    private final ScriptListingMapper listingMapper;
    private final WalletClient walletClient;
    private final DeliveryService deliveryService;
    private final TradeOrderItemMapper orderItemMapper;

    /** Every 30s: dispatch due outbox events. */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void dispatchOutbox() {
        var events = outboxMapper.selectList(new LambdaQueryWrapper<TradeOutboxEvent>()
                .eq(TradeOutboxEvent::getStatus, OutboxStatus.PENDING.name())
                .le(TradeOutboxEvent::getNextRetryAt, LocalDateTime.now())
                .last("LIMIT 20"));

        for (var ev : events) {
            ev.setStatus(OutboxStatus.PROCESSING.name());
            outboxMapper.updateById(ev);
            try {
                processEvent(ev);
                ev.setStatus(OutboxStatus.SUCCEEDED.name());
            } catch (Exception e) {
                ev.setRetryCount(ev.getRetryCount() + 1);
                ev.setLastError(e.getMessage());
                if (ev.getRetryCount() >= ev.getMaxRetries()) {
                    ev.setStatus(OutboxStatus.FAILED.name());
                    log.error("Outbox event exhausted: id={}, type={}", ev.getId(), ev.getEventType());
                } else {
                    ev.setStatus(OutboxStatus.PENDING.name());
                    ev.setNextRetryAt(nextRetry(ev.getRetryCount()));
                }
            }
            outboxMapper.updateById(ev);
        }
    }

    /** Every minute: expire orders older than 30 min. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStaleOrders() {
        var orders = orderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .lt(TradeOrder::getExpiresAt, LocalDateTime.now()));
        for (var o : orders) {
            o.setStatus(OrderStatus.EXPIRED.name());
            orderMapper.updateById(o);
            // Release exclusive reservation
            listingMapper.releaseReservation(o.getOrderNo());
            log.info("Order expired: {}", o.getOrderNo());
        }
    }

    /** Daily: reconcile orders with 3001 wallet. */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(readOnly = true)
    public void dailyReconcile() {
        var paidOrders = orderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .in(TradeOrder::getStatus, OrderStatus.FULFILLED.name(), OrderStatus.PAID_PENDING_DELIVERY.name())
                .isNotNull(TradeOrder::getWalletTransferNo));
        for (var o : paidOrders) {
            try {
                var transfer = walletClient.findByBusinessOrder(o.getOrderNo());
                if (!o.getTotalAmountCents().equals(transfer.amountCents())) {
                    log.error("Reconciliation mismatch: order={}, local={}, wallet={}",
                            o.getOrderNo(), o.getTotalAmountCents(), transfer.amountCents());
                }
            } catch (Exception e) {
                log.error("Reconciliation failed for order={}: {}", o.getOrderNo(), e.getMessage());
            }
        }
    }

    /** Release seller funds for fulfilled orders older than 7 days. */
    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void releaseDueSettlements() {
        var ready = orderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getStatus, OrderStatus.FULFILLED.name())
                .isNotNull(TradeOrder::getWalletTransferNo)
                .lt(TradeOrder::getFulfilledAt, LocalDateTime.now().minusDays(7)));
        for (var o : ready) {
            try {
                walletClient.release(o.getWalletTransferNo(), "trade:release:" + o.getOrderNo());
                log.info("Settlement released: order={}", o.getOrderNo());
            } catch (Exception e) {
                log.error("Settlement release failed: order={}, {}", o.getOrderNo(), e.getMessage());
            }
        }
    }

    void processEvent(TradeOutboxEvent ev) {
        switch (ev.getEventType()) {
            case "QUERY_PAYMENT" -> {
                var transfer = walletClient.findByBusinessOrder(ev.getAggregateId());
                var order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getOrderNo, ev.getAggregateId()));
                if (order != null && "SUCCEEDED".equals(transfer.status())) {
                    order.setStatus(OrderStatus.PAID_PENDING_DELIVERY.name());
                    order.setWalletTransferNo(transfer.transferNo());
                    orderMapper.updateById(order);
                }
            }
            case "DELIVER_ORDER" -> {
                var order = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getOrderNo, ev.getAggregateId()));
                if (order != null) {
                    var items = orderItemMapper.selectList(new LambdaQueryWrapper<TradeOrderItem>()
                            .eq(TradeOrderItem::getOrderId, order.getId()));
                    if (!items.isEmpty()) deliveryService.deliver(order, items.get(0));
                }
            }
        }
    }

    private LocalDateTime nextRetry(int count) {
        long[] delays = {10, 30, 120, 600, 1800};
        long delay = count < delays.length ? delays[count] : 1800;
        return LocalDateTime.now().plusSeconds(delay);
    }
}
