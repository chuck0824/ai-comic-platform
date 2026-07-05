package com.aicp.module.trade.wallet;

import java.util.List;

/**
 * Port to the 3001 wallet service.
 * All money values are in cents (long).
 */
public interface WalletClient {

    record BalanceResult(long availableCents, long frozenCents, String currency) {}

    record PrecheckResult(boolean allowed) {}

    record PurchaseResult(String transferNo, String status, long buyerBalanceAfter) {}

    record TransferRecord(String transferNo, String businessOrderNo, String status,
                          long amountCents, long reversedCents) {}

    record LedgerEntry(String transferNo, String entryType, long amountCents,
                       long balanceAfter, long createdAt) {}

    /** Query available and frozen balance for an owner. */
    BalanceResult getBalance(String ownerType, String ownerId);

    /** Validate wallet exists, has sufficient balance, and user has permission. */
    PrecheckResult precheck(String ownerType, String ownerId, long amountCents, String permission);

    /** Execute atomic purchase transfer. Returns result or throws on failure. */
    PurchaseResult purchase(PurchaseRequest request, String idempotencyKey);

    /** Look up a transfer by business order number. */
    TransferRecord findByBusinessOrder(String orderNo);

    /** Release frozen seller funds. */
    TransferRecord release(String transferNo, String idempotencyKey);

    /** Reverse (refund) a transfer partially or fully. */
    TransferRecord reverse(String transferNo, long amountCents, String idempotencyKey);

    /** Query ledger entries for an owner. */
    List<LedgerEntry> getLedger(String ownerType, String ownerId);

    /** Purchase transfer request DTO. */
    record PurchaseRequest(
            String businessOrderNo,
            String buyerType,
            String buyerId,
            String sellerType,
            String sellerId,
            long amountCents,
            long platformFeeCents,
            String currency) {}
}
