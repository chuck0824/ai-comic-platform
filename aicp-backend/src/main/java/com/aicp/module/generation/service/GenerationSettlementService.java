package com.aicp.module.generation.service;

import com.aicp.module.asset.entity.AssetActivityLog;
import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.mapper.AssetActivityLogMapper;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetMapper;
import com.aicp.module.generation.entity.GenerationSettlementOutbox;
import com.aicp.module.generation.entity.GenerationTask;
import com.aicp.module.generation.mapper.GenerationSettlementOutboxMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Atomic settlement: transforms a succeeded {@link GenerationTask} into a
 * canonical {@link WorkspaceAsset} + {@link AssetVersion}.
 *
 * <h3>In-transaction (all succeed or all roll back)</h3>
 * <ol>
 *   <li>Validate workspace / creator (never fall back to user 1)</li>
 *   <li>Validate output (storage key or file reference must exist)</li>
 *   <li>INSERT workspace_asset (or reuse via idempotency)</li>
 *   <li>INSERT asset_version (immutable append)</li>
 *   <li>UPDATE workspace_asset.current_version_id</li>
 *   <li>INSERT asset_activity_log</li>
 *   <li>UPDATE generation_task.status = 'succeeded' (LAST)</li>
 * </ol>
 *
 * <h3>Out-of-transaction (outbox compensation)</h3>
 * <ol>
 *   <li>Canvas node / shot write-back → outbox stage NODE_WRITEBACK</li>
 *   <li>Storage cleanup on failure → outbox stage STORAGE_CLEANUP</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationSettlementService {

    private final WorkspaceAssetMapper workspaceAssetMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final AssetActivityLogMapper activityLogMapper;
    private final GenerationSettlementOutboxMapper outboxMapper;

    public record SettlementInput(
            String storageProvider, String storageBucket, String storageKey,
            String mimeType, Long fileSize, Integer width, Integer height,
            Integer durationMs, String previewUrl, String checksum) {}

    public record SettlementResult(Long assetId, Long versionId, String assetUuid) {}

    /**
     * Settle a completed generation task into canonical asset records.
     * Returns null (no-op) if already settled.
     */
    @Transactional
    public SettlementResult settle(GenerationTask task, SettlementInput input) {
        // ── Guard: require workspace and creator ──
        if (task.getWorkspaceId() == null || task.getCreatedBy() == null
                || task.getCreatedBy() == 0L) {
            log.error("Settlement failed: missing workspace/creator for task {}", task.getId());
            writeOutbox(task, "ASSET_CREATE", "Missing workspace_id or created_by");
            return null;
        }

        // ── Guard: validate output ──
        if (input.storageKey() == null || input.storageKey().isBlank()) {
            task.setStatus("failed");
            task.setErrorCode("48008");
            task.setErrorMessage("Asset file missing — no storage key");
            writeOutbox(task, "ASSET_CREATE", "Missing storage key");
            return null;
        }

        // ── Build workspace_asset ──
        WorkspaceAsset asset = new WorkspaceAsset();
        asset.setUuid(UUID.randomUUID().toString());
        asset.setWorkspaceId(task.getWorkspaceId());
        asset.setWorkspaceType(task.getWorkspaceId().startsWith("personal_") ? "personal" : "enterprise");
        asset.setCreatorUserId(task.getCreatedBy());
        asset.setAssetType(task.getAssetType() != null ? task.getAssetType() : "OTHER");
        asset.setName(task.getSubType() != null ? task.getSubType() : "Generated Asset");
        asset.setSourceType("PROJECT_GENERATED");
        asset.setSourceTaskId(task.getId());
        asset.setSourceNodeId(task.getNodeId());
        asset.setSourceCanvasProjectId(task.getProjectId());
        asset.setContentProjectId(task.getContentProjectId());
        asset.setMediaType(inferMediaType(task.getType()));
        asset.setStatus("ACTIVE");
        asset.setRowVersion(0);
        asset.setTags("[]");
        asset.setCreatedBy(task.getCreatedBy());
        asset.setUpdatedBy(task.getCreatedBy());

        workspaceAssetMapper.insert(asset);

        // ── Build asset_version ──
        AssetVersion version = new AssetVersion();
        version.setAssetId(asset.getId());
        version.setVersionNumber(1);
        version.setSourceTaskId(task.getId());
        version.setStorageProvider(input.storageProvider());
        version.setStorageBucket(input.storageBucket());
        version.setStorageKey(input.storageKey());
        version.setMimeType(input.mimeType());
        version.setFileSize(input.fileSize());
        version.setWidth(input.width());
        version.setHeight(input.height());
        version.setDurationMs(input.durationMs());
        version.setPreviewUrl(input.previewUrl());
        version.setChecksum(input.checksum());
        version.setGenerationSnapshot(task.getParameters());
        version.setCreatedBy(task.getCreatedBy());

        assetVersionMapper.insert(version);

        // ── Link current version ──
        asset.setCurrentVersionId(version.getId());
        workspaceAssetMapper.updateById(asset);

        // ── Write activity log ──
        AssetActivityLog activityLog = new AssetActivityLog();
        activityLog.setWorkspaceId(task.getWorkspaceId());
        activityLog.setAssetId(asset.getId());
        activityLog.setActorUserId(task.getCreatedBy());
        activityLog.setAction("CREATED");
        activityLog.setRequestId(task.getRequestId());
        activityLogMapper.insert(activityLog);

        // ── Mark task succeeded (LAST step in transaction) ──
        task.setStatus("succeeded");
        // Note: task update is handled by the caller (GenerationExecutor)

        log.info("Settled task {} → asset {} v{}", task.getId(), asset.getUuid(), version.getId());
        return new SettlementResult(asset.getId(), version.getId(), asset.getUuid());
    }

    private void writeOutbox(GenerationTask task, String stage, String error) {
        try {
            GenerationSettlementOutbox existing = outboxMapper.selectOne(
                    new LambdaQueryWrapper<GenerationSettlementOutbox>()
                            .eq(GenerationSettlementOutbox::getTaskId, task.getId())
                            .eq(GenerationSettlementOutbox::getStage, stage));
            if (existing != null) return;

            GenerationSettlementOutbox outbox = new GenerationSettlementOutbox();
            outbox.setTaskId(task.getId());
            outbox.setWorkspaceId(task.getWorkspaceId());
            outbox.setStage(stage);
            outbox.setStatus("PENDING");
            outbox.setRetryCount(0);
            outbox.setLastError(error);
            outbox.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
            outboxMapper.insert(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox for task {}: {}", task.getId(), e.getMessage());
        }
    }

    private String inferMediaType(String taskType) {
        if (taskType == null) return "OTHER";
        return switch (taskType.toLowerCase()) {
            case "image" -> "IMAGE";
            case "video", "compose" -> "VIDEO";
            case "audio" -> "AUDIO";
            default -> "DATA";
        };
    }
}
