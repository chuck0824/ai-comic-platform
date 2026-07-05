package com.aicp.module.agent.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.agent.dto.AgentConfigRequests.TestRunRequest;
import com.aicp.module.agent.dto.AgentConfigViews;
import com.aicp.module.agent.entity.AgentBlueprint;
import com.aicp.module.agent.entity.AgentTestRun;
import com.aicp.module.agent.entity.AgentVersion;
import com.aicp.module.agent.mapper.AgentTestRunMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTestRunService {

    private final AgentTestRunMapper testRunMapper;
    private final AgentVersionService versionService;
    private final AgentBlueprintService blueprintService;
    private final AgentPromptCompiler compiler;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentConfigViews.TestRunView run(Long userId, String versionUuid, TestRunRequest request) {
        AgentVersion version = versionService.requireByUuid(versionUuid);
        AgentBlueprint bp = blueprintService.requireById(version.getBlueprintId());

        AgentPromptCompiler.CompiledPrompt compiled = compiler.compile(bp, version,
                Map.of("task_input", request.taskInput() != null ? request.taskInput() : "",
                        "project_context", ""));

        AgentTestRun run = new AgentTestRun();
        run.setUuid("run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        run.setAgentVersionId(version.getId());
        try {
            run.setInputSnapshotJson(objectMapper.writeValueAsString(
                    Map.of("task_input", request.taskInput() != null ? request.taskInput() : "")));
        } catch (Exception e) {
            run.setInputSnapshotJson("{}");
        }
        run.setStatus("RUNNING");
        run.setCreatedBy(userId);
        testRunMapper.insert(run);

        try {
            Map<String, Object> aiParams = new LinkedHashMap<>();
            aiParams.put("system_prompt", compiled.systemPrompt());
            aiParams.put("prompt", compiled.userPrompt());
            aiParams.put("model", extractModel(version.getModelPolicyJson()));

            long start = System.currentTimeMillis();
            Map<String, Object> result = aiRouter.chatCompletion(aiParams);
            int durationMs = (int) (System.currentTimeMillis() - start);

            String content = extractContent(result);
            Map<String, Object> outputJson = parseOutput(content);
            boolean schemaValid = outputJson != null && !outputJson.isEmpty();

            Integer promptTokens = extractTokens(result, "prompt_tokens");
            Integer completionTokens = extractTokens(result, "completion_tokens");

            run.setStatus("SUCCEEDED");
            run.setOutputJson(content);
            run.setOutputSchemaValid(schemaValid);
            run.setModelId(extractModel(version.getModelPolicyJson()));
            run.setPromptTokens(promptTokens);
            run.setCompletionTokens(completionTokens);
            run.setDurationMs(durationMs);
            testRunMapper.updateById(run);

            return toView(run);
        } catch (Exception e) {
            log.error("Test run failed for version {}", versionUuid, e);
            run.setStatus("FAILED");
            run.setErrorCode("AI_ERROR");
            run.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(2000, e.getMessage().length())) : "unknown");
            testRunMapper.updateById(run);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "试跑失败: " + e.getMessage());
        }
    }

    public AgentConfigViews.TestRunView getTestRun(String uuid) {
        AgentTestRun run = testRunMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentTestRun>()
                .eq(AgentTestRun::getUuid, uuid));
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "试跑记录不存在");
        }
        return toView(run);
    }

    private String extractModel(String modelPolicyJson) {
        try {
            Map<String, Object> policy = objectMapper.readValue(modelPolicyJson,
                    new TypeReference<Map<String, Object>>() {});
            return (String) policy.getOrDefault("default_model", "deepseek-v3");
        } catch (Exception e) {
            return "deepseek-v3";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> result) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    Object content = message.get("content");
                    return content != null ? content.toString() : result.toString();
                }
            }
        } catch (Exception ignored) {}
        return result.toString();
    }

    private Map<String, Object> parseOutput(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Integer extractTokens(Map<String, Object> result, String key) {
        try {
            Map<String, Object> usage = (Map<String, Object>) result.get("usage");
            if (usage != null && usage.containsKey(key)) {
                return ((Number) usage.get(key)).intValue();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private AgentConfigViews.TestRunView toView(AgentTestRun run) {
        Map<String, Object> outputJson = null;
        if (run.getOutputJson() != null) {
            try {
                outputJson = objectMapper.readValue(run.getOutputJson(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }
        return new AgentConfigViews.TestRunView(
                run.getUuid(), null, run.getStatus(), outputJson,
                run.getOutputSchemaValid(), run.getModelId(),
                run.getPromptTokens(), run.getCompletionTokens(),
                run.getCreditCost(), run.getDurationMs(),
                run.getErrorCode(), run.getErrorMessage(),
                run.getCreatedAt());
    }
}
