package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.asset.entity.AssetApplication;
import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.entity.CanvasAssetPlacement;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.mapper.AssetApplicationMapper;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.asset.mapper.CanvasAssetPlacementMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetMapper;
import com.aicp.module.canvas.entity.CanvasNode;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.mapper.CanvasNodeMapper;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.CreateSceneAssetRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.CreateSceneVariantRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.CreateVariantRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.FromWorldLocationRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.RestoreSceneAssetRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.UpdateSceneVariantRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetRequests.UpdateSceneAssetRequest;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.ImpactReferenceView;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetImpactView;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetMarkdownView;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetVersionView;
import com.aicp.module.contentproject.dto.ProjectSceneAssetViews.SceneAssetView;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aicp.module.storyboard.entity.Storyboard;
import com.aicp.module.storyboard.entity.StoryboardShot;
import com.aicp.module.storyboard.entity.StoryboardVersion;
import com.aicp.module.storyboard.mapper.StoryboardMapper;
import com.aicp.module.storyboard.mapper.StoryboardVersionMapper;
import com.aicp.module.storyboard.mapper.StoryboardVersionShotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Owns the project-only scene-master lifecycle on top of workspace_assets/asset_versions. */
@Service
@RequiredArgsConstructor
public class ProjectSceneAssetService {

    private static final String SCENE = "SCENE";
    private static final String PROJECT_GENERATED = "PROJECT_GENERATED";

    private final WorkspaceAssetMapper assetMapper;
    private final AssetVersionMapper versionMapper;
    private final AssetApplicationMapper applicationMapper;
    private final CanvasAssetPlacementMapper placementMapper;
    private final ContentUnitMapper contentUnitMapper;
    private final StoryboardMapper storyboardMapper;
    private final StoryboardVersionMapper storyboardVersionMapper;
    private final StoryboardVersionShotMapper storyboardVersionShotMapper;
    private final CanvasNodeMapper canvasNodeMapper;
    private final CanvasProjectMapper canvasProjectMapper;
    private final ProjectAccessService projectAccess;
    private final ObjectMapper objectMapper;
    private final SceneAssetMarkdownProjector markdownProjector;

    public List<SceneAssetView> list(Long userId, Long projectId, String keyword, String spaceType,
                                     String reusability, String status, Boolean referenced) {
        projectAccess.require(projectId, userId, Action.VIEW);
        LambdaQueryWrapper<WorkspaceAsset> query = sceneQuery(projectId);
        if (keyword != null && !keyword.isBlank()) query.like(WorkspaceAsset::getName, keyword.trim());
        if (status != null && !status.isBlank()) query.eq(WorkspaceAsset::getStatus, status.trim());
        return assetMapper.selectList(query.orderByDesc(WorkspaceAsset::getUpdatedAt)).stream()
                .map(this::toView)
                .filter(view -> spaceType == null || spaceType.equals(view.master().get("space_type")))
                .filter(view -> reusability == null || reusability.equals(view.master().get("reusability")))
                .filter(view -> referenced == null || referenced == (impactFor(view.id()).lockedReferences() > 0))
                .toList();
    }

    @Transactional
    public SceneAssetView create(Long userId, Long projectId, CreateSceneAssetRequest request) {
        projectAccess.require(projectId, userId, Action.EDIT_CONTENT);
        WorkspaceAsset asset = new WorkspaceAsset();
        asset.setUuid(UUID.randomUUID().toString());
        asset.setWorkspaceId(projectWorkspaceId(projectId));
        asset.setWorkspaceType("project");
        asset.setCreatorUserId(userId);
        asset.setAssetType(SCENE);
        asset.setName(request.name());
        asset.setAccessScope("PRIVATE");
        asset.setSourceType(PROJECT_GENERATED);
        asset.setContentProjectId(projectId);
        asset.setMediaType("DATA");
        asset.setStatus("ACTIVE");
        asset.setRowVersion(0);
        asset.setCreatedBy(userId);
        asset.setUpdatedBy(userId);
        assetMapper.insert(asset);

        appendVersion(asset, metadataFor(request, projectId), userId);
        return toView(requireScene(projectId, asset.getId()));
    }

    /** Converts a world-location reference once; subsequent calls return the same project scene. */
    @Transactional
    public SceneAssetView fromLocation(Long userId, Long projectId, FromWorldLocationRequest request) {
        projectAccess.require(projectId, userId, Action.EDIT_CONTENT);
        WorkspaceAsset existing = assetMapper.selectList(sceneQuery(projectId).orderByAsc(WorkspaceAsset::getId))
                .stream()
                .filter(asset -> request.worldLocationRef().equals(master(currentMetadata(asset)).get("world_location_ref")))
                .findFirst().orElse(null);
        if (existing != null) return toView(existing);
        return create(userId, projectId, new CreateSceneAssetRequest(request.name(),
                defaulted(request.spaceType(), "INTERIOR"), defaulted(request.reusability(), "PRIMARY"),
                defaulted(request.realityType(), "REALISTIC"), request.worldLocationRef(), request.layout(), null,
                null, request.lighting(), null, null, null, null, List.of(), null, null, List.of()));
    }

