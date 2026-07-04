package com.aicp.module.enterprise.dto;

import java.time.LocalDateTime;

public final class ApprovalViews {
    private ApprovalViews() {}

    public record ApprovalItemView(
            Long id,
            String sourceType,
            String sourceId,
            Integer sourceVersion,
            String workspaceId,
            String departmentId,
            Long requesterUserId,
            String summary,
            Long amountCents,
            String currency,
            String status,
            LocalDateTime submittedAt,
            LocalDateTime decidedAt,
            LocalDateTime lastEventAt
    ) {}

    public record ApprovalDecisionRequest(
            boolean approved,
            String reason,
            int expectedVersion,
            String idempotencyKey
    ) {}

    public record BudgetView(
            Long id,
            String workspaceId,
            String subjectType,
            String subjectId,
            String periodMonth,
            Long amountCents,
            Long singleLimitCents,
            Long reservedCents,
            Long consumedCents,
            Long availableCents,
            Integer rowVersion
    ) {}

    public record BudgetEntryView(
            Long id,
            Long budgetId,
            String entryType,
            Long amountCents,
            String sourceType,
            String sourceId,
            String walletTransferNo,
            LocalDateTime createdAt
    ) {}
}
