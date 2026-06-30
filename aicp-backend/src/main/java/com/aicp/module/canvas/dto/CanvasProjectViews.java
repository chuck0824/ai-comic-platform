package com.aicp.module.canvas.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CanvasProjectViews {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CanvasProjectSummary(
            Long id,
            String uuid,
            String name,
            String status,
            String purpose,
            Long contentProjectId,
            String contentProjectName,
            String productionUnitType,
            Long productionUnitId,
            String productionUnitName,
            Long ownerId,
            String ownerName,
            String thumbnailUrl,
            int nodeCount,
            int taskCount,
            int errorTaskCount,
            boolean hasUpstreamChanges,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime archivedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CanvasProjectDetail(
            Long id,
            String uuid,
            String name,
            String status,
            String purpose,
            Long contentProjectId,
            String contentProjectName,
            String productionUnitType,
            Long productionUnitId,
            String productionUnitName,
            Long sourceContentVersionId,
            Long sourceStoryboardVersionId,
            ProductionSnapshot productionSnapshot,
            String thumbnailUrl,
            String workspaceId,
            Long ownerId,
            Integer revision,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime archivedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ProductionSnapshot(
            Long contentVersionId,
            String contentVersionHash,
            String contentTitle,
            String contentSummary,
            Long storyboardVersionId,
            Integer storyboardRevision,
            int shotCount,
            boolean storyboardLocked,
            String platformRuleVersion,
            String pluginPackageVersion,
            String aspectRatio,
            String resolution,
            int fps,
            Map<String, Object> metadata) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SourceDiffResult(
            boolean hasChanges,
            List<DimensionDiff> dimensions) {

        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public record DimensionDiff(
                String dimension,
                String label,
                String severity,
                List<FieldChange> changes) {}

        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public record FieldChange(
                String field,
                String label,
                String snapshotValue,
                String upstreamValue,
                String changeType) {}
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ProductionAdmissionResult(
            boolean passed,
            List<MissingRequirement> missingRequirements) {

        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public record MissingRequirement(
                String code,
                String label,
                String remediationLink) {}
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ContinueWorkingItem(
            String itemType,
            Long id,
            String uuid,
            String name,
            String stage,
            String status,
            Long canvasProjectId,
            String canvasProjectUuid,
            boolean hasErrors,
            LocalDateTime updatedAt) {}
}
