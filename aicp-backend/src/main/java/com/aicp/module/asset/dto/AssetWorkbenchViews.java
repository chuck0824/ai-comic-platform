package com.aicp.module.asset.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * View DTOs (camelCase) for the asset workbench API.
 */
public final class AssetWorkbenchViews {
    private AssetWorkbenchViews() {}

    public record RecordSummary(
            String recordKind,      // TASK / ASSET
            String recordId,        // "task-{uuid}" or "asset-{uuid}"
            String name,
            String assetType,
            String mediaType,
            String status,           // lowercase for tasks, UPPERCASE for assets
            String modelId,
            String provider,
            String creatorName,
            String creatorAvatar,
            Long createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Integer progress,
            Integer creditCost,
            String errorCode,
            String errorSummary,
            String previewUrl,
            Long fileSize,
            Integer width,
            Integer height,
            Integer durationMs,
            boolean favorite,
            boolean published,
            String projectUuid,
            String projectName,
            List<String> allowedActions) {}

    public record RecordDetail(
            String recordKind,
            String recordId,
            String name,
            String assetType,
            String mediaType,
            String status,
            String description,
            List<String> tags,
            String provider,
            String modelId,
            String prompt,
            String negativePrompt,
            Long seed,
            Integer width,
            Integer height,
            Integer durationMs,
            Long fileSize,
            String mimeType,
            String storageKey,
            String checksum,
            Long createdBy,
            String creatorName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean favorite,
            boolean published,
            Integer rowVersion,
            String projectUuid,
            String projectName,
            ReferenceView source,
            List<ReferenceView> canvasReferences,
            List<ActivityView> activities,
            List<VersionSummary> versions) {}

    public record ReferenceView(
            String sourceType,       // canvas / node / shot / task / listing
            String sourceId,
            String sourceName,
            String url) {}

    public record ActivityView(
            String action,
            String actorName,
            String description,
            LocalDateTime createdAt) {}

    public record VersionSummary(
            Long versionId,
            Integer versionNumber,
            Long fileSize,
            String mimeType,
            String storageProvider,
            LocalDateTime createdAt) {}

    public record RecordFacets(
            long total,
            long pending,
            long running,
            long succeeded,
            long failed,
            long canceled,
            long trashed,
            java.util.Map<String, Long> byAssetType,
            java.util.Map<String, Long> byMediaType) {}

    public record ProjectSummary(
            String projectUuid,
            String projectName,
            String coverUrl,
            String genre,
            long assetCount,
            long runningCount,
            long failedCount) {}

    public record PageResult<T>(
            List<T> items,
            int page,
            int pageSize,
            long total,
            int totalPages,
            boolean hasMore,
            RecordFacets facets) {}

    public record CanvasPlacementView(
            Long placementId,
            String nodeUuid,
            String redirectUrl,
            boolean replayed) {}

    public record DownloadInfo(String downloadUrl, long expiresAt) {}

    public record BatchResult(int succeeded, int failed, List<String> errors) {}
}
