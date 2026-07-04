package com.aicp.module.enterprise.service;

import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.enterprise.entity.EnterprisePurchaseBudget;
import com.aicp.module.enterprise.entity.EnterprisePurchaseBudgetEntry;
import com.aicp.module.enterprise.mapper.EnterprisePurchaseBudgetEntryMapper;
import com.aicp.module.enterprise.mapper.EnterprisePurchaseBudgetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseBudgetService {

    private final EnterprisePurchaseBudgetMapper budgetMapper;
    private final EnterprisePurchaseBudgetEntryMapper entryMapper;

    // ─── Public contract ─────────────────────────────────────────────────

    public record BudgetSubject(String type, String id) {}
    public record BudgetSnapshot(long amountCents, long reservedCents,
                                  long consumedCents, long availableCents,
                                  int rowVersion) {}

    @Transactional
    public BudgetSnapshot reserve(WorkspaceContext ctx, BudgetSubject subject,
                                   YearMonth month, long amountCents,
                                   String sourceId, String idempotencyKey) {
        return apply(ctx, subject, month, amountCents, "RESERVE", sourceId, null, idempotencyKey);
    }

    @Transactional
    public BudgetSnapshot release(WorkspaceContext ctx, String sourceId,
                                   long amountCents, String idempotencyKey) {
        var entry = findBySource("RESERVE", sourceId);
        var budget = findBudget(entry.getBudgetId());
        return apply(ctx, new BudgetSubject(budget.getSubjectType(), budget.getSubjectId()),
                YearMonth.parse(budget.getPeriodMonth()), -amountCents, "RELEASE",
                sourceId, null, idempotencyKey);
    }

    @Transactional
    public BudgetSnapshot consume(WorkspaceContext ctx, String sourceId,
                                   long amountCents, String walletTransferNo,
                                   String idempotencyKey) {
        var entry = findBySource("RESERVE", sourceId);
        var budget = findBudget(entry.getBudgetId());
        // Move from reserved → consumed
        applyConsume(budget, amountCents, walletTransferNo, sourceId, idempotencyKey);
        return snapshot(budget);
    }

    @Transactional
    public BudgetSnapshot reverse(WorkspaceContext ctx, String sourceId,
                                   long amountCents, String walletReversalNo,
                                   String idempotencyKey) {
        var entry = findBySource("CONSUME", sourceId);
        var budget = findBudget(entry.getBudgetId());
        if (budget.getConsumedCents() < amountCents) {
            throw new BudgetExceededException("insufficient consumed budget to reverse");
        }
        var updated = budgetMapper.update(null,
                new LambdaUpdateWrapper<EnterprisePurchaseBudget>()
                        .eq(EnterprisePurchaseBudget::getId, budget.getId())
                        .eq(EnterprisePurchaseBudget::getRowVersion, budget.getRowVersion())
                        .setSql("consumed_cents = consumed_cents - " + amountCents)
                        .setSql("row_version = row_version + 1"));
        if (updated == 0) throw new BudgetConcurrencyException("reverse conflict, retry");
        insertEntry(budget.getId(), budget.getWorkspaceId(), "REVERSE", -amountCents,
                "refund", sourceId, walletReversalNo, idempotencyKey);
        return snapshot(budgetMapper.selectById(budget.getId()));
    }

    // ─── Core apply ──────────────────────────────────────────────────────

    private BudgetSnapshot apply(WorkspaceContext ctx, BudgetSubject subject,
                                  YearMonth month, long amountCents, String entryType,
                                  String sourceId, String walletTransferNo,
                                  String idempotencyKey) {
        String period = month.toString(); // YYYY-MM
        EnterprisePurchaseBudget budget = findOrCreateBudget(ctx.workspaceId(), subject, period);

        if ("RESERVE".equals(entryType)) {
            // Check single-limit
            if (budget.getSingleLimitCents() > 0 && amountCents > budget.getSingleLimitCents()) {
                throw new BudgetExceededException("amount exceeds single-limit");
            }
            // Check monthly limit: reserved + consumed + new <= total
            if (budget.getAmountCents() > 0 &&
                    budget.getReservedCents() + budget.getConsumedCents() + amountCents > budget.getAmountCents()) {
                throw new BudgetExceededException("monthly budget exceeded");
            }
            int updated = budgetMapper.update(null,
                    new LambdaUpdateWrapper<EnterprisePurchaseBudget>()
                            .eq(EnterprisePurchaseBudget::getId, budget.getId())
                            .eq(EnterprisePurchaseBudget::getRowVersion, budget.getRowVersion())
                            .setSql("reserved_cents = reserved_cents + " + amountCents)
                            .setSql("row_version = row_version + 1"));
            if (updated == 0) throw new BudgetConcurrencyException("reserve conflict, retry");
        } else if ("RELEASE".equals(entryType)) {
            long releaseAmt = Math.abs(amountCents);
            int updated = budgetMapper.update(null,
                    new LambdaUpdateWrapper<EnterprisePurchaseBudget>()
                            .eq(EnterprisePurchaseBudget::getId, budget.getId())
                            .eq(EnterprisePurchaseBudget::getRowVersion, budget.getRowVersion())
                            .ge(EnterprisePurchaseBudget::getReservedCents, releaseAmt)
                            .setSql("reserved_cents = reserved_cents - " + releaseAmt)
                            .setSql("row_version = row_version + 1"));
            if (updated == 0) throw new BudgetConcurrencyException("release conflict, retry");
        }

        insertEntry(budget.getId(), ctx.workspaceId(), entryType, amountCents,
                "purchase_request", sourceId, walletTransferNo, idempotencyKey);
        return snapshot(budgetMapper.selectById(budget.getId()));
    }

    private void applyConsume(EnterprisePurchaseBudget budget, long amountCents,
                               String walletTransferNo, String sourceId,
                               String idempotencyKey) {
        int updated = budgetMapper.update(null,
                new LambdaUpdateWrapper<EnterprisePurchaseBudget>()
                        .eq(EnterprisePurchaseBudget::getId, budget.getId())
                        .eq(EnterprisePurchaseBudget::getRowVersion, budget.getRowVersion())
                        .ge(EnterprisePurchaseBudget::getReservedCents, amountCents)
                        .setSql("reserved_cents = reserved_cents - " + amountCents)
                        .setSql("consumed_cents = consumed_cents + " + amountCents)
                        .setSql("row_version = row_version + 1"));
        if (updated == 0) throw new BudgetConcurrencyException("consume conflict, retry");
        insertEntry(budget.getId(), budget.getWorkspaceId(), "CONSUME", amountCents,
                "order", sourceId, walletTransferNo, idempotencyKey);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private EnterprisePurchaseBudget findOrCreateBudget(String workspaceId,
                                                         BudgetSubject subject, String period) {
        var existing = budgetMapper.selectOne(new LambdaQueryWrapper<EnterprisePurchaseBudget>()
                .eq(EnterprisePurchaseBudget::getWorkspaceId, workspaceId)
                .eq(EnterprisePurchaseBudget::getSubjectType, subject.type())
                .eq(EnterprisePurchaseBudget::getSubjectId, subject.id())
                .eq(EnterprisePurchaseBudget::getPeriodMonth, period));
        if (existing != null) return existing;

        var budget = new EnterprisePurchaseBudget();
        budget.setWorkspaceId(workspaceId);
        budget.setSubjectType(subject.type());
        budget.setSubjectId(subject.id());
        budget.setPeriodMonth(period);
        budget.setAmountCents(0L);
        budget.setSingleLimitCents(0L);
        budget.setReservedCents(0L);
        budget.setConsumedCents(0L);
        budget.setRowVersion(0);
        budget.setCreatedAt(LocalDateTime.now());
        budget.setUpdatedAt(LocalDateTime.now());
        budgetMapper.insert(budget);
        return budget;
    }

    private EnterprisePurchaseBudget findBudget(Long id) {
        return budgetMapper.selectById(id);
    }

    private EnterprisePurchaseBudgetEntry findBySource(String entryType, String sourceId) {
        var entry = entryMapper.selectOne(new LambdaQueryWrapper<EnterprisePurchaseBudgetEntry>()
                .eq(EnterprisePurchaseBudgetEntry::getEntryType, entryType)
                .eq(EnterprisePurchaseBudgetEntry::getSourceId, sourceId));
        if (entry == null) throw new BudgetNotFoundException("no " + entryType + " entry for source " + sourceId);
        return entry;
    }

    private void insertEntry(Long budgetId, String workspaceId, String entryType,
                              long amountCents, String sourceType, String sourceId,
                              String walletTransferNo, String idempotencyKey) {
        // Idempotency: skip if key already exists
        var existing = entryMapper.selectOne(new LambdaQueryWrapper<EnterprisePurchaseBudgetEntry>()
                .eq(EnterprisePurchaseBudgetEntry::getIdempotencyKey, idempotencyKey));
        if (existing != null) return;

        var entry = new EnterprisePurchaseBudgetEntry();
        entry.setBudgetId(budgetId);
        entry.setWorkspaceId(workspaceId);
        entry.setEntryType(entryType);
        entry.setAmountCents(amountCents);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setWalletTransferNo(walletTransferNo);
        entry.setIdempotencyKey(idempotencyKey);
        entry.setCreatedAt(LocalDateTime.now());
        entryMapper.insert(entry);
    }

    private BudgetSnapshot snapshot(EnterprisePurchaseBudget b) {
        long available = b.getAmountCents() > 0
                ? Math.max(0, b.getAmountCents() - b.getReservedCents() - b.getConsumedCents())
                : Long.MAX_VALUE;
        return new BudgetSnapshot(b.getAmountCents(), b.getReservedCents(),
                b.getConsumedCents(), available, b.getRowVersion());
    }

    // ─── Exceptions ──────────────────────────────────────────────────────

    public static class BudgetExceededException extends RuntimeException {
        public BudgetExceededException(String msg) { super(msg); }
    }
    public static class BudgetConcurrencyException extends RuntimeException {
        public BudgetConcurrencyException(String msg) { super(msg); }
    }
    public static class BudgetNotFoundException extends RuntimeException {
        public BudgetNotFoundException(String msg) { super(msg); }
    }
}
