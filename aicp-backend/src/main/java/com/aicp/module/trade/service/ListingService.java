package com.aicp.module.trade.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.trade.domain.TradeEnums.ListingStatus;
import com.aicp.module.trade.dto.TradeRequests.CreateListing;
import com.aicp.module.trade.dto.TradeRequests.UpdateListing;
import com.aicp.module.trade.entity.ListingLicenseOption;
import com.aicp.module.trade.entity.ScriptListing;
import com.aicp.module.trade.mapper.ListingLicenseOptionMapper;
import com.aicp.module.trade.mapper.ScriptListingMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Seller-facing listing operations.
 * All mutations require a trusted {@link WorkspaceContext}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingService {

    private final ScriptListingMapper listingMapper;
    private final ListingLicenseOptionMapper licenseOptionMapper;

    /**
     * Create a draft listing from a warehouse script version.
     */
    @Transactional
    public ScriptListing createDraft(WorkspaceContext ctx, CreateListing request) {
        ScriptListing listing = new ScriptListing();
        listing.setWorkspaceId(ctx.workspaceId());
        listing.setSellerUserId(ctx.userId());
        listing.setScriptId(request.scriptId());
        listing.setScriptVersionId(request.scriptVersionId());
        listing.setTitle(request.title());
        listing.setSynopsis(request.synopsis());
        listing.setCoverUrl(request.coverUrl());
        listing.setTagsJson(request.tagsJson());
        listing.setCharactersJson(request.charactersJson());
        listing.setEpisodeCount(request.episodeCount());
        listing.setAuthorDisplayName(request.authorDisplayName());
        listing.setPreviewEpisodeCount(request.previewEpisodeCount());
        listing.setPreviewEpisodesJson(request.previewEpisodesJson());
        listing.setListingStatus(ListingStatus.DRAFT.name());
        listing.setReviewStatus(ListingStatus.DRAFT.name());
        listingMapper.insert(listing);

        if (request.licenseOptions() != null) {
            for (var opt : request.licenseOptions()) {
                ListingLicenseOption entity = new ListingLicenseOption();
                entity.setListingId(listing.getId());
                entity.setLicenseType(opt.licenseType());
                entity.setPriceCents(opt.priceCents());
                entity.setTermJson(opt.termJson());
                entity.setAgreementText(opt.agreementText());
                entity.setAgreementVersion(opt.agreementVersion());
                entity.setEnabled(1);
                licenseOptionMapper.insert(entity);
            }
        }

        log.info("Listing draft created: id={}, workspace={}, seller={}", listing.getId(),
                ctx.workspaceId(), ctx.userId());
        return listing;
    }

    /**
     * Update a draft or rejected listing. Only the owning workspace can update.
     */
    @Transactional
    public ScriptListing updateDraft(WorkspaceContext ctx, Long listingId, UpdateListing request) {
        ScriptListing listing = requireOwnership(ctx, listingId);
        if (listing.getReviewStatus().equals(ListingStatus.UNDER_REVIEW.name())) {
            throw new BizException(ErrorCode.ORDER_STATE_CONFLICT, "审核中的Listing不可编辑");
        }

        if (request.title() != null) listing.setTitle(request.title());
        if (request.synopsis() != null) listing.setSynopsis(request.synopsis());
        if (request.coverUrl() != null) listing.setCoverUrl(request.coverUrl());
        if (request.tagsJson() != null) listing.setTagsJson(request.tagsJson());
        if (request.charactersJson() != null) listing.setCharactersJson(request.charactersJson());
        if (request.episodeCount() != null) listing.setEpisodeCount(request.episodeCount());
        if (request.authorDisplayName() != null) listing.setAuthorDisplayName(request.authorDisplayName());
        if (request.previewEpisodeCount() != null) listing.setPreviewEpisodeCount(request.previewEpisodeCount());
        if (request.previewEpisodesJson() != null) listing.setPreviewEpisodesJson(request.previewEpisodesJson());
        listing.setReviewStatus(ListingStatus.DRAFT.name());

        listingMapper.updateById(listing);

        if (request.licenseOptions() != null) {
            // Replace license options for simplicity (existing options cascade-deleted logically)
            var existing = licenseOptionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ListingLicenseOption>()
                            .eq(ListingLicenseOption::getListingId, listingId));
            for (var e : existing) {
                licenseOptionMapper.deleteById(e.getId());
            }
            for (var opt : request.licenseOptions()) {
                ListingLicenseOption entity = new ListingLicenseOption();
                entity.setListingId(listingId);
                entity.setLicenseType(opt.licenseType());
                entity.setPriceCents(opt.priceCents());
                entity.setTermJson(opt.termJson());
                entity.setAgreementText(opt.agreementText());
                entity.setAgreementVersion(opt.agreementVersion());
                entity.setEnabled(1);
                licenseOptionMapper.insert(entity);
            }
        }

        return listing;
    }

    /**
     * Submit for platform review.
     */
    @Transactional
    public ScriptListing submit(WorkspaceContext ctx, Long listingId) {
        ScriptListing listing = requireOwnership(ctx, listingId);
        if (!listing.getReviewStatus().equals(ListingStatus.DRAFT.name())
                && !listing.getReviewStatus().equals(ListingStatus.REJECTED.name())) {
            throw new BizException(ErrorCode.ORDER_STATE_CONFLICT, "仅草稿或驳回状态可提交审核");
        }
        listing.setReviewStatus(ListingStatus.UNDER_REVIEW.name());
        listingMapper.updateById(listing);
        return listing;
    }

    /**
     * Withdraw a pending review listing.
     */
    @Transactional
    public ScriptListing withdraw(WorkspaceContext ctx, Long listingId) {
        ScriptListing listing = requireOwnership(ctx, listingId);
        if (!listing.getReviewStatus().equals(ListingStatus.UNDER_REVIEW.name())) {
            throw new BizException(ErrorCode.ORDER_STATE_CONFLICT, "仅审核中的Listing可撤回");
        }
        listing.setReviewStatus(ListingStatus.DRAFT.name());
        listingMapper.updateById(listing);
        return listing;
    }

    /**
     * Platform approves a listing.
     */
    @Transactional
    public ScriptListing approve(Long reviewerUserId, Long listingId, String reason) {
        ScriptListing listing = listingMapper.selectById(listingId);
        if (listing == null) {
            throw new BizException(ErrorCode.LISTING_NOT_AVAILABLE);
        }
        if (!listing.getReviewStatus().equals(ListingStatus.UNDER_REVIEW.name())) {
            throw new BizException(ErrorCode.ORDER_STATE_CONFLICT, "仅审核中的Listing可审批");
        }
        listing.setReviewStatus(ListingStatus.LISTED.name());
        listing.setListingStatus(ListingStatus.LISTED.name());
        listing.setReviewedBy(reviewerUserId);
        listing.setReviewedAt(LocalDateTime.now());
        listing.setReviewReason(reason);
        listing.setListedAt(LocalDateTime.now());
        listingMapper.updateById(listing);
        log.info("Listing approved: id={}, reviewer={}", listingId, reviewerUserId);
        return listing;
    }

    /**
     * Platform rejects a listing. Reason is mandatory.
     */
    @Transactional
    public ScriptListing reject(Long reviewerUserId, Long listingId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回必须提供原因");
        }
        ScriptListing listing = listingMapper.selectById(listingId);
        if (listing == null) {
            throw new BizException(ErrorCode.LISTING_NOT_AVAILABLE);
        }
        if (!listing.getReviewStatus().equals(ListingStatus.UNDER_REVIEW.name())) {
            throw new BizException(ErrorCode.ORDER_STATE_CONFLICT, "仅审核中的Listing可驳回");
        }
        listing.setReviewStatus(ListingStatus.REJECTED.name());
        listing.setReviewReason(reason);
        listing.setReviewedBy(reviewerUserId);
        listing.setReviewedAt(LocalDateTime.now());
        listingMapper.updateById(listing);
        log.info("Listing rejected: id={}, reviewer={}, reason={}", listingId, reviewerUserId, reason);
        return listing;
    }

    /**
     * Seller unlists a listing. Does not affect existing orders/entitlements.
     */
    @Transactional
    public ScriptListing unlist(WorkspaceContext ctx, Long listingId) {
        ScriptListing listing = requireOwnership(ctx, listingId);
        if (!listing.getListingStatus().equals(ListingStatus.LISTED.name())
                && !listing.getListingStatus().equals(ListingStatus.EXCLUSIVE_RESERVED.name())) {
            throw new BizException(ErrorCode.ORDER_STATE_CONFLICT, "仅在售或保留中的Listing可下架");
        }
        listing.setListingStatus(ListingStatus.UNLISTED.name());
        listing.setDelistedAt(LocalDateTime.now());
        listingMapper.updateById(listing);
        return listing;
    }

    /**
     * Get a listing by ID with ownership check.
     */
    public ScriptListing getOwned(WorkspaceContext ctx, Long listingId) {
        return requireOwnership(ctx, listingId);
    }

    /**
     * List all listings owned by a workspace.
     */
    public List<ScriptListing> listByWorkspace(String workspaceId) {
        return listingMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScriptListing>()
                        .eq(ScriptListing::getWorkspaceId, workspaceId)
                        .orderByDesc(ScriptListing::getUpdatedAt));
    }

    // -- helpers --

    private ScriptListing requireOwnership(WorkspaceContext ctx, Long listingId) {
        ScriptListing listing = listingMapper.selectById(listingId);
        if (listing == null) {
            throw new BizException(ErrorCode.LISTING_NOT_AVAILABLE);
        }
        if (!Objects.equals(listing.getWorkspaceId(), ctx.workspaceId())) {
            throw new BizException(ErrorCode.ASSET_PERMISSION_DENIED, "无权操作此Listing");
        }
        return listing;
    }
}
