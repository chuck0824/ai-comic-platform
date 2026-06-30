package com.aicp.module.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Stable response DTOs for asset-market API contracts.
 * All fields use wrapper types and immutable records to prevent
 * accidental mutation of response data.
 */
public class AssetViews {

    /** Public market listing card (search results). */
    public record ListingCard(
            Long id,
            String name,
            String authorName,
            String assetType,
            List<String> tags,
            String thumbnailUrl,
            Integer useCount,
            BigDecimal rating,
            String licenseType,
            Boolean claimed,
            LocalDateTime createdAt) {
    }

    /** Public market listing detail (full view). */
    public record ListingDetail(
            Long id,
            String name,
            String authorName,
            String assetType,
            List<String> tags,
            String description,
            List<String> previews,
            Object versionInfo,
            Object recommendedParams,
            String licenseScope,
            Integer useCount,
            BigDecimal rating,
            String licenseType,
            Boolean claimed,
            Boolean favorited,
            LocalDateTime createdAt) {
    }

    /** Workspace asset view (library). */
    public record AssetView(
            Long id,
            String uuid,
            String workspaceId,
            String workspaceType,
            Long creatorUserId,
            String assetType,
            String name,
            String description,
            List<String> tags,
            String accessScope,
            String sourceType,
            Long sourceListingId,
            Long currentVersionId,
            String status,
            Integer rowVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /** Asset version snapshot. */
    public record VersionView(
            Long id,
            Long assetId,
            Integer versionNumber,
            Object metadata,
            String previewUrl,
            Long createdBy,
            LocalDateTime createdAt) {
    }

    /** Published listing result. */
    public record ListingView(
            Long id,
            Long sourceAssetId,
            Long sourceVersionId,
            String assetType,
            String name,
            String status,
            Integer rowVersion,
            LocalDateTime createdAt) {
    }

    /** Claim result. */
    public record ClaimView(
            Long listingId,
            Long workspaceAssetId,
            Long entitlementId,
            Boolean claimed) {
    }

    /** Application result. */
    public record ApplyView(
            Long applicationId,
            String undoToken,
            String changeSummary) {
    }

    /** Publish request view. */
    public record PublishRequestView(
            Long id,
            Long assetId,
            Long versionId,
            Long requesterId,
            Long reviewerId,
            String status,
            String reason,
            String reviewComment,
            Integer rowVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    private AssetViews() {}
}
