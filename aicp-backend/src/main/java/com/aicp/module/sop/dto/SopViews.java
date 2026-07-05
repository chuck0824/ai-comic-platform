package com.aicp.module.sop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.List;

public final class SopViews {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProjectRiskSummary(
            Long projectId,
            String projectName,
            String overallStatus,
            Integer blockedCount,
            Integer warningCount,
            LocalDateTime lastCheckAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SopSummaryView(
            Long projectId,
            String overallStatus,
            int passedCount,
            int warningCount,
            int blockedCount,
            int notReadyCount,
            int errorCount,
            Long lastRunId,
            LocalDateTime lastRunAt,
            boolean stale) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckRunSummary(
            Long id,
            String triggerType,
            String ruleSetVersion,
            String overallStatus,
            String status,
            int passedCount,
            int warningCount,
            int blockedCount,
            int notReadyCount,
            int errorCount,
            LocalDateTime createdAt,
            LocalDateTime completedAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckReportView(
            Long runId,
            Long projectId,
            String overallStatus,
            String status,
            String ruleSetVersion,
            int passedCount,
            int warningCount,
            int blockedCount,
            int notReadyCount,
            int errorCount,
            List<CheckResultView> results,
            LocalDateTime createdAt,
            LocalDateTime completedAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckResultView(
            Long id,
            String ruleCode,
            String result,
            String severity,
            boolean critical,
            String targetType,
            String targetId,
            String issueFingerprint,
            String evidenceJson,
            String suggestion,
            String fixPolicy) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WorkOrderView(
            Long id,
            Long projectId,
            Long runId,
            Long resultId,
            String ruleCode,
            String issueFingerprint,
            String status,
            String severity,
            String responsibleRole,
            Long assigneeId,
            String resolutionNote,
            LocalDateTime deadline,
            LocalDateTime createdAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GateDecisionView(
            Long decisionId,
            Long projectId,
            Long runId,
            String gateType,
            boolean allowed,
            int blockerCount,
            String idempotencyKey,
            LocalDateTime createdAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WorkOrderEventView(
            Long id,
            Long workOrderId,
            String fromStatus,
            String toStatus,
            Long operatorId,
            String note,
            LocalDateTime createdAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunCheckResponse(
            Long runId,
            String overallStatus,
            int blockedCount,
            int warningCount) {
    }

    private SopViews() {}
}
