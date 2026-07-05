package com.aicp.module.sop.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class SopRequests {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RunCheckRequest(
            Long contentUnitId,
            Long canvasProjectId,
            @NotNull String triggerType) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateWorkOrderRequest(
            @NotNull Long resultId,
            String responsibleRole,
            Long assigneeId) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TransitionWorkOrderRequest(
            @NotNull String toStatus,
            String note) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReviewWorkOrderRequest(
            boolean approved,
            @NotBlank String note) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record EvaluateGateRequest(
            @NotNull String gateType,
            Long contentUnitId,
            Long canvasProjectId,
            @NotBlank String idempotencyKey) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExecuteFixRequest(
            @NotBlank String fixAction,
            String parametersJson) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CompatibilityReadinessRequest(
            @NotNull Long projectId,
            Long contentUnitId) {
    }

    private SopRequests() {}
}
