package com.aicp.module.trade.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.trade.domain.TradeEnums.ListingStatus;
import com.aicp.module.trade.dto.TradeRequests.MarketQuery;
import com.aicp.module.trade.dto.TradeViews.*;
import com.aicp.module.trade.entity.ListingLicenseOption;
import com.aicp.module.trade.entity.ScriptListing;
import com.aicp.module.trade.mapper.ListingLicenseOptionMapper;
import com.aicp.module.trade.mapper.ScriptListingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Public read-only market queries.
 * Only returns LISTED or EXCLUSIVE_RESERVED listings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeMarketQueryService {

    private final ScriptListingMapper listingMapper;
    private final ListingLicenseOptionMapper licenseOptionMapper;

    private static final Set<String> ALLOWED_SORTS = Set.of("latest", "popular", "sales", "rating");
    private static final Set<String> PUBLIC_STATUSES = Set.of(
            ListingStatus.LISTED.name(),
            ListingStatus.EXCLUSIVE_RESERVED.name());

    /**
     * Search public market listings with filters, sort, and pagination.
     */
    public PageView<ListingCard> search(MarketQuery query) {
        LambdaQueryWrapper<ScriptListing> q = new LambdaQueryWrapper<>();
        q.in(ScriptListing::getListingStatus, PUBLIC_STATUSES);

        // Keyword search on title
        if (query.keyword() != null && !query.keyword().isBlank()) {
            q.like(ScriptListing::getTitle, query.keyword());
        }

        // Tag filtering done via JSON string matching (simple approach)
        if (query.genre() != null && !query.genre().isBlank()) {
            q.like(ScriptListing::getTagsJson, query.genre());
        }

        // Sorting
        String sort = query.sort();
        if (sort == null || !ALLOWED_SORTS.contains(sort)) {
            sort = "latest";
        }
        switch (sort) {
            case "popular" -> q.orderByDesc(ScriptListing::getHistoricalNormalCount);
            case "sales" -> q.orderByDesc(ScriptListing::getHistoricalNormalCount); // proxy
            case "rating" -> q.orderByDesc(ScriptListing::getUpdatedAt); // proxy until ratings exist
            default -> q.orderByDesc(ScriptListing::getListedAt);
        }

        Page<ScriptListing> page = listingMapper.selectPage(
                new Page<>(query.page(), query.pageSize()), q);

        List<ListingCard> cards = page.getRecords().stream()
                .map(this::toCard)
                .toList();

        return new PageView<>(
                cards,
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                (int) page.getPages(),
                page.getCurrent() < page.getPages());
    }

    /**
     * Get full listing detail including license options and historical disclosures.
     */
    public ListingDetail detail(Long listingId) {
        ScriptListing listing = listingMapper.selectById(listingId);
        if (listing == null || !PUBLIC_STATUSES.contains(listing.getListingStatus())) {
            throw new BizException(ErrorCode.LISTING_NOT_AVAILABLE);
        }

        List<ListingLicenseOption> options = licenseOptionMapper.selectList(
                new LambdaQueryWrapper<ListingLicenseOption>()
                        .eq(ListingLicenseOption::getListingId, listingId)
                        .eq(ListingLicenseOption::getEnabled, 1));

        List<LicenseOptionView> licenseViews = options.stream()
                .map(o -> new LicenseOptionView(
                        o.getLicenseType(),
                        o.getPriceCents(),
                        o.getCurrency(),
                        o.getAgreementText() != null
                                ? o.getAgreementText().substring(0, Math.min(200, o.getAgreementText().length()))
                                : null,
                        o.getAgreementVersion()))
                .toList();

        return new ListingDetail(
                listing.getId(),
                listing.getTitle(),
                listing.getSynopsis(),
                listing.getCoverUrl(),
                listing.getTagsJson(),
                listing.getCharactersJson(),
                listing.getEpisodeCount(),
                listing.getAuthorDisplayName(),
                listing.getPreviewEpisodeCount(),
                licenseViews,
                listing.getHistoricalNormalCount(),
                listing.getListingStatus(),
                listing.getListedAt());
    }

    /**
     * Return preview episodes. Only returns the approved preview episode count.
     */
    public PreviewView preview(Long listingId) {
        ScriptListing listing = listingMapper.selectById(listingId);
        if (listing == null || !PUBLIC_STATUSES.contains(listing.getListingStatus())) {
            throw new BizException(ErrorCode.LISTING_NOT_AVAILABLE);
        }

        // For now return empty episodes; actual content delivery requires
        // integration with the script module's content service.
        List<PreviewView.EpisodePreview> episodes = Collections.emptyList();
        if (listing.getPreviewEpisodesJson() != null) {
            try {
                // Simple JSON parse — production would use Jackson
                episodes = parsePreviewEpisodes(listing.getPreviewEpisodesJson());
            } catch (Exception e) {
                log.warn("Failed to parse preview episodes for listing {}", listingId, e);
            }
        }

        return new PreviewView(listingId, episodes);
    }

    // -- helpers --

    private ListingCard toCard(ScriptListing listing) {
        List<ListingLicenseOption> options = licenseOptionMapper.selectList(
                new LambdaQueryWrapper<ListingLicenseOption>()
                        .eq(ListingLicenseOption::getListingId, listing.getId())
                        .eq(ListingLicenseOption::getEnabled, 1));

        List<LicenseOptionView> licenseViews = options.stream()
                .map(o -> new LicenseOptionView(
                        o.getLicenseType(),
                        o.getPriceCents(),
                        o.getCurrency(),
                        null,
                        o.getAgreementVersion()))
                .toList();

        return new ListingCard(
                listing.getId(),
                listing.getTitle(),
                listing.getSynopsis() != null
                        ? listing.getSynopsis().substring(0, Math.min(200, listing.getSynopsis().length()))
                        : null,
                listing.getCoverUrl(),
                listing.getTagsJson(),
                listing.getEpisodeCount(),
                listing.getAuthorDisplayName(),
                null, // rating
                listing.getHistoricalNormalCount(), // salesCount proxy
                licenseViews,
                listing.getListingStatus(),
                listing.getListedAt());
    }

    @SuppressWarnings("unchecked")
    private List<PreviewView.EpisodePreview> parsePreviewEpisodes(String json) {
        // Simplified: expect JSON array of {episodeNumber, title, content}
        // In production, use Jackson ObjectMapper
        return Collections.emptyList();
    }
}
