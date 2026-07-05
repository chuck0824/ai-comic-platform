package com.aicp.module.agent.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.agent.dto.AgentConfigViews;
import com.aicp.module.agent.entity.AgentExecutionSnapshot;
import com.aicp.module.agent.mapper.AgentExecutionSnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutionSnapshotService {

    private final AgentExecutionSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentConfigViews.SnapshotView freeze(AgentConfigViews.ResolvedConfigView config,
                                                  Long projectId, String taskType, Long userId) {
        AgentExecutionSnapshot snapshot = new AgentExecutionSnapshot();
        snapshot.setUuid("ags_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        snapshot.setBlueprintId(parseId(config.blueprintId()));
        snapshot.setBlueprintVersion(1);
        snapshot.setUserAgentId(parseId(config.userAgentId()));
        snapshot.setAgentVersionId(parseId(config.versionId()));
        snapshot.setBindingSource(config.bindingSource());
        try {
            snapshot.setResolvedParametersJson(objectMapper.writeValueAsString(config.resolvedParameters()));
            snapshot.setTemporaryOverridesJson("{}");
        } catch (Exception ignored) {}
        snapshot.setResolvedPrompt(config.compiledPrompt());
        snapshot.setPromptHash(config.promptHash());
        snapshot.setProjectId(projectId);
        snapshot.setBusinessTaskType(taskType);
        snapshot.setCreatedBy(userId);
        snapshotMapper.insert(snapshot);

        return new AgentConfigViews.SnapshotView(
                snapshot.getUuid(), config.blueprintId(), 1,
                config.userAgentId(), config.versionId(), config.bindingSource(),
                snapshot.getResolvedPrompt(), snapshot.getPromptHash(),
                projectId, taskType, snapshot.getCreatedAt());
    }

    public AgentConfigViews.SnapshotView getSnapshot(String uuid) {
        AgentExecutionSnapshot s = snapshotMapper.selectOne(new LambdaQueryWrapper<AgentExecutionSnapshot>()
                .eq(AgentExecutionSnapshot::getUuid, uuid));
        if (s == null) throw new BizException(ErrorCode.NOT_FOUND, "执行快照不存在");

        return new AgentConfigViews.SnapshotView(
                s.getUuid(), String.valueOf(s.getBlueprintId()), s.getBlueprintVersion(),
                s.getUserAgentId() != null ? String.valueOf(s.getUserAgentId()) : null,
                s.getAgentVersionId() != null ? String.valueOf(s.getAgentVersionId()) : null,
                s.getBindingSource(), s.getResolvedPrompt(), s.getPromptHash(),
                s.getProjectId(), s.getBusinessTaskType(), s.getCreatedAt());
    }

    private Long parseId(String id) {
        if (id == null) return null;
        try { return Long.parseLong(id); } catch (NumberFormatException e) { return null; }
    }
}