    public SceneAssetView get(Long userId, Long projectId, Long assetId) {
        projectAccess.require(projectId, userId, Action.VIEW);
        return toView(requireScene(projectId, assetId));
    }

    @Transactional
    public SceneAssetView update(Long userId, Long projectId, Long assetId, UpdateSceneAssetRequest request) {
        projectAccess.require(projectId, userId, Action.EDIT_CONTENT);
        WorkspaceAsset asset = requireScene(projectId, assetId);
        requireUpdatePayload(request);
        AssetVersion previousVersion = requireCurrentVersion(asset);
        Map<String, Object> metadata = currentMetadata(asset);
        Map<String, Object> master = master(metadata);
        merge(master, request);
        metadata.put("schema_version", 1);
        metadata.put("master", master);
        String nextName = request.name() == null ? asset.getName() : request.name();
        String nextMetadata = writeMetadata(metadata);
        if (nextName.equals(asset.getName()) && nextMetadata.equals(previousVersion.getMetadata())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "场景资产更新未包含任何有效变更");
        }
        asset.setName(nextName);
        appendVersion(asset, metadata, userId);
        return toView(requireScene(projectId, assetId));
    }

    @Transactional
    public SceneAssetView createVariant(Long userId, Long projectId, Long assetId, CreateSceneVariantRequest request) {
        projectAccess.require(projectId, userId, Action.EDIT_CONTENT);
        WorkspaceAsset asset = requireScene(projectId, assetId);
        Map<String, Object> metadata = currentMetadata(asset);
        List<Map<String, Object>> variants = new ArrayList<>(variantsFrom(metadata));
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("id", stableVariantId(nextVariantSequence(asset.getId())));
        variant.put("version", 1);
        variant.put("name", request.name());
        put(variant, "time", request.time());
        put(variant, "lighting_delta", request.lightingDelta());
        put(variant, "prompts", request.prompts());
        put(variant, "references", request.references());
        variants.add(variant);
        metadata.put("variants", variants);
        appendVersion(asset, metadata, userId);
        return toView(requireScene(projectId, assetId));
    }

    @Transactional
    public SceneAssetView updateVariant(Long userId, Long projectId, Long assetId, String variantId,
                                        UpdateSceneVariantRequest request) {
        projectAccess.require(projectId, userId, Action.EDIT_CONTENT);
        requireVariantPayload(request);
        WorkspaceAsset asset = requireScene(projectId, assetId);
        Map<String, Object> metadata = currentMetadata(asset);
        List<Map<String, Object>> variants = new ArrayList<>(variantsFrom(metadata));
        Map<String, Object> variant = variants.stream()
                .filter(item -> variantId.equals(item.get("id"))).findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.ASSET_NOT_FOUND, "场景变体不存在"));
        Map<String, Object> updated = new LinkedHashMap<>(variant);
        put(updated, "name", request.name());
        put(updated, "time", request.time());
        put(updated, "lighting_delta", request.lightingDelta());
        put(updated, "prompts", request.prompts());
        put(updated, "references", request.references());
        if (updated.equals(variant)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "场景变体更新未包含任何有效变更");
        }
        updated.put("version", variantVersion(variant) + 1);
        variants.set(variants.indexOf(variant), updated);
        metadata.put("variants", variants);
        appendVersion(asset, metadata, userId);
        return toView(requireScene(projectId, assetId));
    }

    public SceneAssetMarkdownView markdown(Long userId, Long projectId, Long assetId) {
        projectAccess.require(projectId, userId, Action.VIEW);
        WorkspaceAsset asset = requireScene(projectId, assetId);
        AssetVersion version = requireCurrentVersion(asset);
        return markdownProjector.project(projectId, asset, version, currentMetadata(asset), trustedLinks(projectId, assetId));
    }

    @Transactional
    public SceneAssetVersionView restore(Long userId, Long projectId, Long assetId, Long versionId,
                                         RestoreSceneAssetRequest request) {
        projectAccess.require(projectId, userId, Action.EDIT_CONTENT);
        WorkspaceAsset asset = requireScene(projectId, assetId);
        AssetVersion historical = versionMapper.selectOne(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getId, versionId).eq(AssetVersion::getAssetId, assetId));
        if (historical == null) throw new BizException(ErrorCode.ASSET_NOT_FOUND, "场景资产版本不存在");
        requireCurrentVersion(asset);
        Map<String, Object> restoredMetadata = parseMetadata(historical.getMetadata());
        AssetVersion restored = appendVersion(asset, restoredMetadata, userId);
        return toVersionView(restored, request == null ? null : request.changeNote());
    }

    @Transactional
    public void archive(Long userId, Long projectId, Long assetId) {
        projectAccess.require(projectId, userId, Action.EDIT_CONTENT);
        WorkspaceAsset asset = requireScene(projectId, assetId);
        SceneAssetImpactView impact = impactFor(assetId);
        if (impact.lockedReferences() > 0) {
            throw new BizException(ErrorCode.ASSET_LIFECYCLE_CONFLICT,
                    "场景资产仍被锁定引用，请先替换或停用引用");
        }
        asset.setStatus("ARCHIVED");
        asset.setUpdatedBy(userId);
        assetMapper.updateById(asset);
    }

    public SceneAssetImpactView impact(Long userId, Long projectId, Long assetId) {
        projectAccess.require(projectId, userId, Action.VIEW);
        requireScene(projectId, assetId);
        return impactFor(assetId);
    }

    /** Resolves and freezes one historical scene master + variant for a V2 storyboard shot. */
    public ResolvedSceneBinding resolveStoryboardSnapshot(Long userId, Long projectId, Long assetId,
                                                           Long assetVersionId, String variantId,
                                                           Integer variantVersion,
                                                           Map<String, Object> sceneOverride) {
        projectAccess.require(projectId, userId, Action.EDIT_CONTENT);
        WorkspaceAsset asset = requireScene(projectId, assetId);
        AssetVersion version = versionMapper.selectOne(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getId, assetVersionId).eq(AssetVersion::getAssetId, assetId));
        if (version == null) throw new BizException(ErrorCode.ASSET_NOT_FOUND, "场景资产版本不存在");
        Map<String, Object> metadata = parseMetadata(version.getMetadata());
        Map<String, Object> variant = variantsFrom(metadata).stream()
                .filter(item -> variantId.equals(item.get("id"))).findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.ASSET_NOT_FOUND, "场景变体不属于该资产版本"));
        int persistedVariantVersion = variantVersion(variant);
        if (!Objects.equals(variantVersion, persistedVariantVersion)) {
            throw new BizException(ErrorCode.ASSET_VERSION_CONFLICT,
                    "场景变体版本不匹配，请选择资产版本中实际存在的变体版本");
        }

        Map<String, Object> masterMetadata = master(metadata);
        Map<String, Object> snapshotMaster = new LinkedHashMap<>();
        snapshotMaster.put("id", masterMetadata.get("stable_id"));
        snapshotMaster.put("name", asset.getName());
        snapshotMaster.put("version", version.getVersionNumber());
        snapshotMaster.put("path", canonicalScenePath(masterMetadata, asset));
        snapshotMaster.put("fixedProps", normalizeValue(masterMetadata.get("fixed_props")));

        Map<String, Object> snapshotVariant = new LinkedHashMap<>();
        snapshotVariant.put("id", variantId);
        snapshotVariant.put("name", variant.get("name"));
        snapshotVariant.put("version", persistedVariantVersion);

        Map<String, Object> normalizedOverride = normalizedMap(sceneOverride);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("master", snapshotMaster);
        snapshot.put("variant", snapshotVariant);
        snapshot.put("sceneOverride", normalizedOverride);
        snapshot.put("continuityRules", normalizeValue(masterMetadata.getOrDefault("continuity_rules", List.of())));
        snapshot.put("finalPromptFragment", finalPromptFragment(masterMetadata, variant, normalizedOverride));
        Map<String, Object> canonical = normalizedMap(snapshot);
        canonical.put("fingerprint", snapshotFingerprint(canonical));
        canonical = normalizedMap(canonical);
        return new ResolvedSceneBinding(assetId, assetVersionId, variantId, persistedVariantVersion,
                canonical, writeMetadata(canonical));
    }

    public List<SceneContinuityIssue> storyboardContinuityIssues(Long projectId, List<StoryboardShot> shots) {
        List<SceneContinuityIssue> issues = new ArrayList<>();
        for (StoryboardShot shot : shots) {
            BindingIntegrityIssue integrity = bindingIntegrityIssue(projectId, shot);
            Map<String, Object> snapshot = parseSnapshotOrNull(shot.getSceneAssetSnapshot());
            if (integrity != null) {
                issues.add(issue(integrity.code(), shot, integrity.message(),
                        "PUT /shots/" + shot.getId() + "/scene-asset 重新绑定有效场景资产快照"));
            }
            if (snapshot != null && hasFixedPropConflict(snapshot)) {
                issues.add(issue("FIXED_PROP_CONFLICT", shot, "镜头覆写与主场景固定道具冲突",
                        "移除冲突的 sceneOverride.fixed_props 或确认新的主场景版本"));
            }
            if (integrity != null) continue;
            WorkspaceAsset asset = assetMapper.selectById(shot.getSceneAssetId());
            if (!Objects.equals(asset.getCurrentVersionId(), shot.getSceneAssetVersionId())) {
                issues.add(issue("STALE_ASSET", shot, "场景资产已有新版本，当前镜头仍保留历史快照",
                        "确认变更后重新调用 scene-asset 绑定接口"));
            }
            Map<String, Object> currentMetadata;
            try {
                currentMetadata = currentMetadata(asset);
            } catch (BizException ex) {
                issues.add(issue("STALE_ASSET", shot, "场景资产当前版本无法校验",
                        "修复资产版本元数据后重新绑定"));
                continue;
            }
            Map<String, Object> currentVariant = variantsFrom(currentMetadata).stream()
                    .filter(item -> Objects.equals(shot.getSceneVariantId(), item.get("id"))).findFirst().orElse(null);
            if (currentVariant == null || !Objects.equals(shot.getSceneVariantVersion(), variantVersion(currentVariant))) {
                issues.add(issue("VARIANT_MISMATCH", shot, "场景变体版本已变更或不再存在",
                        "选择当前资产版本中的变体并重新绑定"));
            }
        }
        return List.copyOf(issues);
    }

    public void requireStoryboardLockSnapshots(Long projectId, Long versionId) {
        List<StoryboardShot> shots = storyboardVersionShotMapper.selectList(
                new LambdaQueryWrapper<StoryboardShot>().eq(StoryboardShot::getVersionId, versionId));
        List<String> invalid = shots.stream().map(shot -> {
            BindingIntegrityIssue problem = bindingIntegrityIssue(projectId, shot);
            return problem == null ? null : "shotId=" + shot.getId() + "(" + problem.message() + ")";
        }).filter(Objects::nonNull).toList();
        if (!invalid.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分镜锁定失败：镜头缺少有效场景资产快照 " + String.join(", ", invalid)
                    + "；请调用 PUT /shots/{shotId}/scene-asset 修复");
        }
    }

    private BindingIntegrityIssue bindingIntegrityIssue(Long projectId, StoryboardShot shot) {
        if (shot.getSceneAssetId() == null || shot.getSceneAssetVersionId() == null
                || shot.getSceneVariantId() == null || shot.getSceneVariantVersion() == null
                || shot.getSceneAssetSnapshot() == null || shot.getSceneAssetSnapshot().isBlank()) {
            return missingBinding("缺少场景资产绑定字段");
        }
        WorkspaceAsset asset = assetMapper.selectOne(sceneQuery(projectId).eq(WorkspaceAsset::getId, shot.getSceneAssetId()));
        if (asset == null) return missingBinding("场景资产不属于当前项目");
        AssetVersion version = versionMapper.selectOne(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getId, shot.getSceneAssetVersionId())
                .eq(AssetVersion::getAssetId, shot.getSceneAssetId()));
        if (version == null) return missingBinding("场景资产版本不属于已绑定资产");
        try {
            Map<String, Object> metadata = parseMetadata(version.getMetadata());
            Map<String, Object> masterMetadata = master(metadata);
            Map<String, Object> variant = variantsFrom(metadata).stream()
                    .filter(item -> Objects.equals(shot.getSceneVariantId(), item.get("id"))).findFirst().orElse(null);
            if (variant == null || !Objects.equals(shot.getSceneVariantVersion(), variantVersion(variant))) {
                return variantMismatch("变体不属于已绑定资产版本");
            }
            Map<String, Object> snapshot = parseSnapshot(shot.getSceneAssetSnapshot());
            Map<String, Object> snapshotMaster = mapValue(snapshot.get("master"));
            Map<String, Object> snapshotVariant = mapValue(snapshot.get("variant"));
            if (!nonBlank(snapshotMaster.get("id")) || !nonBlank(snapshotMaster.get("name"))
                    || !nonBlank(snapshotMaster.get("path"))
                    || !(snapshot.get("sceneOverride") instanceof Map<?, ?>)
                    || !(snapshot.get("continuityRules") instanceof List<?>)
                    || !(snapshot.get("finalPromptFragment") instanceof String)
                    || !nonBlank(snapshotVariant.get("name"))
                    || !nonBlank(snapshot.get("fingerprint"))) {
                return missingBinding("场景资产快照结构不完整");
            }
            if (!Objects.equals(number(snapshotMaster.get("version")), version.getVersionNumber())) {
                return missingBinding("场景主资产快照与绑定版本不一致");
            }
            if (!Objects.equals(snapshotVariant.get("id"), shot.getSceneVariantId())
                    || !Objects.equals(number(snapshotVariant.get("version")), shot.getSceneVariantVersion())) {
                return variantMismatch("场景变体快照与绑定版本不一致");
            }
            Map<String, Object> payload = new LinkedHashMap<>(snapshot);
            String persistedFingerprint = String.valueOf(payload.remove("fingerprint"));
            if (!Objects.equals(persistedFingerprint, snapshotFingerprint(payload))) {
                return missingBinding("场景资产快照指纹校验失败");
            }

            Map<String, Object> expectedMaster = new LinkedHashMap<>();
            expectedMaster.put("id", masterMetadata.get("stable_id"));
            expectedMaster.put("name", snapshotMaster.get("name"));
            expectedMaster.put("version", version.getVersionNumber());
            expectedMaster.put("path", snapshotMaster.get("path"));
            expectedMaster.put("fixedProps", normalizeValue(masterMetadata.get("fixed_props")));
            Map<String, Object> expectedVariant = new LinkedHashMap<>();
            expectedVariant.put("id", variant.get("id"));
            expectedVariant.put("name", variant.get("name"));
            expectedVariant.put("version", variantVersion(variant));
            Map<String, Object> sceneOverride = normalizedMap(mapValue(snapshot.get("sceneOverride")));
            Map<String, Object> expected = new LinkedHashMap<>();
            expected.put("master", expectedMaster);
            expected.put("variant", expectedVariant);
            expected.put("sceneOverride", sceneOverride);
            expected.put("continuityRules",
                    normalizeValue(masterMetadata.getOrDefault("continuity_rules", List.of())));
            expected.put("finalPromptFragment", finalPromptFragment(masterMetadata, variant, sceneOverride));
            if (!Objects.equals(normalizedMap(payload), normalizedMap(expected))) {
                return missingBinding("场景资产快照与历史绑定版本不一致");
            }
        } catch (RuntimeException ex) {
            return missingBinding("场景资产快照无法解析");
        }
        return null;
    }

    private BindingIntegrityIssue missingBinding(String message) {
        return new BindingIntegrityIssue("MISSING_ASSET", message);
    }

    private BindingIntegrityIssue variantMismatch(String message) {
        return new BindingIntegrityIssue("VARIANT_MISMATCH", message);
    }

    private SceneContinuityIssue issue(String code, StoryboardShot shot, String message, String repair) {
        return new SceneContinuityIssue(code, shot.getId(), shot.getShotCode(), message, repair);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSnapshot(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "场景资产快照无效");
        }
    }

    private Map<String, Object> parseSnapshotOrNull(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return parseSnapshot(json);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private boolean hasFixedPropConflict(Map<String, Object> snapshot) {
        Map<String, Object> masterProps = mapValue(mapValue(snapshot.get("master")).get("fixedProps"));
        Map<String, Object> overrideProps = mapValue(mapValue(snapshot.get("sceneOverride")).get("fixed_props"));
        return overrideProps.entrySet().stream().anyMatch(entry -> masterProps.containsKey(entry.getKey())
                && !Objects.equals(masterProps.get(entry.getKey()), entry.getValue()));
    }

    private Integer number(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private boolean nonBlank(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private Map<String, Object> normalizedMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) return new LinkedHashMap<>();
        Map<String, Object> sorted = new TreeMap<>();
        input.forEach((key, value) -> sorted.put(key, normalizeValue(value)));
        return new LinkedHashMap<>(sorted);
    }

    @SuppressWarnings("unchecked")
    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) return normalizedMap((Map<String, Object>) map);
        if (value instanceof List<?> list) return list.stream().map(this::normalizeValue).toList();
        return value;
    }

    private String snapshotFingerprint(Map<String, Object> payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    writeMetadata(normalizedMap(payload)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "场景资产快照指纹计算失败");
        }
    }

    private String finalPromptFragment(Map<String, Object> master, Map<String, Object> variant,
                                       Map<String, Object> sceneOverride) {
        return java.util.stream.Stream.of(master.get("prompts"), variant.get("prompts"),
                        variant.get("lighting_delta"), sceneOverride.get("prompt_fragment"))
                .filter(Objects::nonNull).map(this::promptText).filter(text -> !text.isBlank())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private String promptText(Object value) {
        return value instanceof String text ? text : writeMetadata(Map.of("value", normalizeValue(value)));
    }

    private String canonicalScenePath(Map<String, Object> master, WorkspaceAsset asset) {
        String stableId = String.valueOf(master.getOrDefault("stable_id", "SCENE-ASSET-%03d".formatted(asset.getId())));
        String safeId = safePathPart(stableId);
        String safeName = safePathPart(asset.getName()).replaceAll("\\.+$", "");
        return "04-场景资产/" + safeId + "-" + (safeName.isBlank() ? "unnamed-scene" : safeName) + ".md";
    }

    private String safePathPart(String value) {
        return (value == null ? "" : value).replace("..", "·").replace("/", "／").replace("\\", "＼")
                .replace("#", "＃").replace("[", "［").replace("]", "］")
                .replaceAll("[\\r\\n]+", " ").trim();
    }

    public record ResolvedSceneBinding(Long assetId, Long assetVersionId, String variantId,
                                       Integer variantVersion, Map<String, Object> snapshot,
                                       String snapshotJson) {}

    public record SceneContinuityIssue(String code, Long shotId, String shotCode,
                                       String message, String repairAction) {}

    private record BindingIntegrityIssue(String code, String message) {}

    private WorkspaceAsset requireScene(Long projectId, Long assetId) {
        WorkspaceAsset asset = assetMapper.selectOne(sceneQuery(projectId).eq(WorkspaceAsset::getId, assetId));
        if (asset == null) throw new BizException(ErrorCode.ASSET_NOT_FOUND, "场景资产不存在");
        return asset;
    }

    private LambdaQueryWrapper<WorkspaceAsset> sceneQuery(Long projectId) {
        return new LambdaQueryWrapper<WorkspaceAsset>()
                .eq(WorkspaceAsset::getContentProjectId, projectId)
                .eq(WorkspaceAsset::getAssetType, SCENE)
                .eq(WorkspaceAsset::getSourceType, PROJECT_GENERATED);
    }

    private AssetVersion appendVersion(WorkspaceAsset asset, Map<String, Object> metadata, Long userId) {
        Long count = versionMapper.selectCount(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, asset.getId()));
        AssetVersion version = new AssetVersion();
        version.setAssetId(asset.getId());
        version.setVersionNumber(count.intValue() + 1);
        version.setMetadata(writeMetadata(metadata));
        version.setCreatedBy(userId);
        versionMapper.insert(version);
        asset.setCurrentVersionId(version.getId());
        asset.setUpdatedBy(userId);
        assetMapper.updateById(asset);
        return version;
    }

    private SceneAssetImpactView impactFor(Long assetId) {
        WorkspaceAsset asset = assetMapper.selectById(assetId);
        Long currentVersionId = asset == null ? null : asset.getCurrentVersionId();
        List<ImpactReferenceView> refs = new ArrayList<>();
        applicationMapper.selectList(new LambdaQueryWrapper<AssetApplication>()
                        .eq(AssetApplication::getAssetId, assetId)
                        .eq(AssetApplication::getStatus, "APPLIED"))
                .forEach(application -> refs.add(new ImpactReferenceView("APPLICATION", application.getId(),
                        application.getAssetVersionId(), syncStatus(application.getAssetVersionId(), currentVersionId))));
        placementMapper.selectList(new LambdaQueryWrapper<CanvasAssetPlacement>()
                        .eq(CanvasAssetPlacement::getAssetId, assetId).isNull(CanvasAssetPlacement::getReleasedAt))
                .forEach(placement -> refs.add(new ImpactReferenceView("CANVAS_PLACEMENT", placement.getId(),
                        placement.getAssetVersionId(), syncStatus(placement.getAssetVersionId(), currentVersionId))));
        long staleReferences = refs.stream().filter(reference -> "NEEDS_SYNC".equals(reference.syncStatus())).count();
        return new SceneAssetImpactView(assetId, refs.size(), staleReferences, List.copyOf(refs));
    }

    private SceneAssetView toView(WorkspaceAsset asset) {
        Map<String, Object> metadata = currentMetadata(asset);
        AssetVersion current = requireCurrentVersion(asset);
        return new SceneAssetView(asset.getId(), asset.getUuid(), asset.getContentProjectId(), asset.getAssetType(),
                asset.getName(), asset.getSourceType(), asset.getStatus(), asset.getCurrentVersionId(),
                current == null ? 0 : current.getVersionNumber(), master(metadata), variantsFrom(metadata),
                asset.getCreatedAt(), asset.getUpdatedAt());
    }

    private SceneAssetVersionView toVersionView(AssetVersion version, String changeNote) {
        return new SceneAssetVersionView(version.getId(), version.getAssetId(), version.getVersionNumber(),
                parseMetadata(version.getMetadata()), changeNote, version.getCreatedAt());
    }

    private Map<String, Object> metadataFor(CreateSceneAssetRequest request, Long projectId) {
        Map<String, Object> master = new LinkedHashMap<>();
        master.put("stable_id", nextStableAssetId(projectId));
        put(master, "world_location_ref", request.worldLocationRef());
        put(master, "space_type", request.spaceType());
        put(master, "reusability", request.reusability());
        put(master, "reality_type", request.realityType());
        put(master, "layout", request.layout());
        put(master, "materials", request.materials());
        put(master, "palette", request.palette());
        put(master, "lighting", request.lighting());
        put(master, "landmarks", request.landmarks());
        put(master, "fixed_props", request.fixedProps());
        put(master, "movable_props", request.movableProps());
        put(master, "entrances_exits", request.entrancesExits());
        put(master, "continuity_rules", request.continuityRules());
        put(master, "references", request.references());
        put(master, "prompts", request.prompts());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schema_version", 1);
        metadata.put("master", master);
        metadata.put("variants", variants(request.variants()));
        return metadata;
    }

    private void merge(Map<String, Object> master, UpdateSceneAssetRequest request) {
        put(master, "world_location_ref", request.worldLocationRef());
        put(master, "space_type", request.spaceType());
        put(master, "reusability", request.reusability());
        put(master, "reality_type", request.realityType());
        put(master, "layout", request.layout());
        put(master, "materials", request.materials());
        put(master, "palette", request.palette());
        put(master, "lighting", request.lighting());
        put(master, "landmarks", request.landmarks());
        put(master, "fixed_props", request.fixedProps());
        put(master, "movable_props", request.movableProps());
        put(master, "entrances_exits", request.entrancesExits());
        put(master, "continuity_rules", request.continuityRules());
        put(master, "references", request.references());
        put(master, "prompts", request.prompts());
    }

    private List<Map<String, Object>> variants(List<CreateVariantRequest> requests) {
        if (requests == null) return List.of();
        List<Map<String, Object>> variants = new ArrayList<>();
        for (CreateVariantRequest variant : requests) {
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("id", stableVariantId(variants.size() + 1));
            delta.put("version", 1);
            put(delta, "name", variant.name());
            put(delta, "time", variant.time());
            put(delta, "lighting_delta", variant.lightingDelta());
            put(delta, "prompts", variant.prompts());
            put(delta, "references", variant.references());
            variants.add(delta);
        }
        return List.copyOf(variants);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> currentMetadata(WorkspaceAsset asset) {
        return parseMetadata(requireCurrentVersion(asset).getMetadata());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> master(Map<String, Object> metadata) {
        Object value = metadata.get("master");
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> variantsFrom(Map<String, Object> metadata) {
        Object value = metadata.get("variants");
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance)
                .<Map<String, Object>>map(item -> new LinkedHashMap<>((Map<String, Object>) item)).toList();
    }

    private Map<String, Object> parseMetadata(String json) {
        try {
            if (json == null || json.isBlank()) throw invalidMetadata();
            Map<String, Object> metadata = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
            validateMetadataEnvelope(metadata);
            return metadata;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw invalidMetadata();
        }
    }

    private AssetVersion requireCurrentVersion(WorkspaceAsset asset) {
        if (asset.getCurrentVersionId() == null) throw invalidMetadata();
        AssetVersion version = versionMapper.selectById(asset.getCurrentVersionId());
        if (version == null) throw invalidMetadata();
        return version;
    }

    private void validateMetadataEnvelope(Map<String, Object> metadata) {
        Object schemaVersion = metadata.get("schema_version");
        if (!(schemaVersion instanceof Number number) || number.intValue() != 1
                || !(metadata.get("master") instanceof Map<?, ?>)
                || !(metadata.get("variants") instanceof List<?>)) {
            throw invalidMetadata();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> sceneMaster = (Map<String, Object>) metadata.get("master");
        for (String field : List.of("space_type", "reusability", "reality_type")) {
            Object value = sceneMaster.get(field);
            if (!(value instanceof String text) || text.isBlank()) throw invalidMetadata();
        }
        for (Object variant : (List<?>) metadata.get("variants")) {
            if (!(variant instanceof Map<?, ?> variantMap)) throw invalidMetadata();
            Object name = variantMap.get("name");
            if (!(name instanceof String text) || text.isBlank()) throw invalidMetadata();
        }
    }

    private void requireUpdatePayload(UpdateSceneAssetRequest request) {
        if (request == null || !hasMutableField(request)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "场景资产更新至少需要一个可变字段");
        }
        requireNonBlankIfPresent(request.name(), "name");
        requireNonBlankIfPresent(request.spaceType(), "space_type");
        requireNonBlankIfPresent(request.reusability(), "reusability");
        requireNonBlankIfPresent(request.realityType(), "reality_type");
    }

    private void requireVariantPayload(UpdateSceneVariantRequest request) {
        if (request == null || (request.name() == null && request.time() == null && request.lightingDelta() == null
                && request.prompts() == null && request.references() == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "场景变体更新至少需要一个可变字段");
        }
        requireNonBlankIfPresent(request.name(), "name");
    }

    private boolean hasMutableField(UpdateSceneAssetRequest request) {
        return request.name() != null || request.spaceType() != null || request.reusability() != null
                || request.realityType() != null || request.worldLocationRef() != null || request.layout() != null
                || request.materials() != null || request.palette() != null || request.lighting() != null
                || request.landmarks() != null || request.fixedProps() != null || request.movableProps() != null
                || request.entrancesExits() != null || request.continuityRules() != null || request.references() != null
                || request.prompts() != null;
    }

    private void requireNonBlankIfPresent(String value, String field) {
        if (value != null && value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空白");
        }
    }

    private String syncStatus(Long referencedVersionId, Long currentVersionId) {
        if (currentVersionId != null && !currentVersionId.equals(referencedVersionId)) {
            return "NEEDS_SYNC";
        }
        return "CURRENT";
    }

    private BizException invalidMetadata() {
        return new BizException(ErrorCode.PARAM_INVALID, "场景资产版本元数据无效，无法继续操作");
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "场景资产元数据序列化失败");
        }
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private String projectWorkspaceId(Long projectId) {
        return "project_" + projectId;
    }

    private String nextStableAssetId(Long projectId) {
        long count = assetMapper.selectCount(sceneQuery(projectId));
        return "SCENE-ASSET-%03d".formatted(count);
    }

    private String stableVariantId(int sequence) {
        return "VAR-%03d".formatted(sequence);
    }

    private int variantVersion(Map<String, Object> variant) {
        Object value = variant.get("version");
        return value instanceof Number number ? number.intValue() : 1;
    }

    private String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int nextVariantSequence(Long assetId) {
        int max = 0;
        for (AssetVersion version : versionMapper.selectList(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, assetId))) {
            for (Map<String, Object> variant : variantsFrom(parseMetadata(version.getMetadata()))) {
                Object id = variant.get("id");
                if (id instanceof String stableId && stableId.matches("VAR-\\d+")) {
                    max = Math.max(max, Integer.parseInt(stableId.substring("VAR-".length())));
                }
            }
        }
        return max + 1;
    }

    private List<SceneAssetMarkdownProjector.TrustedLink> trustedLinks(Long projectId, Long assetId) {
        LinkedHashMap<String, SceneAssetMarkdownProjector.TrustedLink> links = new LinkedHashMap<>();
        applicationMapper.selectList(new LambdaQueryWrapper<AssetApplication>()
                        .eq(AssetApplication::getAssetId, assetId).eq(AssetApplication::getStatus, "APPLIED"))
                .forEach(application -> trustedLink(projectId, application.getTargetType(), application.getTargetId())
                        .ifPresent(link -> links.putIfAbsent(link.path(), link)));
        placementMapper.selectList(new LambdaQueryWrapper<CanvasAssetPlacement>()
                        .eq(CanvasAssetPlacement::getAssetId, assetId).isNull(CanvasAssetPlacement::getReleasedAt))
                .forEach(placement -> trustedCanvasLink(projectId, placement.getCanvasProjectId(), placement.getNodeId())
                        .ifPresent(link -> links.putIfAbsent(link.path(), link)));
        return links.values().stream()
                .sorted(Comparator.comparing((SceneAssetMarkdownProjector.TrustedLink link) ->
                                SceneAssetMarkdownProjector.normalizedLinkPath(link.path()))
                        .thenComparing(link -> SceneAssetMarkdownProjector.wikiLinkAlias(link.alias()))
                        .thenComparing(SceneAssetMarkdownProjector.TrustedLink::path)
                        .thenComparing(SceneAssetMarkdownProjector.TrustedLink::alias))
                .toList();
    }

    private java.util.Optional<SceneAssetMarkdownProjector.TrustedLink> trustedLink(Long projectId, String type,
                                                                                     Long targetId) {
        if (targetId == null || type == null) return java.util.Optional.empty();
        return switch (type) {
            case "CONTENT_UNIT" -> trustedContentUnitLink(projectId, targetId);
            case "STORYBOARD_SHOT" -> trustedStoryboardShotLink(projectId, targetId);
            default -> java.util.Optional.empty();
        };
    }

    private java.util.Optional<SceneAssetMarkdownProjector.TrustedLink> trustedContentUnitLink(Long projectId,
                                                                                                 Long targetId) {
        ContentUnit unit = contentUnitMapper.selectById(targetId);
        if (unit == null || !projectId.equals(unit.getProjectId()) || !safeArtifactSegment(unit.getStableKey())
                || unit.getTitle() == null || unit.getTitle().isBlank()) return java.util.Optional.empty();
        return java.util.Optional.of(new SceneAssetMarkdownProjector.TrustedLink(
                "06-剧本正文/" + unit.getStableKey() + ".md", unit.getTitle()));
    }

    private java.util.Optional<SceneAssetMarkdownProjector.TrustedLink> trustedStoryboardShotLink(Long projectId,
                                                                                                     Long targetId) {
        StoryboardShot shot = storyboardVersionShotMapper.selectById(targetId);
        if (shot == null || !List.of("draft", "confirmed", "needs_review").contains(shot.getStatus())
                || !safeArtifactSegment(shot.getShotCode())) return java.util.Optional.empty();
        StoryboardVersion version = storyboardVersionMapper.selectById(shot.getVersionId());
        Storyboard storyboard = version == null ? null : storyboardMapper.selectById(version.getStoryboardId());
        if (storyboard == null || !projectId.equals(storyboard.getProjectId())) return java.util.Optional.empty();
        return java.util.Optional.of(new SceneAssetMarkdownProjector.TrustedLink(
                "08-文字分镜/" + shot.getShotCode() + ".md", shot.getShotCode()));
    }

    private java.util.Optional<SceneAssetMarkdownProjector.TrustedLink> trustedCanvasLink(Long projectId,
                                                                                             Long canvasProjectId,
                                                                                             Long nodeId) {
        CanvasNode node = nodeId == null ? null : canvasNodeMapper.selectById(nodeId);
        CanvasProject canvas = canvasProjectId == null ? null : canvasProjectMapper.selectById(canvasProjectId);
        if (node == null || canvas == null || !projectId.equals(canvas.getContentProjectId())
                || !canvas.getId().equals(node.getProjectId()) || !safeArtifactSegment(node.getUuid())) {
            return java.util.Optional.empty();
        }
        String alias = node.getName() == null || node.getName().isBlank() ? node.getUuid() : node.getName();
        return java.util.Optional.of(new SceneAssetMarkdownProjector.TrustedLink(
                "09-画布交接/CANVAS-NODE-" + node.getUuid() + ".md", alias));
    }

    private boolean safeArtifactSegment(String value) {
        return value != null && value.matches("[\\p{L}\\p{N}_-]+");
    }
}
