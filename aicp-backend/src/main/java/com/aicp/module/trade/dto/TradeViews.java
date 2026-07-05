package com.aicp.module.trade.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Stable public response records for the trade module.
 * Never expose seller private script row IDs.
 * All money values are in cents (long).
 */
public final class TradeViews {

    private TradeViews() {
    }

    /** Card shown in the public market grid. */
    public record ListingCard(
            Long id,
            String title,
            String synopsis,
            String coverUrl,
            String tagsJson,
            Integer episodeCount,
            String authorDisplayName,
            Double rating,
            Integer salesCount,
            List<LicenseOptionView> licenses,
            String listingStatus,
            LocalDateTime listedAt) {
    }

    /** License option shown to buyers. */
    public record LicenseOptionView(
            String licenseType,
            Long priceCents,
            String currency,
            String agreementSummary,
            String agreementVersion) {
    }

    /** Full listing detail including historical disclosures. */
    public record ListingDetail(
            Long id,
            String title,
            String synopsis,
            String coverUrl,
            String tagsJson,
            String charactersJson,
            Integer episodeCount,
            String authorDisplayName,
            int previewEpisodeCount,
            List<LicenseOptionView> licenses,
            int historicalNormalCount,
            String listingStatus,
            LocalDateTime listedAt) {
    }

    /** Preview content for approved episodes only. */
    public record PreviewView(
            Long listingId,
            List<EpisodePreview> episodes) {

        public record EpisodePreview(
                int episodeNumber,
                String title,
                String content) {
        }
    }

    /** Order summary. */
    public record OrderView(
            String orderNo,
            String status,
            Long totalAmountCents,
            String currency,
            Long platformFeeCents,
            Long sellerIncomeCents,
            String licenseType,
            String titleSnapshot,
            String buyerWorkspaceId,
            LocalDateTime expiresAt,
            LocalDateTime paidAt,
            LocalDateTime fulfilledAt,
            String failureReason) {
    }

    /** Seller sales overview. */
    public record SellerOverview(
            long totalRevenueCents,
            long frozenRevenueCents,
            long availableRevenueCents,
            int totalOrders,
            int scriptsSold) {
    }

    /** Seller order list item. */
    public record SellerOrderView(
            String orderNo,
            String status,
            String title,
            String licenseType,
            Long totalAmountCents,
            Long sellerIncomeCents,
            LocalDateTime createdAt) {
    }

    /** Enterprise purchase request view. */
    public record PurchaseRequestView(
            Long id,
            String workspaceId,
            Long requesterUserId,
            Long listingId,
            String licenseType,
            Long amountCents,
            String reason,
            String status,
            Long approverUserId,
            String approvalComment,
            String orderNo,
            LocalDateTime createdAt) {
    }

    /** Refund request view. */
    public record RefundView(
            Long id,
            String orderNo,
            String reasonCode,
            String reasonText,
            String status,
            Long refundAmountCents,
            String reviewComment,
            LocalDateTime createdAt) {
    }

    /** Generic paginated response. */
    public record PageView<T>(
            List<T> items,
            int page,
            int pageSize,
            long total,
            int totalPages,
            boolean hasMore) {
    }
}
