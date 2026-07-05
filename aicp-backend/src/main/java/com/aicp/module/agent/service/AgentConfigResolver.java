package com.aicp.module.agent.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.agent.dto.AgentConfigViews;
import com.aicp.module.agent.entity.AgentBinding;
import com.aicp.module.agent.entity.AgentBlueprint;
import com.aicp.module.agent.entity.AgentVersion;
import com.aicp.module.agent.entity.UserAgentDefinition;
import com.aicp.module.agent.mapper.AgentBindingMapper;
import com.aicp.module.agent.mapper.AgentBlueprintMapper;
import com.aicp.module.agent.mapper.AgentVersionMapper;
import com.aicp.module.agent.mapper.UserAgentDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentConfigResolver {

    private final AgentBindingMapper bindingMapper;
    private final AgentBlueprintMapper blueprintMapper;
    private final AgentVersionMapper versionMapper;
    private final UserAgentDefinitionMapper definitionMapper;
    private final AgentPromptCompiler compiler;
    private final ObjectMapper objectMapper;

    public AgentConfigViews.ResolvedConfigView resolve(Long userId, Long projectId,
                                                        String roleType, Map<String, Object> overrides) {
        AgentBinding binding = findProjectBinding(projectId, roleType);
        String bindingSource = "PROJECT";

        if (binding == null) {
            binding = findUserBinding(userId, roleType);
            bindingSource = "USER";
        }

        if (binding != null) {
            return resolveExplicit(binding, bindingSource, overrides);
        }

        return resolveSystemDefault(roleType, overrides);
    }

    private AgentBinding findProjectBinding(Long projectId, String roleType) {
        if (projectId == null) return null;
        return bindingMapper.selectOne(new LambdaQueryWrapper<AgentBinding>()
                .eq(AgentBinding::getScopeType, "PROJECT")
                .eq(AgentBinding::getScopeId, projectId.toString())
                .eq(AgentBinding::getRoleType, roleType));
    }

    private AgentBinding findUserBinding(Long userId, String roleType) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<AgentBinding>()
                .eq(AgentBinding::getScopeType, "USER")
                .eq(AgentBinding::getScopeId, userId.toString())
                .eq(AgentBinding::getRoleType, roleType));
    }

    private AgentConfigViews.ResolvedConfigView resolveExplicit(AgentBinding binding, String source,
                                                                  Map<String, Object> overrides) {
        AgentVersion version = versionMapper.selectById(binding.getAgentVersionId());
        if (version == null || "ARCHIVED".equals(version.getStatus())) {
            throw new BizException(ErrorCode.AGENT_VERSION_STATE_CONFLICT,
                    "绑定版本已失效，请重新绑定");
        }

        AgentBlueprint bp = blueprintMapper.selectById(version.getBlueprintId());
        UserAgentDefinition def = definitionMapper.selectById(binding.getUserAgentId());

        Map<String, Object> resolvedParams = mergeParameters(version, overrides);

        AgentPromptCompiler.CompiledPrompt compiled = compiler.compile(bp, version,
                buildRuntimeContext(overrides));

        return new AgentConfigViews.ResolvedConfigView(
                source,
                def != null ? def.getUuid() : null,
                def != null ? def.getName() : null,
                version.getUuid(), version.getVersionNo(),
                bp.getUuid(), bp.getRoleType(),
                resolvedParams,
                compiled.systemPrompt() + "\n\n" + compiled.userPrompt(),
                compiled.promptHash());
    }

    private AgentConfigViews.ResolvedConfigView resolveSystemDefault(String roleType,
                                                                       Map<String, Object> overrides) {
        AgentBlueprint bp = blueprintMapper.selectOne(new LambdaQueryWrapper<AgentBlueprint>()
                .eq(AgentBlueprint::getRoleType, roleType)
                .eq(AgentBlueprint::getStatus, "ACTIVE")
                .orderByDesc(AgentBlueprint::getBlueprintVersion)
                .last("LIMIT 1"));

        if (bp == null) {
            throw new BizException(ErrorCode.AGENT_BLUEPRINT_NOT_FOUND,
                    "角色 " + roleType + " 的系统默认框架不存在");
        }

        AgentVersion dummyVersion = new AgentVersion();
        dummyVersion.setEditablePrompt(bp.getEditablePromptTemplate());
        dummyVersion.setParametersJson(bp.getDefaultParametersJson());

        Map<String, Object> resolvedParams = overrides != null ? new HashMap<>(overrides) : new HashMap<>();
        try {
            Map<String, Object> defaults = objectMapper.readValue(bp.getDefaultParametersJson(),
                    new TypeReference<Map<String, Object>>() {});
            resolvedParams = new LinkedHashMap<>(defaults);
            if (overrides != null) resolvedParams.putAll(overrides);
        } catch (Exception ignored) {}

        AgentPromptCompiler.CompiledPrompt compiled = compiler.compile(bp, dummyVersion,
                buildRuntimeContext(overrides));

        return new AgentConfigViews.ResolvedConfigView(
                "SYSTEM", null, bp.getName(), null, 0,
                bp.getUuid(), bp.getRoleType(), resolvedParams,
                compiled.systemPrompt() + "\n\n" + compiled.userPrompt(),
                compiled.promptHash());
    }

    private Map<String, Object> mergeParameters(AgentVersion version, Map<String, Object> overrides) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            if (version.getParametersJson() != null) {
                params.putAll(objectMapper.readValue(version.getParametersJson(),
                        new TypeReference<Map<String, Object>>() {}));
            }
            if (overrides != null) params.putAll(overrides);
            return params;
        } catch (Exception e) {
            return overrides != null ? new LinkedHashMap<>(overrides) : new LinkedHashMap<>();
        }
    }

    private Map<String, Object> buildRuntimeContext(Map<String, Object> overrides) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("task_input", "");
        ctx.put("project_context", "");
        if (overrides != null && overrides.containsKey("task_input")) {
            ctx.put("task_input", overrides.get("task_input"));
        }
        return ctx;
    }
}
