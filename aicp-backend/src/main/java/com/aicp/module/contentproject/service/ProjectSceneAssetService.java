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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        if (request.variants() != null) metadata.put("variants", variants(request.variants()));
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
        variant.put("id", stableVariantId(variants.size() + 1));
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
        return markdownProjector.project(projectId, asset, version, currentMetadata(asset));
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
                || request.prompts() != null || request.variants() != null;
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
}
