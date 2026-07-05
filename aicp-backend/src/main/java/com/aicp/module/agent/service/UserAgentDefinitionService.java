package com.aicp.module.agent.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.agent.dto.AgentConfigRequests.CreateDefinitionRequest;
import com.aicp.module.agent.dto.AgentConfigRequests.UpdateDefinitionRequest;
import com.aicp.module.agent.dto.AgentConfigViews;
import com.aicp.module.agent.entity.AgentBlueprint;
import com.aicp.module.agent.entity.AgentVersion;
import com.aicp.module.agent.entity.UserAgentDefinition;
import com.aicp.module.agent.mapper.AgentVersionMapper;
import com.aicp.module.agent.mapper.UserAgentDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAgentDefinitionService {

    private final UserAgentDefinitionMapper definitionMapper;
    private final AgentVersionMapper versionMapper;
    private final AgentBlueprintService blueprintService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentConfigViews.DefinitionView create(Long userId, CreateDefinitionRequest request) {
        AgentBlueprint blueprint = blueprintService.requireActive(request.blueprintId());

        // Validate blueprint JSON fields are parseable
        try {
            if (blueprint.getDefaultParametersJson() != null) {
                objectMapper.readValue(blueprint.getDefaultParametersJson(),
                        new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.error("Failed to parse blueprint default parameters for blueprint {}", blueprint.getUuid(), e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Blueprint 默认参数格式异常");
        }

        UserAgentDefinition definition = new UserAgentDefinition();
        definition.setUuid("agent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        definition.setBlueprintId(blueprint.getId());
        definition.setOwnerUserId(userId);
        definition.setName(request.name());
        definition.setDescription(request.description());
        definition.setVisibility("PRIVATE");
        definition.setLifecycleStatus("ACTIVE");
        definition.setRowVersion(0);
        definitionMapper.insert(definition);

        AgentVersion version = new AgentVersion();
        version.setUuid("ver_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        version.setUserAgentId(definition.getId());
        version.setBlueprintId(blueprint.getId());
        version.setVersionNo(1);
        version.setParametersJson(blueprint.getDefaultParametersJson());
        version.setEditablePrompt(blueprint.getEditablePromptTemplate());
        version.setStatus("DRAFT");
        version.setCreatedBy(userId);
        version.setRowVersion(0);
        versionMapper.insert(version);

        return toDefinitionView(definition, blueprint);
    }

    public List<AgentConfigViews.DefinitionListItem> list(Long userId) {
        return definitionMapper.selectList(new LambdaQueryWrapper<UserAgentDefinition>()
                        .eq(UserAgentDefinition::getOwnerUserId, userId)
                        .eq(UserAgentDefinition::getLifecycleStatus, "ACTIVE")
                        .orderByDesc(UserAgentDefinition::getUpdatedAt))
                .stream()
                .map(this::toListItem)
                .toList();
    }

    public AgentConfigViews.DefinitionView get(Long userId, String uuid) {
        UserAgentDefinition def = requireOwned(userId, uuid);
        AgentBlueprint bp = blueprintService.requireById(def.getBlueprintId());
        return toDefinitionView(def, bp);
    }

    @Transactional
    public AgentConfigViews.DefinitionView update(Long userId, String uuid, UpdateDefinitionRequest request) {
        UserAgentDefinition def = requireOwned(userId, uuid);
        if (request.name() != null) def.setName(request.name());
        if (request.description() != null) def.setDescription(request.description());
        def.setRowVersion(def.getRowVersion() + 1);
        definitionMapper.updateById(def);
        AgentBlueprint bp = blueprintService.requireById(def.getBlueprintId());
        return toDefinitionView(def, bp);
    }

    @Transactional
    public AgentConfigViews.DefinitionView copy(Long userId, String uuid) {
        UserAgentDefinition source = requireOwned(userId, uuid);
        AgentBlueprint bp = blueprintService.requireById(source.getBlueprintId());

        UserAgentDefinition copy = new UserAgentDefinition();
        copy.setUuid("agent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        copy.setBlueprintId(source.getBlueprintId());
        copy.setOwnerUserId(userId);
        copy.setName(source.getName() + " (副本)");
        copy.setDescription(source.getDescription());
        copy.setApplicableGenresJson(source.getApplicableGenresJson());
        copy.setPlatformsJson(source.getPlatformsJson());
        copy.setVisibility("PRIVATE");
        copy.setLifecycleStatus("ACTIVE");
        copy.setRowVersion(0);
        definitionMapper.insert(copy);

        // Copy the latest published version (or draft if no published) as initial draft
        AgentVersion sourceVersion = findLatestVersionForCopy(source.getId());

        AgentVersion copyVersion = new AgentVersion();
        copyVersion.setUuid("ver_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        copyVersion.setUserAgentId(copy.getId());
        copyVersion.setBlueprintId(bp.getId());
        copyVersion.setVersionNo(1);
        if (sourceVersion != null) {
            copyVersion.setParametersJson(sourceVersion.getParametersJson());
            copyVersion.setEditablePrompt(sourceVersion.getEditablePrompt());
            copyVersion.setExamplesJson(sourceVersion.getExamplesJson());
            copyVersion.setModelPolicyJson(sourceVersion.getModelPolicyJson());
        } else {
            copyVersion.setParametersJson(bp.getDefaultParametersJson());
            copyVersion.setEditablePrompt(bp.getEditablePromptTemplate());
        }
        copyVersion.setStatus("DRAFT");
        copyVersion.setCreatedBy(userId);
        copyVersion.setRowVersion(0);
        versionMapper.insert(copyVersion);

        return toDefinitionView(copy, bp);
    }

    @Transactional
    public AgentConfigViews.DefinitionView archive(Long userId, String uuid) {
        UserAgentDefinition def = requireOwned(userId, uuid);
        def.setLifecycleStatus("ARCHIVED");
        def.setRowVersion(def.getRowVersion() + 1);
        definitionMapper.updateById(def);
        AgentBlueprint bp = blueprintService.requireById(def.getBlueprintId());
        return toDefinitionView(def, bp);
    }

    public UserAgentDefinition requireOwned(Long userId, String uuid) {
        UserAgentDefinition def = definitionMapper.selectOne(new LambdaQueryWrapper<UserAgentDefinition>()
                .eq(UserAgentDefinition::getUuid, uuid));
        if (def == null) {
            throw new BizException(ErrorCode.AGENT_DEFINITION_NOT_FOUND);
        }
        if (!def.getOwnerUserId().equals(userId)) {
            throw new BizException(ErrorCode.AGENT_CONFIG_ACCESS_DENIED);
        }
        return def;
    }

    public UserAgentDefinition requireByUuid(String uuid) {
        UserAgentDefinition def = definitionMapper.selectOne(new LambdaQueryWrapper<UserAgentDefinition>()
                .eq(UserAgentDefinition::getUuid, uuid));
        if (def == null) {
            throw new BizException(ErrorCode.AGENT_DEFINITION_NOT_FOUND);
        }
        return def;
    }

    /**
     * Find the latest published version for copying, falling back to the latest
     * draft if no published version exists.
     */
    private AgentVersion findLatestVersionForCopy(Long userAgentId) {
        List<AgentVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<AgentVersion>()
                        .eq(AgentVersion::getUserAgentId, userAgentId)
                        .orderByDesc(AgentVersion::getVersionNo));
        // Prefer the latest published version
        for (AgentVersion v : versions) {
            if ("PUBLISHED".equals(v.getStatus())) {
                return v;
            }
        }
        // Fall back to the latest draft
        for (AgentVersion v : versions) {
            if ("DRAFT".equals(v.getStatus())) {
                return v;
            }
        }
        return versions.isEmpty() ? null : versions.get(0);
    }

    private AgentConfigViews.DefinitionView toDefinitionView(UserAgentDefinition def, AgentBlueprint bp) {
        String publishedVersionUuid = resolvePublishedVersionUuid(def.getCurrentPublishedVersionId());
        return new AgentConfigViews.DefinitionView(
                def.getUuid(), bp.getUuid(), bp.getRoleType(), def.getName(), def.getDescription(),
                def.getLifecycleStatus(), publishedVersionUuid, def.getRowVersion(),
                def.getCreatedAt(), def.getUpdatedAt());
    }

    private AgentConfigViews.DefinitionListItem toListItem(UserAgentDefinition def) {
        AgentBlueprint bp = blueprintService.requireById(def.getBlueprintId());
        Integer currentVersionNo = null;
        String publishedVersionUuid = null;
        if (def.getCurrentPublishedVersionId() != null) {
            AgentVersion v = versionMapper.selectById(def.getCurrentPublishedVersionId());
            if (v != null) {
                currentVersionNo = v.getVersionNo();
                publishedVersionUuid = v.getUuid();
            }
        }
        return new AgentConfigViews.DefinitionListItem(
                def.getUuid(), bp.getUuid(), bp.getRoleType(), def.getName(),
                def.getLifecycleStatus(), publishedVersionUuid, currentVersionNo, def.getUpdatedAt());
    }

    private String resolvePublishedVersionUuid(Long versionId) {
        if (versionId == null) {
            return null;
        }
        AgentVersion v = versionMapper.selectById(versionId);
        return v != null ? v.getUuid() : null;
    }
}
