package com.aicp.module.asset.service;

import com.aicp.common.dto.PageResult;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetViews;
import com.aicp.module.asset.entity.AssetEntitlement;
import com.aicp.module.asset.entity.AssetFavorite;
import com.aicp.module.asset.entity.MarketListing;
import com.aicp.module.asset.mapper.AssetEntitlementMapper;
import com.aicp.module.asset.mapper.AssetFavoriteMapper;
import com.aicp.module.asset.mapper.MarketListingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Read-only public market queries. All results are derived from LISTED
 * {@link MarketListing} snapshots — private seller assets are never leaked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketQueryService {

    private final MarketListingMapper listingMapper;
    private final AssetEntitlementMapper entitlementMapper;
    private final AssetFavoriteMapper favoriteMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> ALLOWED_SORTS = Set.of("latest", "popular", "rating", "relevance");
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * Search public listings with keyword, type, sort, and pagination.
     * Never returns UNLISTED or REMOVED listings.
     */
    public PageResult<AssetViews.ListingCard> search(String keyword, String type, String sort,
                                                      int page, int pageSize,
                                                      WorkspaceContext ctx) {
        int safePageSize = Math.min(pageSize > 0 ? pageSize : 20, MAX_PAGE_SIZE);
        String safeSort = ALLOWED_SORTS.contains(sort) ? sort : "latest";

        LambdaQueryWrapper<MarketListing> qw = new LambdaQueryWrapper<>();
        qw.eq(MarketListing::getStatus, "LISTED");

        if (StringUtils.hasText(type)) {
            qw.eq(MarketListing::getAssetType, type.toUpperCase());
        }

        if (StringUtils.hasText(keyword)) {
            String escaped = escapeLike(keyword);
            qw.and(w -> w
                    .like(MarketListing::getPublicSnapshot, escaped)
            );
        }

        // Sort: latest → created_at DESC, popular → use_count DESC, rating → rating DESC
        switch (safeSort) {
            case "popular" -> qw.orderByDesc(MarketListing::getUseCount);
            case "rating" -> qw.orderByDesc(MarketListing::getRating);
            default -> qw.orderByDesc(MarketListing::getCreatedAt);
        }

        Page<MarketListing> mpPage = new Page<>(page, safePageSize);
        Page<MarketListing> result = listingMapper.selectPage(mpPage, qw);

        List<AssetViews.ListingCard> cards = result.getRecords().stream()
                .map(ml -> toListingCard(ml, ctx))
                .toList();

        return PageResult.of(cards, page, safePageSize, result.getTotal());
    }

    /**
     * Get the full detail of a public listing.
     * Returns null if the listing is not LISTED.
     */
    public AssetViews.ListingDetail getDetail(Long listingId, WorkspaceContext ctx) {
        MarketListing ml = listingMapper.selectById(listingId);
        if (ml == null || !"LISTED".equals(ml.getStatus())) {
            return null;
        }

        boolean claimed = ctx != null && hasEntitlement(ctx.workspaceId(), listingId);
        boolean favorited = ctx != null && isFavorited(ctx.userId(), ctx.workspaceId(), listingId);

        Map<String, Object> snapshot = parseSnapshot(ml.getPublicSnapshot());

        return new AssetViews.ListingDetail(
                ml.getId(),
                stringFromSnapshot(snapshot, "name"),
                stringFromSnapshot(snapshot, "author_name"),
                ml.getAssetType(),
                tagsFromSnapshot(snapshot),
                stringFromSnapshot(snapshot, "description"),
                previewsFromSnapshot(snapshot),
                versionInfoFromSnapshot(snapshot),
                recommendedParamsFromSnapshot(snapshot),
                stringFromSnapshot(snapshot, "license_scope"),
                ml.getUseCount(),
                ml.getRating(),
                ml.getLicenseType(),
                claimed,
                favorited,
                ml.getCreatedAt()
        );
    }

    // ---- Internal helpers ----

    private AssetViews.ListingCard toListingCard(MarketListing ml, WorkspaceContext ctx) {
        Map<String, Object> snap = parseSnapshot(ml.getPublicSnapshot());
        boolean claimed = ctx != null && hasEntitlement(ctx.workspaceId(), ml.getId());

        List<String> tags = tagsFromSnapshot(snap);
        String thumbnailUrl = previewsFromSnapshot(snap).isEmpty()
                ? null : previewsFromSnapshot(snap).get(0);

        return new AssetViews.ListingCard(
                ml.getId(),
                stringFromSnapshot(snap, "name"),
                stringFromSnapshot(snap, "author_name"),
                ml.getAssetType(),
                tags,
                thumbnailUrl,
                ml.getUseCount(),
                ml.getRating(),
                ml.getLicenseType(),
                claimed,
                ml.getCreatedAt()
        );
    }

    private boolean hasEntitlement(String workspaceId, Long listingId) {
        return entitlementMapper.exists(
                new LambdaQueryWrapper<AssetEntitlement>()
                        .eq(AssetEntitlement::getBeneficiaryWorkspaceId, workspaceId)
                        .eq(AssetEntitlement::getListingId, listingId));
    }

    private boolean isFavorited(Long userId, String workspaceId, Long listingId) {
        return favoriteMapper.exists(
                new LambdaQueryWrapper<AssetFavorite>()
                        .eq(AssetFavorite::getUserId, userId)
                        .eq(AssetFavorite::getWorkspaceId, workspaceId)
                        .eq(AssetFavorite::getListingId, listingId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSnapshot(String json) {
        if (!StringUtils.hasText(json)) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse listing snapshot: {}", e.getMessage());
            return Map.of();
        }
    }

    private String stringFromSnapshot(Map<String, Object> snap, String key) {
        Object val = snap.get(key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> tagsFromSnapshot(Map<String, Object> snap) {
        Object tags = snap.get("tags");
        if (tags instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> previewsFromSnapshot(Map<String, Object> snap) {
        Object previews = snap.get("previews");
        if (previews instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private Object versionInfoFromSnapshot(Map<String, Object> snap) {
        return snap.getOrDefault("version_info", Map.of());
    }

    private Object recommendedParamsFromSnapshot(Map<String, Object> snap) {
        return snap.getOrDefault("recommended_params", Map.of());
    }

    /** Escape SQL LIKE wildcards to prevent accidental pattern matching. */
    private String escapeLike(String input) {
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
