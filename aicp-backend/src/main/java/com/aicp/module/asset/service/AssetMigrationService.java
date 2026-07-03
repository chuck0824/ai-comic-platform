package com.aicp.module.asset.service;

import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.entity.WorkspaceAssetFavorite;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetFavoriteMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetMapper;
import com.aicp.module.generation.entity.PlatformAsset;
import com.aicp.module.generation.mapper.PlatformAssetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resumable batch migration from {@code platform_assets} to
 * {@code workspace_assets + asset_versions}.
 *
 * Each batch is a standalone transaction. Rows already migrated
 * (matched via {@code legacy_platform_asset_id}) are skipped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetMigrationService {

    private final PlatformAssetMapper platformAssetMapper;
    private final WorkspaceAssetMapper workspaceAssetMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final WorkspaceAssetFavoriteMapper favoriteMapper;

    /** Max rows per batch (hard cap). */
    private static final int MAX_BATCH_SIZE = 500;

    /**
     * Result of one migration batch.
     */
    public record MigrationBatchResult(
            long scanned, long migrated, long skipped, long failed,
            Long lastPlatformAssetId, List<MigrationAnomaly> anomalies) {}

    public record MigrationAnomaly(Long platformAssetId, String code, String message) {}

    /**
     * Migrate the next batch starting from the given cursor (exclusive).
     * {@code limit} is capped at {@value #MAX_BATCH_SIZE}.
     */
    @Transactional
    public MigrationBatchResult migrateAfter(Long lastId, int limit) {
        int effectiveLimit = Math.min(limit, MAX_BATCH_SIZE);

        LambdaQueryWrapper<PlatformAsset> qw = new LambdaQueryWrapper<>();
        if (lastId != null) qw.gt(PlatformAsset::getId, lastId);
        qw.isNotNull(PlatformAsset::getOwnerUserId);
        qw.orderByAsc(PlatformAsset::getId);
        qw.last("LIMIT " + effectiveLimit);

        List<PlatformAsset> batch = platformAssetMapper.selectList(qw);
        if (batch.isEmpty()) {
            return new MigrationBatchResult(0, 0, 0, 0, lastId, List.of());
        }

        long scanned = batch.size();
        long migrated = 0, skipped = 0, failed = 0;
        Long lastProcessedId = null;
        List<MigrationAnomaly> anomalies = new ArrayList<>();

        for (PlatformAsset pa : batch) {
            lastProcessedId = pa.getId();
            try {
                // ── Idempotency: skip if already migrated ──
                Long existingCount = workspaceAssetMapper.selectCount(
                        new LambdaQueryWrapper<WorkspaceAsset>()
                                .eq(WorkspaceAsset::getLegacyPlatformAssetId, pa.getId()));
                if (existingCount != null && existingCount > 0) {
                    skipped++;
                    continue;
                }

                // ── Resolve workspace identity ──
                String workspaceId = "personal_" + pa.getOwnerUserId();

                // ── Build workspace_asset ──
                WorkspaceAsset asset = new WorkspaceAsset();
                asset.setUuid(pa.getUuid() != null ? pa.getUuid() : UUID.randomUUID().toString());
                asset.setWorkspaceId(workspaceId);
                asset.setWorkspaceType("personal");
                asset.setCreatorUserId(pa.getOwnerUserId());
                asset.setAssetType(normalizeAssetType(pa.getType()));
                asset.setName(pa.getName() != null ? pa.getName() : "未命名资产");
                asset.setSourceType("PROJECT_GENERATED");
                asset.setSourceNodeId(pa.getSourceNodeId());
                asset.setSourceTaskId(pa.getSourceTaskId());
                asset.setMediaType(normalizeMediaType(pa.getType()));
                asset.setStatus("ACTIVE");
                asset.setRowVersion(0);
                asset.setLegacyPlatformAssetId(pa.getId());
                asset.setTags(pa.getTags() != null ? pa.getTags() : "[]");
                asset.setCreatedBy(pa.getOwnerUserId());
                asset.setUpdatedBy(pa.getOwnerUserId());

                workspaceAssetMapper.insert(asset);

                // ── Build asset_version ──
                AssetVersion version = new AssetVersion();
                version.setAssetId(asset.getId());
                version.setVersionNumber(1);
                version.setSourceTaskId(pa.getSourceTaskId());
                version.setContentRef(pa.getFileUrl());
                version.setMimeType(normalizeMimeType(pa.getType()));
                version.setFileSize(pa.getFileSize());
                version.setWidth(pa.getWidth());
                version.setHeight(pa.getHeight());
                version.setDurationMs(pa.getDurationMs());
                version.setChecksum(null);
                version.setCreatedBy(pa.getOwnerUserId());

                assetVersionMapper.insert(version);

                // ── Link current version ──
                asset.setCurrentVersionId(version.getId());
                workspaceAssetMapper.updateById(asset);

                // ── Migrate favorite ──
                if (pa.getFavorite() != null && pa.getFavorite() == 1) {
                    WorkspaceAssetFavorite fav = new WorkspaceAssetFavorite();
                    fav.setUserId(pa.getOwnerUserId());
                    fav.setWorkspaceId(workspaceId);
                    fav.setAssetId(asset.getId());
                    try {
                        favoriteMapper.insert(fav);
                    } catch (Exception e) {
                        // duplicate favorite is harmless
                        log.debug("Favorite already exists for asset {}", asset.getId());
                    }
                }

                migrated++;
            } catch (Exception e) {
                failed++;
                anomalies.add(new MigrationAnomaly(
                        pa.getId(), "MIG-001", e.getMessage()));
                log.warn("Migration failed for platform_asset {}: {}", pa.getId(), e.getMessage());
            }
        }

        return new MigrationBatchResult(
                scanned, migrated, skipped, failed, lastProcessedId, anomalies);
    }

    private String normalizeAssetType(String type) {
        if (type == null) return "OTHER";
        return switch (type.toUpperCase()) {
            case "IMAGE", "IMAGE_GENERATED" -> "CHARACTER";
            case "VIDEO", "VIDEO_CLIP" -> "SCENE";
            case "AUDIO", "AUDIO_CLIP" -> "VOICE";
            case "PROMPT" -> "PROMPT";
            default -> "OTHER";
        };
    }

    private String normalizeMediaType(String type) {
        if (type == null) return "OTHER";
        return switch (type.toUpperCase()) {
            case "IMAGE", "IMAGE_GENERATED" -> "IMAGE";
            case "VIDEO", "VIDEO_CLIP" -> "VIDEO";
            case "AUDIO", "AUDIO_CLIP" -> "AUDIO";
            default -> "DATA";
        };
    }

    private String normalizeMimeType(String type) {
        if (type == null) return "application/octet-stream";
        return switch (type.toLowerCase()) {
            case "image", "image_generated" -> "image/png";
            case "video", "video_clip" -> "video/mp4";
            case "audio", "audio_clip" -> "audio/mpeg";
            default -> "application/octet-stream";
        };
    }
}
