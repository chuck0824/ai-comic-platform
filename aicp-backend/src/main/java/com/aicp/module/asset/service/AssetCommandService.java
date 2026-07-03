package com.aicp.module.asset.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetWorkbenchRequests.*;
import com.aicp.module.asset.dto.AssetWorkbenchViews.BatchResult;
import com.aicp.module.asset.entity.*;
import com.aicp.module.asset.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetCommandService {

    private final WorkspaceAssetMapper assetMapper;
    private final WorkspaceAssetFavoriteMapper favoriteMapper;
    private final AssetActivityLogMapper activityLogMapper;
    private final AssetCommandIdempotencyMapper idempotencyMapper;

    @Transactional
    public WorkspaceAsset edit(WorkspaceContext ctx, String assetUuid, EditAssetRequest req) {
        WorkspaceAsset asset = requireAsset(ctx, assetUuid);
        if (req.rowVersion() != null && !req.rowVersion().equals(asset.getRowVersion())) {
            throw new BizException(ErrorCode.ASSET_VERSION_CONFLICT);
        }
        if (req.name() != null) asset.setName(req.name());
        if (req.assetType() != null) asset.setAssetType(req.assetType());
        if (req.tags() != null) asset.setTags(req.tags());
        asset.setRowVersion(asset.getRowVersion() + 1);
        assetMapper.updateById(asset);

        writeActivity(ctx, asset.getId(), "EDIT", null, "name=" + req.name());
        return asset;
    }

    @Transactional
    public void toggleFavorite(WorkspaceContext ctx, String assetUuid, boolean favorite) {
        WorkspaceAsset asset = requireAsset(ctx, assetUuid);
        var existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceAssetFavorite>()
                        .eq(WorkspaceAssetFavorite::getWorkspaceId, ctx.workspaceId())
                        .eq(WorkspaceAssetFavorite::getUserId, ctx.userId())
                        .eq(WorkspaceAssetFavorite::getAssetId, asset.getId()));

        if (favorite && existing == null) {
            WorkspaceAssetFavorite fav = new WorkspaceAssetFavorite();
            fav.setUserId(ctx.userId());
            fav.setWorkspaceId(ctx.workspaceId());
            fav.setAssetId(asset.getId());
            favoriteMapper.insert(fav);
        } else if (!favorite && existing != null) {
            favoriteMapper.deleteById(existing.getId());
        }
    }

    @Transactional
    public BatchResult batchOperate(WorkspaceContext ctx, BatchAssetRequest req) {
        int succeeded = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        for (String uuid : req.assetUuids()) {
            try {
                WorkspaceAsset asset = requireAsset(ctx, uuid);
                switch (req.operation().toUpperCase()) {
                    case "TRASH" -> trashOne(asset, ctx);
                    case "RESTORE" -> restoreOne(asset, ctx);
                    default -> throw new BizException(ErrorCode.PARAM_INVALID);
                }
                succeeded++;
            } catch (BizException e) {
                failed++;
                errors.add(uuid + ": " + e.getMessage());
            }
        }
        return new BatchResult(succeeded, failed, errors);
    }

    private void trashOne(WorkspaceAsset asset, WorkspaceContext ctx) {
        if ("TRASHED".equals(asset.getStatus())) return;
        asset.setStatus("TRASHED");
        asset.setDeletedAt(LocalDateTime.now());
        asset.setDeletedBy(ctx.userId());
        asset.setPurgeAt(LocalDateTime.now().plusDays(30));
        assetMapper.updateById(asset);
        writeActivity(ctx, asset.getId(), "TRASH", "ACTIVE", "TRASHED");
    }

    private void restoreOne(WorkspaceAsset asset, WorkspaceContext ctx) {
        if (!"TRASHED".equals(asset.getStatus())) {
            throw new BizException(ErrorCode.ASSET_LIFECYCLE_CONFLICT);
        }
        asset.setStatus("ACTIVE");
        asset.setDeletedAt(null);
        asset.setDeletedBy(null);
        asset.setPurgeAt(null);
        assetMapper.updateById(asset);
        writeActivity(ctx, asset.getId(), "RESTORE", "TRASHED", "ACTIVE");
    }

    private WorkspaceAsset requireAsset(WorkspaceContext ctx, String uuid) {
        WorkspaceAsset asset = assetMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceAsset>()
                        .eq(WorkspaceAsset::getUuid, uuid)
                        .eq(WorkspaceAsset::getWorkspaceId, ctx.workspaceId()));
        if (asset == null) throw new BizException(ErrorCode.ASSET_NOT_FOUND);
        return asset;
    }

    private void writeActivity(WorkspaceContext ctx, Long assetId, String action, String before, String after) {
        AssetActivityLog l = new AssetActivityLog();
        l.setWorkspaceId(ctx.workspaceId());
        l.setAssetId(assetId);
        l.setActorUserId(ctx.userId());
        l.setAction(action);
        l.setBeforeData(before);
        l.setAfterData(after);
        activityLogMapper.insert(l);
    }
}
