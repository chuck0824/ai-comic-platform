package com.aicp.module.trade.domain;

import java.util.Map;
import java.util.Set;

/**
 * All listing, license, order, entitlement, refund, and outbox enums for the
 * script trading market.
 *
 * <p>Money is always {@code long} cents. Currency is {@code CNY}.
 */
public final class TradeEnums {

    private TradeEnums() {
    }

    /** Listing lifecycle from seller draft through review to final sale or delist. */
    public enum ListingStatus {
        DRAFT,
        UNDER_REVIEW,
        REJECTED,
        LISTED,
        EXCLUSIVE_RESERVED,
        EXCLUSIVE_SOLD,
        UNLISTED
    }

    /** The four license types a listing may offer. */
    public enum LicenseType {
        FREE,
        NORMAL,
        EXCLUSIVE,
        BUYOUT;

        public boolean isExclusive() {
            return this == EXCLUSIVE || this == BUYOUT;
        }

        public boolean requiresInventoryLock() {
            return isExclusive();
        }
    }

    /**
     * Order state machine.
     *
     * <pre>
     * PENDING_APPROVAL → PENDING_PAYMENT → PAYING → PAID_PENDING_DELIVERY → FULFILLED
     *         │                 │             │                │
     *         └→ REJECTED       ├→ CANCELLED  ├→ PAYMENT_FAILED├→ COMPENSATING → REFUNDED
     *                           └→ EXPIRED     └→ PAYMENT_UNKNOWN              └→ FULFILLED
     *
     * FULFILLED → REFUND_REQUESTED → REFUND_PROCESSING → REFUNDED
     *                             └────────────→ REFUND_REJECTED → FULFILLED
     * </pre>
     */
    public enum OrderStatus {
        PENDING_APPROVAL,
        REJECTED,
        PENDING_PAYMENT,
        PAYING,
        PAYMENT_FAILED,
        PAYMENT_UNKNOWN,
        PAID_PENDING_DELIVERY,
        COMPENSATING,
        FULFILLED,
        REFUND_REQUESTED,
        REFUND_PROCESSING,
        REFUND_REJECTED,
        REFUNDED,
        CANCELLED,
        EXPIRED;

        private static final Map<OrderStatus, Set<OrderStatus>> NEXT = Map.ofEntries(
                Map.entry(PENDING_APPROVAL, Set.of(REJECTED, PENDING_PAYMENT, CANCELLED)),
                Map.entry(PENDING_PAYMENT, Set.of(PAYING, CANCELLED, EXPIRED)),
                Map.entry(PAYING, Set.of(PAYMENT_FAILED, PAYMENT_UNKNOWN, PAID_PENDING_DELIVERY)),
                Map.entry(PAYMENT_UNKNOWN, Set.of(PAYMENT_FAILED, PAID_PENDING_DELIVERY)),
                Map.entry(PAID_PENDING_DELIVERY, Set.of(COMPENSATING, FULFILLED, REFUNDED)),
                Map.entry(COMPENSATING, Set.of(FULFILLED, REFUNDED)),
                Map.entry(FULFILLED, Set.of(REFUND_REQUESTED)),
                Map.entry(REFUND_REQUESTED, Set.of(REFUND_PROCESSING, REFUND_REJECTED)),
                Map.entry(REFUND_PROCESSING, Set.of(REFUNDED)),
                Map.entry(REFUND_REJECTED, Set.of(FULFILLED))
        );

        public boolean canTransitionTo(OrderStatus next) {
            return NEXT.getOrDefault(this, Set.of()).contains(next);
        }

        /** Orders in these states must not be charged again. */
        public boolean isTerminal() {
            return this == FULFILLED || this == REFUNDED
                    || this == CANCELLED || this == EXPIRED || this == REJECTED;
        }

        /** Orders awaiting or undergoing payment. */
        public boolean isPaymentActive() {
            return this == PAYING || this == PAYMENT_UNKNOWN;
        }
    }

    /** Entitlement lifecycle for a granted license. */
    public enum EntitlementStatus {
        ACTIVE,
        REFUND_LOCKED,
        REVOKED
    }

    /** Enterprise purchase request workflow. */
    public enum PurchaseRequestStatus {
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    /** Refund request states. */
    public enum RefundStatus {
        REQUESTED,
        PROCESSING,
        APPROVED,
        REJECTED,
        REFUNDED
    }

    /** Outbox event processing states. */
    public enum OutboxStatus {
        PENDING,
        PROCESSING,
        SUCCEEDED,
        FAILED
    }
}
