package com.aicp.module.contentproject.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ContentProjectViews {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ProjectDetail(
            Long id,
            String uuid,
            String tenantType,
            Long tenantId,
            Long ownerUserId,
            String name,
            String creationMode,
            String sourceMode,
            String storyboardIntentStatus,
            String contentStatus,
            String productionStatus,
            String marketStatus,
            String lastStageKey,
            String lastTaskKey,
            Long lastContentUnitId,
            Long currentParameterVersionId,
            Long legacyScriptId,
            Integer revision,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<MemberView> members) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ProjectSummary(
            Long id,
            String uuid,
            String name,
            String creationMode,
            String sourceMode,
            String contentStatus,
            String productionStatus,
            String storyboardIntentStatus,
            String lastStageKey,
            Integer revision,
            LocalDateTime updatedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record MemberView(
            Long id,
            Long userId,
            String role,
            LocalDateTime createdAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record WorkflowView(
            String currentStageKey,
            String currentTaskKey,
            int progress,
            List<StageView> stages) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record StageView(
            String key,
            String label,
            String status,
            boolean required,
            List<String> missingConditions,
            String primaryAction,
            String route) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ParameterVersionView(
            Long id,
            Integer versionNo,
            Map<String, Object> payload,
            String contentHash,
            Long createdBy,
            LocalDateTime createdAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ContentUnitView(
            Long id,
            String stableKey,
            String unitType,
            Integer displayNo,
            String title,
            String status,
            Long currentVersionId,
            Integer revision,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record DraftView(
            Long id,
            Long contentUnitId,
            Integer revision,
            String contentJson,
            String plainText,
            LocalDateTime updatedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ContentVersionView(
            Long id,
            Integer versionNo,
            String status,
            String contentJson,
            String plainText,
            String source,
            String contentHash,
            Long createdBy,
            LocalDateTime createdAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record GenerationJobView(
            Long id,
            String uuid,
            String jobType,
            String targetType,
            Long targetId,
            String status,
            Integer estimatedCredits,
            Integer estimatedDurationSec,
            Integer pollAfterMs,
            Long createdBy,
            LocalDateTime createdAt,
            LocalDateTime finishedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ContextSnapshot(
            Map<String, Long> selectedVersions,
            Long bibleVersionId,
            Long projectGuideId,
            List<Long> characterGuideIds,
            Long unitGuideId,
            String resolvedGuideJson,
            String payload,
            String contentHash) {}

    // ===== Warehouse / Lifecycle Views =====

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record WarehouseProjectView(
            Long id,
            String uuid,
            String name,
            String creationMode,
            String sourceMode,
            String contentStatus,
            String productionStatus,
            String commercialStatus,
            String lifecycleStatus,
            String lastStageKey,
            Long adoptedVersionId,
            String primaryAction,
            String blockedReason,
            boolean migrationIssue,
            Integer revision,
            LocalDateTime updatedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ProjectTodoView(
            Long projectId,
            String projectName,
            String type,
            String label,
            String route,
            LocalDateTime updatedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ProjectHubView(
            ProjectDetail project,
            WarehouseProjectView summary,
            List<ContentVersionView> versions,
            Map<String, Long> relationCounts) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record WarehouseProjectListResult(
            List<WarehouseProjectView> items,
            int page,
            int pageSize,
            long total) {}
}
