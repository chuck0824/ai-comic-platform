package com.aicp.module.asset.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetViews;
import com.aicp.module.asset.entity.*;
import com.aicp.module.asset.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetClaimService {

    private final MarketListingMapper listingMapper;
    private final AssetEntitlementMapper entitlementMapper;
    private final WorkspaceAssetMapper assetMapper;
    private final AssetVersionMapper versionMapper;
    private final AssetFavoriteMapper favoriteMapper;

    /**
     * Idempotent free claim. Repeated calls return the same workspace asset.
     * Within one transaction: check existing entitlement → lock listing →
     * create entitlement → clone snapshot as workspace asset → increment use count.
     */
    @Transactional
    public AssetViews.ClaimView claim(WorkspaceContext ctx, Long listingId) {
        ctx.require("asset.use");

        // Check existing entitlement (idempotency)
        AssetEntitlement existing = entitlementMapper.selectOne(new LambdaQueryWrapper<AssetEntitlement>()
                .eq(AssetEntitlement::getBeneficiaryWorkspaceId, ctx.workspaceId())
                .eq(AssetEntitlement::getListingId, listingId));
        if (existing != null) {
            WorkspaceAsset wa = assetMapper.selectOne(new LambdaQueryWrapper<WorkspaceAsset>()
                    .eq(WorkspaceAsset::getWorkspaceId, ctx.workspaceId())
                    .eq(WorkspaceAsset::getSourceListingId, listingId)
                    .eq(WorkspaceAsset::getSourceType, "MARKET_CLAIMED"));
            return new AssetViews.ClaimView(listingId, wa != null ? wa.getId() : null,
                    existing.getId(), true);
        }

        // Lock and verify listing
        MarketListing listing = listingMapper.selectById(listingId);
        if (listing == null || !"LISTED".equals(listing.getStatus())) {
            throw new BizException(ErrorCode.LISTING_UNAVAILABLE);
        }

        // Create entitlement and workspace asset in one transaction
        AssetEntitlement entitlement = new AssetEntitlement();
        entitlement.setBeneficiaryWorkspaceId(ctx.workspaceId());
        entitlement.setListingId(listingId);
        entitlement.setSourceVersionId(listing.getSourceVersionId());
        entitlement.setGrantType("FREE_CLAIM");
        entitlement.setClaimedBy(ctx.userId());

        try {
            entitlementMapper.insert(entitlement);
        } catch (DuplicateKeyException e) {
            // Race condition: re-read the winning entitlement
            AssetEntitlement winner = entitlementMapper.selectOne(new LambdaQueryWrapper<AssetEntitlement>()
                    .eq(AssetEntitlement::getBeneficiaryWorkspaceId, ctx.workspaceId())
                    .eq(AssetEntitlement::getListingId, listingId));
            WorkspaceAsset wa = assetMapper.selectOne(new LambdaQueryWrapper<WorkspaceAsset>()
                    .eq(WorkspaceAsset::getWorkspaceId, ctx.workspaceId())
                    .eq(WorkspaceAsset::getSourceListingId, listingId)
                    .eq(WorkspaceAsset::getSourceType, "MARKET_CLAIMED"));
            return new AssetViews.ClaimView(listingId, wa != null ? wa.getId() : null,
                    winner.getId(), true);
        }

        // Clone snapshot into buyer's workspace asset
        WorkspaceAsset buyerAsset = new WorkspaceAsset();
        buyerAsset.setUuid(UUID.randomUUID().toString());
        buyerAsset.setWorkspaceId(ctx.workspaceId());
        buyerAsset.setWorkspaceType(ctx.workspaceType());
        buyerAsset.setCreatorUserId(ctx.userId());
        buyerAsset.setAssetType(listing.getAssetType());
        buyerAsset.setName(extractName(listing.getPublicSnapshot()));
        buyerAsset.setSourceType("MARKET_CLAIMED");
        buyerAsset.setSourceListingId(listingId);
        buyerAsset.setSourceVersionId(listing.getSourceVersionId());
        buyerAsset.setAccessScope("PRIVATE");
        buyerAsset.setStatus("ACTIVE");
        buyerAsset.setCurrentVersionId(listing.getSourceVersionId());
        buyerAsset.setRowVersion(0);
        buyerAsset.setCreatedBy(ctx.userId());
        buyerAsset.setUpdatedBy(ctx.userId());
        assetMapper.insert(buyerAsset);

        // Increment use count
        listing.setUseCount((listing.getUseCount() != null ? listing.getUseCount() : 0) + 1);
        listingMapper.updateById(listing);

        log.info("Asset claimed: listing={}, buyerWorkspace={}, buyerAsset={}",
                listingId, ctx.workspaceId(), buyerAsset.getId());

        return new AssetViews.ClaimView(listingId, buyerAsset.getId(), entitlement.getId(), true);
    }

    public void favorite(WorkspaceContext ctx, Long listingId) {
        ctx.require("asset.use");
        AssetFavorite fav = new AssetFavorite();
        fav.setUserId(ctx.userId());
        fav.setWorkspaceId(ctx.workspaceId());
        fav.setListingId(listingId);
        try {
            favoriteMapper.insert(fav);
        } catch (DuplicateKeyException e) {
            // Already favorited — idempotent
        }
    }

    private String extractName(String snapshot) {
        try {
            ObjectMapper om = new ObjectMapper();
            Map<String, Object> map = om.readValue(snapshot, Map.class);
            Object name = map.get("name");
            return name != null ? name.toString() : "未命名资产";
        } catch (Exception e) {
            return "未命名资产";
        }
    }

    public void unfavorite(WorkspaceContext ctx, Long listingId) {
        favoriteMapper.delete(new LambdaQueryWrapper<AssetFavorite>()
                .eq(AssetFavorite::getUserId, ctx.userId())
                .eq(AssetFavorite::getWorkspaceId, ctx.workspaceId())
                .eq(AssetFavorite::getListingId, listingId));
    }
}
