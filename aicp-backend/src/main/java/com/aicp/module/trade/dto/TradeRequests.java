package com.aicp.module.trade.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * Validated inbound request records for the trade module.
 * All money values are in cents (long).
 */
public final class TradeRequests {

    private TradeRequests() {
    }

    /** Create a new listing draft from a warehouse script version. */
    public record CreateListing(
            @NotBlank String workspaceId,
            @NotNull Long scriptId,
            @NotNull Long scriptVersionId,
            @NotBlank @Size(min = 1, max = 120) String title,
            @Size(max = 5000) String synopsis,
            String coverUrl,
            String tagsJson,
            String charactersJson,
            Integer episodeCount,
            String authorDisplayName,
            @Min(1) @Max(3) int previewEpisodeCount,
            String previewEpisodesJson,
            @NotEmpty List<LicenseOptionInput> licenseOptions) {
    }

    public record LicenseOptionInput(
            @NotBlank String licenseType,
            @PositiveOrZero long priceCents,
            String termJson,
            String agreementText,
            String agreementVersion) {
    }

    /** Update an existing listing draft. */
    public record UpdateListing(
            @Size(min = 1, max = 120) String title,
            @Size(max = 5000) String synopsis,
            String coverUrl,
            String tagsJson,
            String charactersJson,
            Integer episodeCount,
            String authorDisplayName,
            @Min(1) @Max(3) Integer previewEpisodeCount,
            String previewEpisodesJson,
            List<LicenseOptionInput> licenseOptions) {
    }

    /** Platform review decision. */
    public record ReviewDecision(
            @NotNull boolean approved,
            @Size(max = 2000) String reason) {
    }

    /** Public market query parameters. */
    public record MarketQuery(
            String keyword,
            String genre,
            String plot,
            String tone,
            String setting,
            String licenseType,
            Integer minPrice,
            Integer maxPrice,
            @Pattern(regexp = "latest|popular|sales|rating") String sort,
            @Min(1) int page,
            @Min(1) @Max(100) int pageSize) {

        public MarketQuery {
            if (sort == null || sort.isBlank()) sort = "latest";
            if (page < 1) page = 1;
            if (pageSize < 1) pageSize = 20;
        }
    }

    /** Create an order. Price is derived server-side; client price is for hint only. */
    public record CreateOrder(
            @NotNull Long listingId,
            @NotBlank String licenseType,
            @NotBlank String idempotencyKey) {
    }

    /** Confirm payment for an order. */
    public record PayOrder(
            @NotBlank String paymentMethod) {
    }

    /** Enterprise purchase request. */
    public record CreatePurchaseRequest(
            @NotBlank String workspaceId,
            @NotNull Long listingId,
            @NotBlank String licenseType,
            @Size(max = 2000) String reason) {
    }

    /** Enterprise approval / rejection. */
    public record ApprovalDecision(
            @NotNull boolean approved,
            @Size(max = 2000) String comment) {
    }

    /** Refund request from buyer. */
    public record CreateRefundRequest(
            @NotBlank String reasonCode,
            @Size(max = 2000) String reasonText) {
    }
}
