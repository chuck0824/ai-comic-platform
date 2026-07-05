package com.aicp.module.agent.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.agent.dto.AgentConfigViews;
import com.aicp.module.agent.entity.AgentBlueprint;
import com.aicp.module.agent.mapper.AgentBlueprintMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentBlueprintService {

    private final AgentBlueprintMapper mapper;
    private final ObjectMapper objectMapper;

    public List<AgentConfigViews.BlueprintView> listActive() {
        return mapper.selectList(new LambdaQueryWrapper<AgentBlueprint>()
                        .eq(AgentBlueprint::getStatus, "ACTIVE")
                        .orderByAsc(AgentBlueprint::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    public AgentConfigViews.BlueprintView getByUuid(String uuid) {
        AgentBlueprint bp = requireByUuid(uuid);
        return toView(bp);
    }

    public AgentBlueprint requireByUuid(String uuid) {
        AgentBlueprint bp = mapper.selectOne(new LambdaQueryWrapper<AgentBlueprint>()
                .eq(AgentBlueprint::getUuid, uuid));
        if (bp == null) {
            throw new BizException(ErrorCode.AGENT_BLUEPRINT_NOT_FOUND);
        }
        return bp;
    }

    public AgentBlueprint requireById(Long id) {
        AgentBlueprint bp = mapper.selectById(id);
        if (bp == null) {
            throw new BizException(ErrorCode.AGENT_BLUEPRINT_NOT_FOUND);
        }
        return bp;
    }

    public AgentBlueprint requireActive(String uuid) {
        AgentBlueprint bp = requireByUuid(uuid);
        if (!"ACTIVE".equals(bp.getStatus())) {
            throw new BizException(ErrorCode.AGENT_BLUEPRINT_NOT_FOUND);
        }
        return bp;
    }

    private AgentConfigViews.BlueprintView toView(AgentBlueprint bp) {
        try {
            Map<String, Object> schema = objectMapper.readValue(bp.getParameterSchemaJson(),
                    new TypeReference<Map<String, Object>>() {});
            Map<String, Object> defaults = objectMapper.readValue(bp.getDefaultParametersJson(),
                    new TypeReference<Map<String, Object>>() {});
            return new AgentConfigViews.BlueprintView(
                    bp.getUuid(), bp.getRoleType(), bp.getName(), bp.getDescription(),
                    schema, defaults, bp.getBlueprintVersion());
        } catch (Exception e) {
            log.error("Failed to parse Blueprint JSON for {}", bp.getUuid(), e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Blueprint 数据异常");
        }
    }
}
