package com.aicp.module.asset.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Validated request DTOs for the asset workbench module.
 */
public final class AssetWorkbenchRequests {
    private AssetWorkbenchRequests() {}

    public record RecordQuery(
            String scope,            // mine / team
            String projectUuid,
            String collection,       // UNFILED / FAVORITES / PUBLISHED / TRASH
            String recordKind,       // TASK / ASSET
            String assetType,
            String status,           // comma-separated statuses
            String mediaType,
            String modelId,
            Long createdBy,
            String createdFrom,      // ISO date
            String createdTo,        // ISO date
            String tags,             // comma-separated AND match
            @Size(max = 100) String keyword,
            String sort,             // created_at:desc / created_at:asc / updated_at:desc / name:asc
            @Min(1) Integer page,
            @Min(1) @Max(100) Integer pageSize) {

        public RecordQuery {
            if (page == null) page = 1;
            if (pageSize == null) pageSize = 24;
            if (sort == null || sort.isBlank()) sort = "created_at:desc";
        }
    }

    public record EditAssetRequest(
            @Size(max = 200) String name,
            String assetType,
            String tags,
            Integer rowVersion) {}

    public record ToggleFavoriteRequest(boolean favorite) {}

    public record MoveAssetRequest(String targetProjectUuid, String targetAssetType) {}

    public record BatchAssetRequest(
            @Size(min = 1, max = 100) java.util.List<String> assetUuids,
            String operation,
            String payload) {}

    public record CanvasPlacementRequest(
            String targetProjectUuid,
            String targetCanvasUuid,
            String placement,     // viewport_center / auto / absolute
            Integer x,
            Integer y) {}

    public record PublishAssetRequest(
            @Size(max = 200) String title,
            @Size(max = 1000) String description,
            String tags,
            String licenseType) {}
}
