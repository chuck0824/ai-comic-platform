package com.aicp.module.trade.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradeEnumsTest {

    @Test
    void exposesApprovedLicenseTypes() {
        assertThat(TradeEnums.LicenseType.values())
                .extracting(Enum::name)
                .containsExactly("FREE", "NORMAL", "EXCLUSIVE", "BUYOUT");
    }

    @Test
    void exclusiveLicenseTypesAreCorrectlyIdentified() {
        assertThat(TradeEnums.LicenseType.EXCLUSIVE.isExclusive()).isTrue();
        assertThat(TradeEnums.LicenseType.BUYOUT.isExclusive()).isTrue();
        assertThat(TradeEnums.LicenseType.NORMAL.isExclusive()).isFalse();
        assertThat(TradeEnums.LicenseType.FREE.isExclusive()).isFalse();
    }

    @Test
    void orderStatusTransitionsMatchDesign() {
        // Happy path
        assertThat(TradeEnums.OrderStatus.PENDING_PAYMENT.canTransitionTo(
                TradeEnums.OrderStatus.PAYING)).isTrue();
        assertThat(TradeEnums.OrderStatus.PAYING.canTransitionTo(
                TradeEnums.OrderStatus.PAID_PENDING_DELIVERY)).isTrue();
        assertThat(TradeEnums.OrderStatus.PAID_PENDING_DELIVERY.canTransitionTo(
                TradeEnums.OrderStatus.FULFILLED)).isTrue();

        // Payment failure
        assertThat(TradeEnums.OrderStatus.PAYING.canTransitionTo(
                TradeEnums.OrderStatus.PAYMENT_FAILED)).isTrue();
        assertThat(TradeEnums.OrderStatus.PAYING.canTransitionTo(
                TradeEnums.OrderStatus.PAYMENT_UNKNOWN)).isTrue();

        // PAYMENT_UNKNOWN resolution
        assertThat(TradeEnums.OrderStatus.PAYMENT_UNKNOWN.canTransitionTo(
                TradeEnums.OrderStatus.PAID_PENDING_DELIVERY)).isTrue();
        assertThat(TradeEnums.OrderStatus.PAYMENT_UNKNOWN.canTransitionTo(
                TradeEnums.OrderStatus.PAYMENT_FAILED)).isTrue();

        // Compensating
        assertThat(TradeEnums.OrderStatus.PAID_PENDING_DELIVERY.canTransitionTo(
                TradeEnums.OrderStatus.COMPENSATING)).isTrue();
        assertThat(TradeEnums.OrderStatus.COMPENSATING.canTransitionTo(
                TradeEnums.OrderStatus.FULFILLED)).isTrue();
        assertThat(TradeEnums.OrderStatus.COMPENSATING.canTransitionTo(
                TradeEnums.OrderStatus.REFUNDED)).isTrue();

        // Refund flow
        assertThat(TradeEnums.OrderStatus.FULFILLED.canTransitionTo(
                TradeEnums.OrderStatus.REFUND_REQUESTED)).isTrue();
        assertThat(TradeEnums.OrderStatus.REFUND_REQUESTED.canTransitionTo(
                TradeEnums.OrderStatus.REFUND_PROCESSING)).isTrue();
        assertThat(TradeEnums.OrderStatus.REFUND_PROCESSING.canTransitionTo(
                TradeEnums.OrderStatus.REFUNDED)).isTrue();
    }

    @Test
    void terminalStatesCannotTransition() {
        assertThat(TradeEnums.OrderStatus.FULFILLED.canTransitionTo(
                TradeEnums.OrderStatus.PENDING_PAYMENT)).isFalse();
        assertThat(TradeEnums.OrderStatus.REFUNDED.canTransitionTo(
                TradeEnums.OrderStatus.PENDING_PAYMENT)).isFalse();
        assertThat(TradeEnums.OrderStatus.CANCELLED.canTransitionTo(
                TradeEnums.OrderStatus.PENDING_PAYMENT)).isFalse();
        assertThat(TradeEnums.OrderStatus.EXPIRED.canTransitionTo(
                TradeEnums.OrderStatus.PENDING_PAYMENT)).isFalse();
    }

    @Test
    void isTerminalCorrectlyIdentifiesFinalStates() {
        assertThat(TradeEnums.OrderStatus.FULFILLED.isTerminal()).isTrue();
        assertThat(TradeEnums.OrderStatus.REFUNDED.isTerminal()).isTrue();
        assertThat(TradeEnums.OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(TradeEnums.OrderStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(TradeEnums.OrderStatus.REJECTED.isTerminal()).isTrue();

        assertThat(TradeEnums.OrderStatus.PENDING_PAYMENT.isTerminal()).isFalse();
        assertThat(TradeEnums.OrderStatus.PAYING.isTerminal()).isFalse();
    }

    @Test
    void isPaymentActiveIdentifiesChargingStates() {
        assertThat(TradeEnums.OrderStatus.PAYING.isPaymentActive()).isTrue();
        assertThat(TradeEnums.OrderStatus.PAYMENT_UNKNOWN.isPaymentActive()).isTrue();

        assertThat(TradeEnums.OrderStatus.PENDING_PAYMENT.isPaymentActive()).isFalse();
        assertThat(TradeEnums.OrderStatus.FULFILLED.isPaymentActive()).isFalse();
    }

    @Test
    void listingStatusValuesExist() {
        assertThat(TradeEnums.ListingStatus.values())
                .extracting(Enum::name)
                .contains("DRAFT", "UNDER_REVIEW", "REJECTED", "LISTED",
                        "EXCLUSIVE_RESERVED", "EXCLUSIVE_SOLD", "UNLISTED");
    }

    @Test
    void allEnumsExposeExpectedStates() {
        assertThat(TradeEnums.EntitlementStatus.values())
                .extracting(Enum::name)
                .containsExactly("ACTIVE", "REFUND_LOCKED", "REVOKED");

        assertThat(TradeEnums.PurchaseRequestStatus.values())
                .extracting(Enum::name)
                .containsExactly("PENDING_APPROVAL", "APPROVED", "REJECTED", "CANCELLED");

        assertThat(TradeEnums.OutboxStatus.values())
                .extracting(Enum::name)
                .containsExactly("PENDING", "PROCESSING", "SUCCEEDED", "FAILED");
    }
}
