package com.aicp.module.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Validated command DTOs for asset-market write operations.
 */
public class AssetRequests {

    public record CreateAssetRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank String assetType,
            @Size(max = 2000) String description,
            List<String> tags,
            @NotBlank String accessScope) {
    }

    public record EditAssetRequest(
            @Size(max = 200) String name,
            @Size(max = 2000) String description,
            List<String> tags,
            @NotNull Integer rowVersion) {
    }

    public record CreateVersionRequest(
            @NotBlank String metadata,
            String previewUrl) {
    }

    public record PublishAssetRequest(
            @NotNull @Positive Long versionId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            List<String> tags,
            @Size(max = 100) String authorName,
            @Size(max = 500) String licenseScope,
            @NotNull Integer rowVersion) {
    }

    public record ReviewRequest(
            @NotNull Integer rowVersion,
            @Size(max = 500) String reason) {
    }

    public record ApplyAssetRequest(
            @NotNull @Positive Long projectId,
            String targetType,
            Long targetId,
            @NotBlank String idempotencyKey) {
    }

    public record UndoRequest(
            @NotBlank String undoToken,
            @NotNull Integer projectRowVersion) {
    }

    private AssetRequests() {}
}
