package com.aicp.module.agent.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.agent.dto.AgentConfigRequests.CreateDraftRequest;
import com.aicp.module.agent.dto.AgentConfigRequests.PublishVersionRequest;
import com.aicp.module.agent.dto.AgentConfigRequests.UpdateDraftRequest;
import com.aicp.module.agent.dto.AgentConfigViews;
import com.aicp.module.agent.entity.AgentBlueprint;
import com.aicp.module.agent.entity.AgentVersion;
import com.aicp.module.agent.entity.UserAgentDefinition;
import com.aicp.module.agent.mapper.AgentTestRunMapper;
import com.aicp.module.agent.mapper.AgentVersionMapper;
import com.aicp.module.agent.mapper.UserAgentDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentVersionService {

    private final AgentVersionMapper versionMapper;
    private final UserAgentDefinitionMapper definitionMapper;
    private final AgentTestRunMapper testRunMapper;
    private final AgentBlueprintService blueprintService;
    private final AgentPromptCompiler compiler;
    private final ObjectMapper objectMapper;

    public AgentConfigViews.VersionView getVersion(String versionUuid) {
        AgentVersion v = requireByUuid(versionUuid);
        return toView(v);
    }

    public List<AgentConfigViews.VersionView> listVersions(String definitionUuid) {
        UserAgentDefinition def = definitionMapper.selectOne(new LambdaQueryWrapper<UserAgentDefinition>()
                .eq(UserAgentDefinition::getUuid, definitionUuid));
        if (def == null) throw new BizException(ErrorCode.AGENT_DEFINITION_NOT_FOUND);

        return versionMapper.selectList(new LambdaQueryWrapper<AgentVersion>()
                        .eq(AgentVersion::getUserAgentId, def.getId())
                        .orderByDesc(AgentVersion::getVersionNo))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public AgentConfigViews.VersionView createDraft(Long userId, String definitionUuid, CreateDraftRequest request) {
        UserAgentDefinition def = definitionMapper.selectOne(new LambdaQueryWrapper<UserAgentDefinition>()
                .eq(UserAgentDefinition::getUuid, definitionUuid));
        if (def == null) throw new BizException(ErrorCode.AGENT_DEFINITION_NOT_FOUND);
        if (!def.getOwnerUserId().equals(userId)) throw new BizException(ErrorCode.AGENT_CONFIG_ACCESS_DENIED);

        AgentBlueprint bp = blueprintService.requireById(def.getBlueprintId());

        Integer maxNo = versionMapper.selectList(new LambdaQueryWrapper<AgentVersion>()
                        .eq(AgentVersion::getUserAgentId, def.getId())
                        .orderByDesc(AgentVersion::getVersionNo))
                .stream()
                .map(AgentVersion::getVersionNo)
                .findFirst()
                .orElse(0);

        AgentVersion version = new AgentVersion();
        version.setUuid("ver_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        version.setUserAgentId(def.getId());
        version.setBlueprintId(bp.getId());
        version.setVersionNo(maxNo + 1);
        version.setParametersJson(bp.getDefaultParametersJson());
        version.setEditablePrompt(bp.getEditablePromptTemplate());
        version.setStatus("DRAFT");
        version.setCreatedBy(userId);
        version.setRowVersion(0);
        versionMapper.insert(version);

        return toView(version);
    }

    @Transactional
    public AgentConfigViews.VersionView updateDraft(Long userId, String versionUuid, UpdateDraftRequest request) {
        AgentVersion version = requireByUuid(versionUuid);
        if (!"DRAFT".equals(version.getStatus())) {
            throw new BizException(ErrorCode.AGENT_VERSION_STATE_CONFLICT, "只有草稿可以编辑");
        }

        try {
            version.setParametersJson(objectMapper.writeValueAsString(request.parameters()));
            version.setEditablePrompt(request.editablePrompt());
            if (request.examples() != null) {
                version.setExamplesJson(objectMapper.writeValueAsString(request.examples()));
            }
            if (request.modelPolicy() != null) {
                version.setModelPolicyJson(objectMapper.writeValueAsString(request.modelPolicy()));
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.AGENT_CONFIG_INVALID, "参数序列化失败");
        }

        LambdaUpdateWrapper<AgentVersion> wrapper = new LambdaUpdateWrapper<AgentVersion>()
                .eq(AgentVersion::getUuid, versionUuid)
                .eq(AgentVersion::getStatus, "DRAFT")
                .eq(AgentVersion::getRowVersion, request.rowVersion())
                .set(AgentVersion::getParametersJson, version.getParametersJson())
                .set(AgentVersion::getEditablePrompt, version.getEditablePrompt())
                .set(AgentVersion::getExamplesJson, version.getExamplesJson())
                .set(AgentVersion::getModelPolicyJson, version.getModelPolicyJson())
                .setSql("row_version = row_version + 1");

        int updated = versionMapper.update(null, wrapper);
        if (updated == 0) {
            throw new BizException(ErrorCode.AGENT_VERSION_STATE_CONFLICT, "版本已被他人修改，请刷新后重试");
        }

        version.setRowVersion(request.rowVersion() + 1);
        return toView(version);
    }

    public AgentConfigViews.ValidateResult validate(Long userId, String versionUuid) {
        AgentVersion version = requireByUuid(versionUuid);
        AgentBlueprint bp = blueprintService.requireById(version.getBlueprintId());
        List<String> errors = new ArrayList<>();

        try {
            compiler.compile(bp, version, Map.of("task_input", "test", "project_context", ""));
        } catch (BizException e) {
            errors.add(e.getMessage());
        }

        try {
            if (version.getParametersJson() != null) {
                Map<String, Object> params = objectMapper.readValue(version.getParametersJson(),
                        new TypeReference<Map<String, Object>>() {});
                if (params.isEmpty()) errors.add("参数不可为空");
            }
        } catch (Exception e) {
            errors.add("参数 JSON 格式错误");
        }

        boolean valid = errors.isEmpty();
        return new AgentConfigViews.ValidateResult(valid, errors);
    }

    @Transactional
    public AgentConfigViews.VersionView publish(Long userId, String versionUuid, PublishVersionRequest request) {
        AgentVersion version = requireByUuid(versionUuid);

        if (!"DRAFT".equals(version.getStatus())) {
            throw new BizException(ErrorCode.AGENT_VERSION_STATE_CONFLICT, "只有草稿可以发布");
        }
        if (!version.getRowVersion().equals(request.rowVersion())) {
            throw new BizException(ErrorCode.AGENT_VERSION_STATE_CONFLICT, "版本冲突，请刷新后重试");
        }

        AgentBlueprint bp = blueprintService.requireById(version.getBlueprintId());

        AgentConfigViews.ValidateResult validation = validate(userId, versionUuid);
        if (!validation.valid()) {
            throw new BizException(ErrorCode.AGENT_CONFIG_INVALID,
                    "校验未通过: " + String.join("; ", validation.errors()));
        }

        Long successCount = testRunMapper.selectCount(new LambdaQueryWrapper<com.aicp.module.agent.entity.AgentTestRun>()
                .eq(com.aicp.module.agent.entity.AgentTestRun::getAgentVersionId, version.getId())
                .eq(com.aicp.module.agent.entity.AgentTestRun::getStatus, "SUCCEEDED"));
        if (successCount == 0) {
            throw new BizException(ErrorCode.AGENT_TEST_RUN_REQUIRED);
        }

        version.setStatus("PUBLISHED");
        version.setChangeSummary(request.changeSummary());
        version.setPublishedBy(userId);
        version.setPublishedAt(LocalDateTime.now());
        version.setRowVersion(request.rowVersion() + 1);

        LambdaUpdateWrapper<AgentVersion> wrapper = new LambdaUpdateWrapper<AgentVersion>()
                .eq(AgentVersion::getUuid, versionUuid)
                .eq(AgentVersion::getRowVersion, request.rowVersion())
                .eq(AgentVersion::getStatus, "DRAFT")
                .set(AgentVersion::getStatus, "PUBLISHED")
                .set(AgentVersion::getChangeSummary, request.changeSummary())
                .set(AgentVersion::getPublishedBy, userId)
                .set(AgentVersion::getPublishedAt, LocalDateTime.now())
                .setSql("row_version = row_version + 1");

        int updated = versionMapper.update(null, wrapper);
        if (updated == 0) {
            throw new BizException(ErrorCode.AGENT_VERSION_STATE_CONFLICT);
        }

        UserAgentDefinition def = definitionMapper.selectById(version.getUserAgentId());
        def.setCurrentPublishedVersionId(version.getId());
        definitionMapper.updateById(def);

        return toView(version);
    }

    @Transactional
    public AgentConfigViews.VersionView activate(Long userId, String versionUuid) {
        AgentVersion version = requireByUuid(versionUuid);
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw new BizException(ErrorCode.AGENT_VERSION_STATE_CONFLICT, "只有已发布版本可以激活");
        }

        UserAgentDefinition def = definitionMapper.selectById(version.getUserAgentId());
        if (!def.getOwnerUserId().equals(userId)) {
            throw new BizException(ErrorCode.AGENT_CONFIG_ACCESS_DENIED);
        }

        def.setCurrentPublishedVersionId(version.getId());
        definitionMapper.updateById(def);

        return toView(version);
    }

    public AgentVersion requireByUuid(String uuid) {
        AgentVersion v = versionMapper.selectOne(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getUuid, uuid));
        if (v == null) throw new BizException(ErrorCode.AGENT_VERSION_NOT_FOUND);
        return v;
    }

    public AgentVersion requirePublishedAndRole(String uuid, String roleType) {
        AgentVersion v = requireByUuid(uuid);
        if (!"PUBLISHED".equals(v.getStatus())) {
            throw new BizException(ErrorCode.AGENT_VERSION_STATE_CONFLICT, "版本未发布");
        }
        AgentBlueprint bp = blueprintService.requireById(v.getBlueprintId());
        if (!bp.getRoleType().equals(roleType)) {
            throw new BizException(ErrorCode.AGENT_CONFIG_INVALID, "版本角色不匹配");
        }
        return v;
    }

    private AgentConfigViews.VersionView toView(AgentVersion v) {
        try {
            Map<String, Object> params = v.getParametersJson() != null
                    ? objectMapper.readValue(v.getParametersJson(), new TypeReference<Map<String, Object>>() {})
                    : Map.of();
            List<Map<String, Object>> examples = v.getExamplesJson() != null
                    ? objectMapper.readValue(v.getExamplesJson(), new TypeReference<List<Map<String, Object>>>() {})
                    : List.of();
            Map<String, Object> modelPolicy = v.getModelPolicyJson() != null
                    ? objectMapper.readValue(v.getModelPolicyJson(), new TypeReference<Map<String, Object>>() {})
                    : Map.of();
            return new AgentConfigViews.VersionView(
                    v.getUuid(), null, v.getVersionNo(), params, v.getEditablePrompt(),
                    examples, modelPolicy, v.getStatus(), v.getChangeSummary(),
                    v.getRowVersion(), v.getCreatedBy(), v.getCreatedAt(), v.getPublishedAt());
        } catch (Exception e) {
            return new AgentConfigViews.VersionView(
                    v.getUuid(), null, v.getVersionNo(), Map.of(), v.getEditablePrompt(),
                    List.of(), Map.of(), v.getStatus(), v.getChangeSummary(),
                    v.getRowVersion(), v.getCreatedBy(), v.getCreatedAt(), v.getPublishedAt());
        }
    }
}
