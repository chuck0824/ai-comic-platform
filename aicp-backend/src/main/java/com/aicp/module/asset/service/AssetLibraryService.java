package com.aicp.module.asset.service;

import com.aicp.common.dto.PageResult;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetRequests;
import com.aicp.module.asset.dto.AssetViews;
import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetLibraryService {

    private final WorkspaceAssetMapper assetMapper;
    private final AssetVersionMapper versionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PageResult<AssetViews.AssetView> listLibrary(WorkspaceContext ctx, String assetType,
                                                         String sourceType, int page, int pageSize) {
        LambdaQueryWrapper<WorkspaceAsset> qw = new LambdaQueryWrapper<>();
        qw.eq(WorkspaceAsset::getWorkspaceId, ctx.workspaceId());
        // Project-owned scene masters are only available through the project API,
        // where ProjectAccessService verifies membership.
        qw.isNull(WorkspaceAsset::getContentProjectId);
        qw.ne(WorkspaceAsset::getStatus, "ARCHIVED");
        if (assetType != null) qw.eq(WorkspaceAsset::getAssetType, assetType);
        if (sourceType != null) qw.eq(WorkspaceAsset::getSourceType, sourceType);
        qw.orderByDesc(WorkspaceAsset::getUpdatedAt);

        Page<WorkspaceAsset> mpPage = new Page<>(page, Math.min(pageSize, 50));
        Page<WorkspaceAsset> result = assetMapper.selectPage(mpPage, qw);

        List<AssetViews.AssetView> views = result.getRecords().stream().map(this::toAssetView).toList();
        return PageResult.of(views, page, Math.min(pageSize, 50), result.getTotal());
    }

    public AssetViews.AssetView getAsset(WorkspaceContext ctx, Long assetId) {
        WorkspaceAsset asset = requireWorkspaceAsset(ctx, assetId);
        return toAssetView(asset);
    }

    @Transactional
    public AssetViews.AssetView create(WorkspaceContext ctx, AssetRequests.CreateAssetRequest req) {
        ctx.require("asset.manage");
        WorkspaceAsset asset = new WorkspaceAsset();
        asset.setUuid(UUID.randomUUID().toString());
        asset.setWorkspaceId(ctx.workspaceId());
        asset.setWorkspaceType(ctx.workspaceType());
        asset.setCreatorUserId(ctx.userId());
        asset.setAssetType(req.assetType());
        asset.setName(req.name());
        asset.setDescription(req.description());
        asset.setTags(toJson(req.tags()));
        asset.setAccessScope(req.accessScope());
        asset.setSourceType("CREATED");
        asset.setStatus("ACTIVE");
        asset.setRowVersion(0);
        asset.setCreatedBy(ctx.userId());
        asset.setUpdatedBy(ctx.userId());
        assetMapper.insert(asset);
        log.info("Asset created: id={}, workspace={}, type={}", asset.getId(), ctx.workspaceId(), req.assetType());
        return toAssetView(asset);
    }

    @Transactional
    public AssetViews.AssetView edit(WorkspaceContext ctx, Long assetId, AssetRequests.EditAssetRequest req) {
        ctx.require("asset.manage");
        WorkspaceAsset asset = requireWorkspaceAsset(ctx, assetId);
        if (!asset.getRowVersion().equals(req.rowVersion())) {
            throw new BizException(ErrorCode.ASSET_VERSION_CONFLICT);
        }
        if (req.name() != null) asset.setName(req.name());
        if (req.description() != null) asset.setDescription(req.description());
        if (req.tags() != null) asset.setTags(toJson(req.tags()));
        asset.setRowVersion(asset.getRowVersion() + 1);
        asset.setUpdatedBy(ctx.userId());
        assetMapper.updateById(asset);
        return toAssetView(asset);
    }

    @Transactional
    public AssetViews.VersionView createVersion(WorkspaceContext ctx, Long assetId, AssetRequests.CreateVersionRequest req) {
        ctx.require("asset.manage");
        WorkspaceAsset asset = requireWorkspaceAsset(ctx, assetId);
        AssetVersion version = new AssetVersion();
        version.setAssetId(assetId);
        version.setMetadata(req.metadata());
        version.setPreviewUrl(req.previewUrl());
        version.setCreatedBy(ctx.userId());

        // Determine version number
        Long count = versionMapper.selectCount(
                new LambdaQueryWrapper<AssetVersion>().eq(AssetVersion::getAssetId, assetId));
        version.setVersionNumber(count.intValue() + 1);

        versionMapper.insert(version);
        asset.setCurrentVersionId(version.getId());
        asset.setUpdatedBy(ctx.userId());
        assetMapper.updateById(asset);

        return new AssetViews.VersionView(version.getId(), version.getAssetId(),
                version.getVersionNumber(), parseJson(version.getMetadata()),
                version.getPreviewUrl(), version.getCreatedBy(), version.getCreatedAt());
    }

    @Transactional
    public void archive(WorkspaceContext ctx, Long assetId, Integer rowVersion) {
        ctx.require("asset.delete");
        WorkspaceAsset asset = requireWorkspaceAsset(ctx, assetId);
        if (!asset.getRowVersion().equals(rowVersion)) {
            throw new BizException(ErrorCode.ASSET_VERSION_CONFLICT);
        }
        asset.setStatus("ARCHIVED");
        asset.setRowVersion(asset.getRowVersion() + 1);
        asset.setUpdatedBy(ctx.userId());
        assetMapper.updateById(asset);
    }

    // ---- helpers ----

    WorkspaceAsset requireWorkspaceAsset(WorkspaceContext ctx, Long assetId) {
        WorkspaceAsset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.getContentProjectId() != null
                || !ctx.workspaceId().equals(asset.getWorkspaceId())) {
            throw new BizException(ErrorCode.ASSET_NOT_FOUND);
        }
        return asset;
    }

    private AssetViews.AssetView toAssetView(WorkspaceAsset a) {
        return new AssetViews.AssetView(a.getId(), a.getUuid(), a.getWorkspaceId(),
                a.getWorkspaceType(), a.getCreatorUserId(), a.getAssetType(), a.getName(),
                a.getDescription(), parseTags(a.getTags()), a.getAccessScope(), a.getSourceType(),
                a.getSourceListingId(), a.getCurrentVersionId(), a.getContentProjectId(), a.getStatus(),
                a.getRowVersion(), a.getCreatedAt(), a.getUpdatedAt());
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "[]"; }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTags(String json) {
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); } catch (Exception e) { return List.of(); }
    }

    private Object parseJson(String json) {
        try { return objectMapper.readValue(json, Object.class); } catch (Exception e) { return json; }
    }
}
