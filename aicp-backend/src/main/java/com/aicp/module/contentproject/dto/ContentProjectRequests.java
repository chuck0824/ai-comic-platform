package com.aicp.module.contentproject.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public interface ContentProjectRequests {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CreateProjectRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank String creationMode,
            @NotBlank String sourceMode,
            @Size(max = 20000) String startContent,
            @NotBlank @Size(max = 50) String contentGoal,
            @NotBlank String tenantType,
            Long tenantId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record UpdateProjectRequest(
            @Size(max = 200) String name,
            Integer revision) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ResumePositionRequest(
            String stageKey,
            String taskKey,
            Long contentUnitId,
            Integer revision) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SaveDraftRequest(
            Integer revision,
            String contentJson,
            String plainText) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CreateMemberRequest(
            @NotBlank Long userId,
            @NotBlank String role) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record UpdateMemberRequest(
            @NotBlank String role) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CreateVersionRequest(
            @NotBlank String status) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record GenerationJobRequest(
            @NotBlank String jobType,
            @NotBlank String targetType,
            Long targetId,
            java.util.Map<String, Long> selectedVersions,
            String strategy,
            String schemaVersion) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record AppendParameterRequest(
            java.util.Map<String, Object> payload,
            Integer revision) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record StoryboardIntentRequest(
            @NotBlank String intent,
            Long sourceVersionId) {}

    // ===== Warehouse / Lifecycle =====

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ProjectQuery(
            int page,
            int pageSize,
            String keyword,
            String creationMode,
            String sourceMode,
            String contentStatus,
            String productionStatus,
            String commercialStatus,
            String lifecycleStatus,
            String updatedFrom,
            String updatedTo,
            String sort) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record VersionActionRequest(
            Long versionId,
            Integer revision,
            @NotBlank String idempotencyKey,
            @Size(max = 2000) String comment) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ProjectActionRequest(
            Integer revision,
            @NotBlank String idempotencyKey,
            @Size(max = 2000) String comment) {}
}
