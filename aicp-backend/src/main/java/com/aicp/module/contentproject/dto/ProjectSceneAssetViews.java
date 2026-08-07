package com.aicp.module.contentproject.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Response contracts for project-scoped scene masters and their versions. */
public interface ProjectSceneAssetViews {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SceneAssetView(
            Long id,
            String uuid,
            Long contentProjectId,
            String assetType,
            String name,
            String sourceType,
            String status,
            Long currentVersionId,
            Integer currentVersionNo,
            Map<String, Object> master,
            List<Map<String, Object>> variants,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SceneAssetVersionView(
            Long id,
            Long assetId,
            Integer versionNo,
            Map<String, Object> metadata,
            String changeNote,
            LocalDateTime createdAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SceneAssetImpactView(
            Long assetId,
            long lockedReferences,
            long staleReferences,
            List<ImpactReferenceView> references) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ImpactReferenceView(String type, Long id, Long versionId, String syncStatus) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SceneAssetMarkdownView(String path, String content) {}
}
