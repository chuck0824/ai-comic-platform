package com.aicp.common.ai;

import com.aicp.common.ai.client.NewApiClient;
import com.aicp.module.generation.entity.GenerationTask;
import com.aicp.module.generation.mapper.GenerationTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 路由核心层
 * 职责：画布任务 → 模型选择 → Adapter 翻译 → new-api 调用 → 结果回写
 * 所有 AI 调用必须经过 AiRouter，不允许微服务直连 new-api
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRouter {

    private final NewApiClient newApiClient;
    private final GenerationTaskMapper taskMapper;

    /**
     * 执行生成任务
     */
    public void executeTask(Long taskId) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null) return;

        try {
            task.setStatus("running");
            task.setStartedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            Map<String, Object> params = parseJson(task.getParameters());
            Map<String, Object> result = routeAndExecute(task.getType(), task.getSubType(),
                    task.getModelId(), params);

            task.setStatus("succeeded");
            task.setProgress(100);
            task.setOutputAssets(toJson(result));
        } catch (Exception e) {
            log.error("AI task failed: taskId={}, type={}", task.getId(), task.getType(), e);
            task.setStatus("failed");
            task.setErrorCode("AI_ERROR");
            task.setErrorMessage(e.getMessage());
        } finally {
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    /**
     * 根据任务类型路由到对应 Adapter
     */
    private Map<String, Object> routeAndExecute(String type, String subType,
                                                 String modelId, Map<String, Object> params) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", modelId != null ? modelId : defaultModel(type));

        return switch (type) {
            case "image" -> executeImage(request, params);
            case "video" -> executeVideo(request, params, subType);
            case "audio" -> executeAudio(request, params);
            case "compose", "export" -> Map.of("message", "合成/导出任务排队中", "progress", 0);
            case "quality" -> executeQuality(request, params);
            case "agent", "skill" -> executeAgent(request, params);
            default -> executeLLM(request, params);
        };
    }

    /**
     * 公开的 LLM 调用入口 —— 供 ScriptGenService 等非 GenerationTask 场景使用
     */
    public Map<String, Object> chatCompletion(Map<String, Object> params) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", params.getOrDefault("model", defaultModel("llm")));
        return executeLLM(request, params);
    }

    private Map<String, Object> executeLLM(Map<String, Object> request, Map<String, Object> params) {
        request.put("messages", List.of(
                Map.of("role", "system", "content", params.getOrDefault("system_prompt", "你是一个AI助手")),
                Map.of("role", "user", "content", params.getOrDefault("prompt", ""))));
        request.put("temperature", params.getOrDefault("temperature", 0.7));
        request.put("max_tokens", params.getOrDefault("max_tokens", 4096));
        return newApiClient.chatCompletions(request);
    }

    private Map<String, Object> executeImage(Map<String, Object> request, Map<String, Object> params) {
        request.put("prompt", params.getOrDefault("prompt", ""));
        request.put("size", params.getOrDefault("size", "1080x1920"));
        request.put("n", params.getOrDefault("n", 1));
        return newApiClient.imageGeneration(request);
    }

    private Map<String, Object> executeVideo(Map<String, Object> request, Map<String, Object> params, String subType) {
        request.put("prompt", params.getOrDefault("prompt", ""));
        request.put("duration", params.getOrDefault("duration", 5));
        if ("first_last_frame".equals(subType)) {
            request.put("first_frame", params.get("keyframe_start"));
            request.put("last_frame", params.get("keyframe_end"));
        }
        if ("omni_reference".equals(subType)) {
            request.put("reference_images", params.get("reference_images"));
            request.put("reference_videos", params.get("reference_videos"));
            request.put("reference_audio", params.get("reference_audio"));
        }
        return newApiClient.videoGeneration(request);
    }

    private Map<String, Object> executeAudio(Map<String, Object> request, Map<String, Object> params) {
        request.put("input", params.getOrDefault("text", ""));
        request.put("voice", params.getOrDefault("voice_id", "default"));
        request.put("speed", params.getOrDefault("speed", 1.0));
        return newApiClient.audioSpeech(request);
    }

    private Map<String, Object> executeQuality(Map<String, Object> request, Map<String, Object> params) {
        request.put("messages", List.of(Map.of("role", "user", "content",
                "请对以下内容进行质量评分(0-1)：\n" + params.getOrDefault("content", ""))));
        return newApiClient.chatCompletions(request);
    }

    private Map<String, Object> executeAgent(Map<String, Object> request, Map<String, Object> params) {
        request.put("messages", List.of(Map.of("role", "system", "content",
                params.getOrDefault("skill_content", "执行以下任务：")),
                Map.of("role", "user", "content", params.getOrDefault("input", ""))));
        return newApiClient.chatCompletions(request);
    }

    private String defaultModel(String type) {
        return switch (type) {
            case "image" -> "seedream-5.0";
            case "video" -> "seedance-2.0";
            case "audio" -> "volcano-tts";
            default -> "deepseek-v3";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null) return Map.of();
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    private String toJson(Object obj) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}
