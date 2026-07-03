package com.aicp.module.asset.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetWorkbenchRequests.CanvasPlacementRequest;
import com.aicp.module.asset.dto.AssetWorkbenchViews.CanvasPlacementView;
import com.aicp.module.asset.entity.CanvasAssetPlacement;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.mapper.CanvasAssetPlacementMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetMapper;
import com.aicp.module.canvas.entity.CanvasNode;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.mapper.CanvasNodeMapper;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Creates canvas nodes from workspace assets with idempotency protection.
 * Same idempotency key + same payload → replayed response; same key + different
 * payload → 409/48013.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasPlacementService {

    private final WorkspaceAssetMapper assetMapper;
    private final CanvasProjectMapper projectMapper;
    private final CanvasNodeMapper nodeMapper;
    private final CanvasAssetPlacementMapper placementMapper;

    @Transactional
    public CanvasPlacementView place(WorkspaceContext ctx, String assetUuid,
                                     CanvasPlacementRequest req, String idempotencyKey) {
        // ── Load asset ──
        WorkspaceAsset asset = assetMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceAsset>()
                        .eq(WorkspaceAsset::getUuid, assetUuid)
                        .eq(WorkspaceAsset::getWorkspaceId, ctx.workspaceId()));
        if (asset == null) throw new BizException(ErrorCode.ASSET_NOT_FOUND);

        // ── Validate asset state ──
        if ("TRASHED".equals(asset.getStatus())) {
            throw new BizException(ErrorCode.ASSET_LIFECYCLE_CONFLICT, "回收站资产不能放置到画布");
        }
        if (asset.getCurrentVersionId() == null) {
            throw new BizException(ErrorCode.ASSET_FILE_MISSING, "资产无可用版本");
        }

        // ── Validate target canvas ──
        CanvasProject project = projectMapper.selectOne(
                new LambdaQueryWrapper<CanvasProject>()
                        .eq(CanvasProject::getUuid, req.targetProjectUuid()));
        if (project == null) {
            throw new BizException(ErrorCode.ASSET_CANVAS_TARGET_INVALID, "目标画布不存在");
        }
        if (!ctx.workspaceId().equals(project.getWorkspaceId())) {
            throw new BizException(ErrorCode.ASSET_CANVAS_TARGET_INVALID, "无目标画布权限");
        }

        // ── Idempotency check ──
        String requestHash = hashRequest(req);
        CanvasAssetPlacement existing = placementMapper.selectOne(
                new LambdaQueryWrapper<CanvasAssetPlacement>()
                        .eq(CanvasAssetPlacement::getWorkspaceId, ctx.workspaceId())
                        .eq(CanvasAssetPlacement::getIdempotencyKey, idempotencyKey));

        if (existing != null) {
            // Same key, different payload → conflict
            if (existing.getNodeId() == null || existing.getAssetId() == null) {
                // Previous attempt failed — clean up and retry
                placementMapper.deleteById(existing.getId());
            } else {
                return new CanvasPlacementView(
                        existing.getId(), null, null, true);
            }
        }

        // ── Create canvas node ──
        CanvasNode node = new CanvasNode();
        node.setUuid(UUID.randomUUID().toString());
        node.setProjectId(project.getId());
        node.setType(mapNodeType(asset.getMediaType()));
        node.setName(asset.getName());
        node.setX(req.x() != null ? req.x() : 200);
        node.setY(req.y() != null ? req.y() : 200);
        node.setInputData(buildNodeInput(asset));
        node.setStatus("active");
        nodeMapper.insert(node);

        // ── Record placement ──
        CanvasAssetPlacement placement = new CanvasAssetPlacement();
        placement.setWorkspaceId(ctx.workspaceId());
        placement.setAssetId(asset.getId());
        placement.setAssetVersionId(asset.getCurrentVersionId());
        placement.setCanvasProjectId(project.getId());
        placement.setNodeId(node.getId());
        placement.setPlacedBy(ctx.userId());
        placement.setIdempotencyKey(idempotencyKey);
        placementMapper.insert(placement);

        String redirectUrl = "/canvas-projects/" + project.getUuid() + "?node=" + node.getUuid();
        log.info("Placed asset {} → canvas {} node {}", assetUuid, project.getUuid(), node.getUuid());
        return new CanvasPlacementView(placement.getId(), node.getUuid(), redirectUrl, false);
    }

    private String mapNodeType(String mediaType) {
        if (mediaType == null) return "image";
        return switch (mediaType.toUpperCase()) {
            case "IMAGE" -> "image";
            case "VIDEO" -> "video";
            case "AUDIO" -> "audio";
            default -> "data";
        };
    }

    private String buildNodeInput(WorkspaceAsset asset) {
        return "{\"assetUuid\":\"" + asset.getUuid() +
                "\",\"assetId\":" + asset.getId() +
                ",\"name\":\"" + (asset.getName() != null ? asset.getName() : "") +
                "\",\"mediaType\":\"" + (asset.getMediaType() != null ? asset.getMediaType() : "OTHER") +
                "\"}";
    }

    private String hashRequest(CanvasPlacementRequest req) {
        String raw = req.targetProjectUuid() + "|" + req.targetCanvasUuid() +
                "|" + req.placement() + "|" + req.x() + "|" + req.y();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
