package com.aicp.module.asset.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetRequests;
import com.aicp.module.asset.dto.AssetViews;
import com.aicp.module.asset.entity.*;
import com.aicp.module.asset.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetPublicationService {

    private final AssetLibraryService libraryService;
    private final WorkspaceAssetMapper assetMapper;
    private final AssetVersionMapper versionMapper;
    private final MarketListingMapper listingMapper;
    private final AssetPublishRequestMapper publishRequestMapper;
    private final AssetOutboxService outboxService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Personal workspace: direct publish. Enterprise workspace: rejects — use requestEnterprisePublish. */
    @Transactional
    public AssetViews.ListingView publishPersonal(WorkspaceContext ctx, Long assetId, AssetRequests.PublishAssetRequest req) {
        ctx.require("asset.manage");
        if (!"personal".equals(ctx.workspaceType())) {
            throw new BizException(ErrorCode.PUBLISH_STATE_CONFLICT.getCode(), "企业资产请通过发布申请流程");
        }
        return upsertListing(ctx, assetId, req);
    }

    /** Enterprise workspace: submit a publish request for admin approval. */
    @Transactional
    public AssetViews.PublishRequestView requestEnterprisePublish(WorkspaceContext ctx, Long assetId, AssetRequests.PublishAssetRequest req) {
        ctx.require("asset.publish.request");
        if (!"enterprise".equals(ctx.workspaceType())) {
            throw new BizException(ErrorCode.ASSET_PERMISSION_DENIED);
        }
        WorkspaceAsset asset = libraryService.requireWorkspaceAsset(ctx, assetId);
        // Check no existing PENDING request for this asset+version
        Long count = publishRequestMapper.selectCount(new LambdaQueryWrapper<AssetPublishRequest>()
                .eq(AssetPublishRequest::getWorkspaceId, ctx.workspaceId())
                .eq(AssetPublishRequest::getAssetId, assetId)
                .eq(AssetPublishRequest::getVersionId, req.versionId())
                .eq(AssetPublishRequest::getStatus, "PENDING"));
        if (count > 0) {
            throw new BizException(ErrorCode.PUBLISH_STATE_CONFLICT.getCode(), "该版本已有待审批的发布申请");
        }

        AssetPublishRequest pr = new AssetPublishRequest();
        pr.setWorkspaceId(ctx.workspaceId());
        pr.setAssetId(assetId);
        pr.setVersionId(req.versionId());
        pr.setRequesterId(ctx.userId());
        pr.setStatus("PENDING");
        pr.setReason(req.name());
        pr.setRowVersion(0);
        publishRequestMapper.insert(pr);

        outboxService.emit("ASSET_PUBLISH", "publish-" + pr.getId(), "SUBMITTED",
                Map.of("source_id", "publish-" + pr.getId(),
                       "workspace_id", ctx.workspaceId(),
                       "department_id", "",
                       "requester_user_id", ctx.userId(),
                       "summary", "资产发布申请 #" + pr.getId(),
                       "amount_cents", 0,
                       "status", "PENDING"));

        return toPublishRequestView(pr);
    }

    @Transactional
    public AssetViews.ListingView approve(WorkspaceContext ctx, Long requestId, AssetRequests.ReviewRequest body) {
        ctx.require("asset.publish.approve");
        AssetPublishRequest pr = requirePublishRequest(ctx, requestId);
        if (!"PENDING".equals(pr.getStatus())) {
            throw new BizException(ErrorCode.PUBLISH_STATE_CONFLICT);
        }
        if (!pr.getRowVersion().equals(body.rowVersion())) {
            throw new BizException(ErrorCode.ASSET_VERSION_CONFLICT);
        }

        pr.setStatus("APPROVED");
        pr.setReviewerId(ctx.userId());
        pr.setReviewComment(body.reason());
        pr.setRowVersion(pr.getRowVersion() + 1);
        publishRequestMapper.updateById(pr);

        // Emit outbox for approval projection
        outboxService.emit("ASSET_PUBLISH", "publish-" + pr.getId(), "APPROVED",
                Map.of("source_id", "publish-" + pr.getId(),
                       "workspace_id", pr.getWorkspaceId(),
                       "department_id", "",
                       "requester_user_id", pr.getRequesterId(),
                       "summary", "资产发布已批准 #" + pr.getId(),
                       "amount_cents", 0,
                       "status", "APPROVED"));

        // Upsert the listing
        AssetRequests.PublishAssetRequest pubReq = new AssetRequests.PublishAssetRequest(
                pr.getVersionId(), pr.getReason(), null, null, null, null, body.rowVersion());
        return upsertListing(ctx, pr.getAssetId(), pubReq);
    }

    @Transactional
    public AssetViews.PublishRequestView reject(WorkspaceContext ctx, Long requestId, AssetRequests.ReviewRequest body) {
        ctx.require("asset.publish.approve");
        if (body.reason() == null || body.reason().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "驳回原因不能为空");
        }
        AssetPublishRequest pr = requirePublishRequest(ctx, requestId);
        if (!"PENDING".equals(pr.getStatus())) {
            throw new BizException(ErrorCode.PUBLISH_STATE_CONFLICT);
        }
        pr.setStatus("REJECTED");
        pr.setReviewerId(ctx.userId());
        pr.setReason(body.reason());
        pr.setRowVersion(pr.getRowVersion() + 1);
        publishRequestMapper.updateById(pr);

        outboxService.emit("ASSET_PUBLISH", "publish-" + pr.getId(), "REJECTED",
                Map.of("source_id", "publish-" + pr.getId(),
                       "workspace_id", pr.getWorkspaceId(),
                       "department_id", "",
                       "requester_user_id", pr.getRequesterId(),
                       "summary", "资产发布已驳回 #" + pr.getId(),
                       "amount_cents", 0,
                       "status", "REJECTED"));

        return toPublishRequestView(pr);
    }

    @Transactional
    public void cancel(WorkspaceContext ctx, Long requestId) {
        AssetPublishRequest pr = requirePublishRequest(ctx, requestId);
        if (!ctx.userId().equals(pr.getRequesterId()) && !ctx.has("asset.publish.approve")) {
            throw new BizException(ErrorCode.ASSET_PERMISSION_DENIED);
        }
        if (!"PENDING".equals(pr.getStatus())) {
            throw new BizException(ErrorCode.PUBLISH_STATE_CONFLICT);
        }
        pr.setStatus("CANCELLED");
        pr.setRowVersion(pr.getRowVersion() + 1);
        publishRequestMapper.updateById(pr);
    }

    @Transactional
    public void unlist(WorkspaceContext ctx, Long assetId, Integer rowVersion) {
        ctx.require("asset.manage");
        libraryService.requireWorkspaceAsset(ctx, assetId);
        MarketListing listing = listingMapper.selectOne(new LambdaQueryWrapper<MarketListing>()
                .eq(MarketListing::getSourceAssetId, assetId)
                .eq(MarketListing::getStatus, "LISTED"));
        if (listing == null) return;
        if (!listing.getRowVersion().equals(rowVersion)) {
            throw new BizException(ErrorCode.ASSET_VERSION_CONFLICT);
        }
        listing.setStatus("UNLISTED");
        listing.setRowVersion(listing.getRowVersion() + 1);
        listingMapper.updateById(listing);
    }

    /** Upsert the market listing from the asset and version. */
    private AssetViews.ListingView upsertListing(WorkspaceContext ctx, Long assetId, AssetRequests.PublishAssetRequest req) {
        WorkspaceAsset asset = libraryService.requireWorkspaceAsset(ctx, assetId);
        AssetVersion version = versionMapper.selectById(req.versionId());
        if (version == null || !version.getAssetId().equals(assetId)) {
            throw new BizException(ErrorCode.ASSET_NOT_FOUND);
        }

        // Build public snapshot
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", req.name());
        snapshot.put("description", req.description());
        snapshot.put("tags", req.tags() != null ? req.tags() : List.of());
        snapshot.put("author_name", req.authorName());
        snapshot.put("license_scope", req.licenseScope());
        // Previews come from asset version, not request
        snapshot.put("previews", version.getPreviewUrl() != null
                ? List.of(version.getPreviewUrl()) : List.of());

        // Check for existing listing
        MarketListing listing = listingMapper.selectOne(new LambdaQueryWrapper<MarketListing>()
                .eq(MarketListing::getSourceAssetId, assetId)
                .eq(MarketListing::getSourceVersionId, req.versionId()));

        if (listing == null) {
            listing = new MarketListing();
            listing.setPublisherWorkspaceId(ctx.workspaceId());
            listing.setPublisherUserId(ctx.userId());
            listing.setSourceAssetId(assetId);
            listing.setSourceVersionId(req.versionId());
            listing.setAssetType(asset.getAssetType());
            listing.setLicenseType("FREE");
            listing.setPrice(java.math.BigDecimal.ZERO);
            listing.setUseCount(0);
            listing.setRating(java.math.BigDecimal.ZERO);
        }

        listing.setPublicSnapshot(toJson(snapshot));
        listing.setStatus("LISTED");
        listing.setRowVersion(listing.getRowVersion() == null ? 0 : listing.getRowVersion() + 1);

        if (listing.getId() == null) {
            listingMapper.insert(listing);
        } else {
            listingMapper.updateById(listing);
        }

        return new AssetViews.ListingView(listing.getId(), listing.getSourceAssetId(),
                listing.getSourceVersionId(), listing.getAssetType(),
                stringFromSnapshot(snapshot, "name"), listing.getStatus(),
                listing.getRowVersion(), listing.getCreatedAt());
    }

    private AssetPublishRequest requirePublishRequest(WorkspaceContext ctx, Long requestId) {
        AssetPublishRequest pr = publishRequestMapper.selectById(requestId);
        if (pr == null || !ctx.workspaceId().equals(pr.getWorkspaceId())) {
            throw new BizException(ErrorCode.ASSET_NOT_FOUND);
        }
        return pr;
    }

    private AssetViews.PublishRequestView toPublishRequestView(AssetPublishRequest pr) {
        return new AssetViews.PublishRequestView(pr.getId(), pr.getAssetId(), pr.getVersionId(),
                pr.getRequesterId(), pr.getReviewerId(), pr.getStatus(), pr.getReason(),
                pr.getReviewComment(), pr.getRowVersion(), pr.getCreatedAt(), pr.getUpdatedAt());
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private String stringFromSnapshot(Map<String, Object> snap, String key) {
        Object val = snap.get(key);
        return val != null ? val.toString() : null;
    }
}
