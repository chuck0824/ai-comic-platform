package com.aicp.module.agent.service;

import com.aicp.module.agent.dto.AgentConfigRequests.BindVersionRequest;
import com.aicp.module.agent.dto.AgentConfigViews;
import com.aicp.module.agent.entity.AgentBinding;
import com.aicp.module.agent.entity.AgentVersion;
import com.aicp.module.agent.entity.UserAgentDefinition;
import com.aicp.module.agent.mapper.AgentBindingMapper;
import com.aicp.module.agent.mapper.UserAgentDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentBindingService {

    private final AgentBindingMapper bindingMapper;
    private final UserAgentDefinitionMapper definitionMapper;
    private final AgentVersionService versionService;

    public List<AgentConfigViews.BindingView> listUserBindings(Long userId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<AgentBinding>()
                        .eq(AgentBinding::getScopeType, "USER")
                        .eq(AgentBinding::getScopeId, userId.toString()))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public AgentConfigViews.BindingView setUserBinding(Long userId, String roleType, BindVersionRequest request) {
        AgentVersion version = versionService.requirePublishedAndRole(request.versionId(), roleType);
        return upsert("USER", userId.toString(), roleType, version, userId);
    }

    @Transactional
    public void deleteUserBinding(Long userId, String roleType) {
        bindingMapper.delete(new LambdaQueryWrapper<AgentBinding>()
                .eq(AgentBinding::getScopeType, "USER")
                .eq(AgentBinding::getScopeId, userId.toString())
                .eq(AgentBinding::getRoleType, roleType));
    }

    public List<AgentConfigViews.BindingView> listProjectBindings(Long projectId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<AgentBinding>()
                        .eq(AgentBinding::getScopeType, "PROJECT")
                        .eq(AgentBinding::getScopeId, projectId.toString()))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public AgentConfigViews.BindingView setProjectBinding(Long userId, Long projectId, String roleType,
                                                           BindVersionRequest request) {
        AgentVersion version = versionService.requirePublishedAndRole(request.versionId(), roleType);
        return upsert("PROJECT", projectId.toString(), roleType, version, userId);
    }

    @Transactional
    public void deleteProjectBinding(Long projectId, String roleType) {
        bindingMapper.delete(new LambdaQueryWrapper<AgentBinding>()
                .eq(AgentBinding::getScopeType, "PROJECT")
                .eq(AgentBinding::getScopeId, projectId.toString())
                .eq(AgentBinding::getRoleType, roleType));
    }

    private AgentConfigViews.BindingView upsert(String scopeType, String scopeId, String roleType,
                                                 AgentVersion version, Long userId) {
        AgentBinding existing = bindingMapper.selectOne(new LambdaQueryWrapper<AgentBinding>()
                .eq(AgentBinding::getScopeType, scopeType)
                .eq(AgentBinding::getScopeId, scopeId)
                .eq(AgentBinding::getRoleType, roleType));

        UserAgentDefinition agentDef = definitionMapper.selectById(version.getUserAgentId());
        String agentName = agentDef != null ? agentDef.getName() : "未知";

        if (existing != null) {
            existing.setAgentVersionId(version.getId());
            existing.setUserAgentId(version.getUserAgentId());
            existing.setUpdatedBy(userId);
            existing.setRowVersion(existing.getRowVersion() + 1);
            bindingMapper.updateById(existing);
            return new AgentConfigViews.BindingView(
                    existing.getUuid(), scopeType, scopeId, roleType,
                    agentName, version.getUuid(),
                    existing.getRowVersion(), existing.getCreatedAt());
        }

        AgentBinding binding = new AgentBinding();
        binding.setUuid("bind_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        binding.setScopeType(scopeType);
        binding.setScopeId(scopeId);
        binding.setRoleType(roleType);
        binding.setUserAgentId(version.getUserAgentId());
        binding.setAgentVersionId(version.getId());
        binding.setCreatedBy(userId);
        binding.setRowVersion(0);
        bindingMapper.insert(binding);

        return new AgentConfigViews.BindingView(
                binding.getUuid(), scopeType, scopeId, roleType,
                agentName, version.getUuid(), 0, binding.getCreatedAt());
    }

    private AgentConfigViews.BindingView toView(AgentBinding b) {
        UserAgentDefinition def = definitionMapper.selectById(b.getUserAgentId());
        String agentName = def != null ? def.getName() : "未知";
        return new AgentConfigViews.BindingView(
                b.getUuid(), b.getScopeType(), b.getScopeId(), b.getRoleType(),
                agentName, null, b.getRowVersion(), b.getCreatedAt());
    }
}
