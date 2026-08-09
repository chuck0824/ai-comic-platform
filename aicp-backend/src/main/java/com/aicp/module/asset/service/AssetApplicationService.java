package com.aicp.module.asset.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetRequests;
import com.aicp.module.asset.dto.AssetViews;
import com.aicp.module.asset.entity.*;
import com.aicp.module.asset.mapper.*;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetApplicationService {

    private final AssetLibraryService libraryService;
    private final WorkspaceAssetMapper assetMapper;
    private final AssetVersionMapper versionMapper;
    private final AssetApplicationMapper applicationMapper;
    private final CanvasProjectMapper projectMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public AssetViews.ApplyView apply(WorkspaceContext ctx, Long assetId, AssetRequests.ApplyAssetRequest req) {
        ctx.require("asset.use");
        WorkspaceAsset asset = libraryService.requireWorkspaceAsset(ctx, assetId);
        AssetVersion version = versionMapper.selectById(asset.getCurrentVersionId());
        if (version == null) throw new BizException(ErrorCode.ASSET_NOT_FOUND);

        // Check existing application (idempotency)
        AssetApplication existing = applicationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetApplication>()
                        .eq(AssetApplication::getWorkspaceId, ctx.workspaceId())
                        .eq(AssetApplication::getIdempotencyKey, req.idempotencyKey()));
        if (existing != null) {
            // Idempotent retry: return the stored result but NOT the raw undo token.
            // The client must use the token from the original response.
            return new AssetViews.ApplyView(existing.getId(),
                    null, // token not recoverable — client must use original
                    existing.getChangeSummary() + " (已应用)");
        }

        // Verify project belongs to the same workspace
        CanvasProject project = projectMapper.selectById(req.projectId());
        if (project == null || !ctx.workspaceId().equals(project.getWorkspaceId())) {
            throw new BizException(ErrorCode.ASSET_NOT_FOUND.getCode(), "项目不存在");
        }

        // Apply based on asset type
        String previousState;
        String changeSummary;
        String assetType = asset.getAssetType();

        switch (assetType) {
            case "CHECKPOINT", "LORA", "STYLE_PACK" -> {
                previousState = project.getStyleConfig();
                project.setStyleConfig(mergeStyleConfig(project.getStyleConfig(), version));
                changeSummary = "已将风格模型「" + asset.getName() + "」应用到项目";
            }
            case "CHARACTER", "SCENE" -> {
                previousState = project.getAppliedAssetIds();
                List<Long> ids = parseAssetIds(project.getAppliedAssetIds());
                ids.add(assetId);
                project.setAppliedAssetIds(toJson(ids));
                changeSummary = "已将" + ("CHARACTER".equals(assetType) ? "角色" : "场景")
                        + "「" + asset.getName() + "」添加到项目";
            }
            case "PROMPT" -> {
                previousState = project.getAppliedAssetIds();
                List<Long> ids = parseAssetIds(project.getAppliedAssetIds());
                ids.add(assetId);
                project.setAppliedAssetIds(toJson(ids));
                changeSummary = "已将提示词「" + asset.getName() + "」添加到项目";
            }
            default -> throw new BizException(ErrorCode.ASSET_TYPE_UNSUPPORTED);
        }

        projectMapper.updateById(project);

        // Generate undo token
        String undoToken = generateUndoToken();
        String undoTokenHash = sha256(undoToken);

        AssetApplication application = new AssetApplication();
        application.setWorkspaceId(ctx.workspaceId());
        application.setAssetId(assetId);
        application.setAssetVersionId(asset.getCurrentVersionId());
        application.setProjectId(req.projectId());
        application.setTargetType(req.targetType());
        application.setTargetId(req.targetId());
        application.setTargetKey(req.targetKey());
        application.setChangeSummary(changeSummary);
        application.setPreviousState(previousState);
        application.setUndoTokenHash(undoTokenHash);
        application.setAppliedBy(ctx.userId());
        application.setIdempotencyKey(req.idempotencyKey());
        application.setStatus("APPLIED");

        try {
            applicationMapper.insert(application);
        } catch (DuplicateKeyException e) {
            AssetApplication winner = applicationMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetApplication>()
                            .eq(AssetApplication::getWorkspaceId, ctx.workspaceId())
                            .eq(AssetApplication::getIdempotencyKey, req.idempotencyKey()));
            return new AssetViews.ApplyView(winner.getId(),
                    null, // race condition: original token not recoverable
                    winner.getChangeSummary() + " (并发重复)");
        }

        return new AssetViews.ApplyView(application.getId(), undoToken, changeSummary);
    }

    @Transactional
    public void undo(WorkspaceContext ctx, Long applicationId, AssetRequests.UndoRequest req) {
        AssetApplication application = applicationMapper.selectById(applicationId);
        if (application == null || !ctx.workspaceId().equals(application.getWorkspaceId())) {
            throw new BizException(ErrorCode.ASSET_NOT_FOUND);
        }
        if (!"APPLIED".equals(application.getStatus())) {
            throw new BizException(ErrorCode.PUBLISH_STATE_CONFLICT.getCode(), "该应用已撤销");
        }
        // Verify undo token
        if (!sha256(req.undoToken()).equals(application.getUndoTokenHash())) {
            throw new BizException(ErrorCode.ASSET_PERMISSION_DENIED.getCode(), "撤销令牌无效");
        }
        // Verify project row version
        CanvasProject project = projectMapper.selectById(application.getProjectId());
        if (project == null || !project.getCanvasVersion().equals(req.projectRowVersion())) {
            throw new BizException(ErrorCode.ASSET_VERSION_CONFLICT.getCode(), "项目已被修改，无法撤销");
        }

        // Restore previous state
        WorkspaceAsset asset = assetMapper.selectById(application.getAssetId());
        String assetType = asset != null ? asset.getAssetType() : "STYLE_PACK";
        switch (assetType) {
            case "CHECKPOINT", "LORA", "STYLE_PACK" ->
                project.setStyleConfig(application.getPreviousState());
            default -> project.setAppliedAssetIds(application.getPreviousState());
        }
        projectMapper.updateById(project);

        application.setStatus("UNDONE");
        applicationMapper.updateById(application);
        log.info("Application undone: id={}, project={}", applicationId, application.getProjectId());
    }

    private String mergeStyleConfig(String currentConfig, AssetVersion version) {
        // Simple merge: if current is empty, use version metadata; otherwise append
        if (currentConfig == null || currentConfig.isEmpty() || "null".equals(currentConfig)) {
            return version.getMetadata();
        }
        return currentConfig; // Keep existing, version info is in the application record
    }

    private String generateUndoToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseAssetIds(String json) {
        if (json == null || json.isEmpty() || "[]".equals(json)) return new ArrayList<>();
        try { return objectMapper.readValue(json, List.class); } catch (Exception e) { return new ArrayList<>(); }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "[]"; }
    }
}
