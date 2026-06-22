package com.aicp.module.script.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.script.entity.GenTask;
import com.aicp.module.script.mapper.GenTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptGenService {

    private final GenTaskMapper genTaskMapper;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> createGenTask(String genType, Map<String, Object> params) {
        GenTask task = new GenTask();
        task.setUserId(SecurityUtil.requireCurrentUserId());
        task.setGenType(genType);
        task.setInputParams(toJson(params));
        task.setStatus("pending");
        task.setTokensUsed(0);
        task.setDurationMs(0);

        String tier = params.containsKey("tier") ? params.get("tier").toString() : null;
        if (tier != null) task.setStoryboardTier(tier);

        genTaskMapper.insert(task);

        // 异步执行生成（调用AI Router）
        simulateGeneration(task.getId(), genType, params, tier);

        return Map.of("task_id", task.getId(), "status", "pending",
                "estimated_seconds", genType.equals("quick") ? 120 : 30);
    }

    @Async
    public void simulateGeneration(Long taskId, String genType, Map<String, Object> params, String tier) {
        try {
            GenTask task = genTaskMapper.selectById(taskId);
            if (task == null) return;
            task.setStatus("processing");
            genTaskMapper.updateById(task);

            // 通过 AI Router 调用 LLM
            Map<String, Object> aiParams = new LinkedHashMap<>(params);
            aiParams.put("gen_type", genType);
            aiParams.put("system_prompt", buildSystemPrompt(genType, params));
            aiParams.put("prompt", params.getOrDefault("idea", params.getOrDefault("synopsis", "")));
            aiParams.put("temperature", 0.7);
            aiParams.put("max_tokens", 4096);

            task.setPromptUsed(String.valueOf(aiParams.get("system_prompt")) + "\n\n" + aiParams.get("prompt"));

            // 尝试调用真实 AI，失败时回退到模拟数据
            boolean aiSuccess = false;
            try {
                Map<String, Object> aiResult = aiRouter.chatCompletion(aiParams);
                if (aiResult != null && !aiResult.isEmpty()) {
                    parseAndSaveResult(task, genType, params, tier, aiResult);
                    aiSuccess = true;
                    task.setModelUsed(String.valueOf(aiResult.getOrDefault("model", "ai-router")));
                    log.info("AI 生成成功: taskId={}, genType={}", taskId, genType);
                }
            } catch (Exception aiEx) {
                log.warn("AI 调用失败，回退到模拟数据: taskId={}, genType={}, error={}",
                        taskId, genType, aiEx.getMessage());
            }

            if (!aiSuccess) {
                simulateResult(task, genType, params, tier);
                task.setModelUsed("mock-fallback");
            }

        } catch (Exception e) {
            log.error("生成任务失败: taskId={}", taskId, e);
            GenTask task = genTaskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus("failed");
                task.setErrorMsg(e.getMessage());
                genTaskMapper.updateById(task);
            }
        }
    }

    /**
     * 解析 AI 返回结果并保存到 task
     */
    @SuppressWarnings("unchecked")
    private void parseAndSaveResult(GenTask task, String genType, Map<String, Object> params,
                                     String tier, Map<String, Object> aiResult) {
        // 从 AI 响应中提取文本内容
        String content = extractTextContent(aiResult);
        Map<String, Object> result = new LinkedHashMap<>();

        if (genType.equals("topic")) {
            // 尝试解析选题列表，失败则用简单拆分
            List<Map<String, Object>> suggestions = parseTopicSuggestions(content);
            result.put("suggestions", suggestions);
        } else {
            // 通用：将 AI 返回文本填入对应字段
            result.put("title", params.getOrDefault("title",
                    extractFirstLine(content).replaceAll("^#+\\s*", "")));
            result.put("synopsis", content);
            result.put("raw", content);
            result.put("worldBuilding", "");
            result.put("plotPhases", List.of("", "", "", ""));
            result.put("coreConflict", "");
            result.put("highlights", List.of());
        }

        if (tier != null) result.put("tier", tier);
        result.put("tags", Map.of(
                "genre", params.getOrDefault("genre_tag", ""),
                "plot", params.getOrDefault("plot_tags", List.of()),
                "tone", params.getOrDefault("tone_tags", List.of()),
                "setting", params.getOrDefault("setting_tag", "")));

        task.setOutputData(toJson(result));
        task.setStatus("completed");
        task.setProgress(100);
        task.setTokensUsed(estimateTokens(content));
        task.setDurationMs(2500);
        task.setCompletedAt(new Date());
        genTaskMapper.updateById(task);
    }

    /** 从 AI 响应中提取文本内容 */
    @SuppressWarnings("unchecked")
    private String extractTextContent(Map<String, Object> aiResult) {
        // new-api 标准格式: choices[0].message.content
        Object choices = aiResult.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> msg) {
                    Object content = msg.get("content");
                    if (content != null) return String.valueOf(content);
                }
            }
        }
        // 回退: 直接取 content 或 text 字段
        Object content = aiResult.get("content");
        if (content != null) return String.valueOf(content);
        Object text = aiResult.get("text");
        if (text != null) return String.valueOf(text);
        return aiResult.toString();
    }

    /** 从 AI 文本中解析选题建议 */
    private List<Map<String, Object>> parseTopicSuggestions(String content) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        // 按数字序号或 ## 标题拆分
        String[] lines = content.split("\n");
        String currentTitle = null;
        StringBuilder currentDesc = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            // 匹配 "1. xxx" 或 "方案1：xxx" 或 "## xxx"
            if (trimmed.matches("^\\d+[.、．]\\s*.+") ||
                trimmed.matches("^方案\\s*\\d+[：:].+") ||
                trimmed.matches("^#{1,3}\\s+.+")) {
                // 保存上一个
                if (currentTitle != null) {
                    suggestions.add(Map.of(
                            "title", currentTitle,
                            "description", currentDesc.toString().trim(),
                            "match_rate", 85));
                }
                currentTitle = trimmed.replaceAll("^\\d+[.、．]\\s*", "")
                                      .replaceAll("^方案\\s*\\d+[：:]\\s*", "")
                                      .replaceAll("^#{1,3}\\s*", "");
                currentDesc = new StringBuilder();
            } else if (!trimmed.isEmpty() && currentTitle != null) {
                if (currentDesc.length() > 0) currentDesc.append("\n");
                currentDesc.append(trimmed);
            }
        }
        // 保存最后一个
        if (currentTitle != null) {
            suggestions.add(Map.of(
                    "title", currentTitle,
                    "description", currentDesc.toString().trim(),
                    "match_rate", 85));
        }

        // 如果没解析出任何内容，做简单拆分
        if (suggestions.isEmpty() && !content.isBlank()) {
            String[] parts = content.split("\n\n");
            for (int i = 0; i < Math.min(parts.length, 5); i++) {
                String part = parts[i].trim();
                if (!part.isEmpty()) {
                    suggestions.add(Map.of(
                            "title", extractFirstLine(part),
                            "description", part,
                            "match_rate", 90 - i * 5));
                }
            }
        }
        return suggestions;
    }

    private String extractFirstLine(String text) {
        if (text == null || text.isBlank()) return "";
        return text.split("\n")[0].trim();
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        // 粗略估算：中文约 1.5 字符/token，英文约 4 字符/token
        return text.length() * 2 / 3;
    }

    private void simulateResult(GenTask task, String genType, Map<String, Object> params, String tier) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("script_id", 12345);
        result.put("title", "霸道总裁的替身新娘");

        if (genType.equals("quick") || genType.equals("synopsis")) {
            result.put("synopsis", "她是被家族抛弃的私生女，意外成为权势滔天的商业帝王唯一在意的女人...");
            result.put("episode_count", params.getOrDefault("episode_count", 40));
            result.put("worldBuilding", "现代都市豪门与底层职场交织，资本家族以婚姻和继承权维系权力。");
            result.put("plotPhases", List.of("误入豪门局", "身份与情感拉扯", "旧伤疤暴露真相", "女主掌握主动权"));
            result.put("coreConflict", "女主想保住自我与秘密，男主试图查明她与旧案的关系。");
            result.put("highlights", List.of("身份反转", "情感博弈", "结尾强钩子"));
        }
        if (genType.equals("topic")) {
            result.put("suggestions", List.of(
                      Map.of("title", "豪门身份反转", "match_rate", 95),
                      Map.of("title", "重生逆袭", "match_rate", 88),
                      Map.of("title", "先婚后爱", "match_rate", 82)));
        }
        if (genType.equals("quick") || genType.equals("outline")) {
            int count = toInt(params.get("episode_count"), 12);
            List<Map<String, Object>> episodes = new ArrayList<>();
            for (int i = 1; i <= Math.min(count, 12); i++) {
                episodes.add(Map.of(
                        "number", i,
                        "title", "第" + i + "集 · 身份裂缝",
                        "coreEvent", "女主在第" + i + "次试探中暴露一处关键信息，男主加深怀疑。",
                        "openingHook", "一个陌生来电打断平静。",
                        "closingHook", "男主看到女主手腕上的旧疤。"));
            }
            result.put("episodes", episodes);
        }
        if (genType.equals("quick") || genType.equals("episode")) {
            result.put("raw", """
                    [场景1 办公室 日 内]
                    △ 苏小晚端着咖啡走进办公室，刻意避开林默的视线。
                    林默：你以前在哪家公司做过？
                    苏小晚：一家很小的公司，说了你也不会知道。
                    △ 林默的目光落在她手腕的旧疤上，空气忽然安静。
                    【旁白】：有些秘密越想藏，越会在细节里发光。
                    """);
        }
        if (genType.equals("quick") || genType.equals("storyboard") || tier != null) {
            result.put("tier", tier != null ? tier : "A");
            result.put("shot_count", 18);
            result.put("estimated_duration", 75);
            result.put("shots", List.of(
                    Map.of("shotNo", "SH001", "duration", "3s", "shotSize", "MS", "cameraMove", "跟拍",
                            "visual", "苏小晚端咖啡进入办公室", "dialogue", "—", "function", "建立场景"),
                    Map.of("shotNo", "SH002", "duration", "5s", "shotSize", "MCU", "cameraMove", "固定",
                            "visual", "林默抬头审视苏小晚", "dialogue", "林默：之前在哪工作？", "function", "试探"),
                    Map.of("shotNo", "SH003", "duration", "4s", "shotSize", "CU", "cameraMove", "特写",
                            "visual", "苏小晚手指紧握咖啡杯", "dialogue", "苏小晚：一家小公司。", "function", "隐藏"),
                    Map.of("shotNo", "SH004", "duration", "6s", "shotSize", "MS", "cameraMove", "缓推",
                            "visual", "林默注意到她手腕上的旧疤", "dialogue", "林默：这道伤是怎么来的？", "function", "发现钩子")));
        }
        if (genType.equals("quick") || genType.equals("promotion")) {
            result.put("titles", List.of("她只是替身，豪门继承人却慌了", "手腕旧疤暴露后，总裁连夜查她身份", "被抛弃的她，成了他唯一破例"));
            result.put("coverCopy", List.of("替身新娘", "旧疤藏着真相", "他开始怀疑她"));
            result.put("threeSecHooks", List.of("总裁盯着她的手腕，脸色瞬间变了。", "她以为藏住了身份，却漏掉一道伤疤。"));
            result.put("clipScripts", List.of("0-3s 旧疤特写；3-8s 男主质问；8-15s 女主回避；15s 留悬念。"));
        }

        result.put("tags", Map.of("genre", "言情", "plot", List.of("重生", "先婚后爱"),
                "tone", List.of("甜宠", "打脸"), "setting", "现代"));

        task.setOutputData(toJson(result));
        task.setStatus("completed");
        task.setProgress(100);
        task.setTokensUsed(45000);
        task.setDurationMs(2500);
        task.setModelUsed("mock-fallback");
        task.setCompletedAt(new Date());
        genTaskMapper.updateById(task);
    }

    private String buildSystemPrompt(String genType, Map<String, Object> params) {
        return switch (genType) {
            case "topic" -> "你是一位资深的短剧编剧。请根据创意生成3-5个爆款选题方案。";
            case "synopsis" -> "你是一位资深短剧编剧。请根据选题生成500字故事梗概。";
            case "outline" -> "你是一位资深短剧编剧。请根据梗概生成" + params.getOrDefault("episode_count", 40) + "集分集大纲。";
            case "episode" -> "你是一位资深短剧编剧。请根据大纲生成单集完整剧本。";
            case "storyboard" -> "你是一位资深导演。请根据剧本生成分镜脚本。";
            default -> "你是一位AI创作助手。请根据输入生成内容。";
        };
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return fallback; }
    }

    public Map<String, Object> getTaskStatus(String taskId) {
        GenTask task;
        try {
            task = genTaskMapper.selectById(Long.parseLong(taskId));
        } catch (NumberFormatException e) {
            task = genTaskMapper.selectOne(
                    new LambdaQueryWrapper<GenTask>().eq(GenTask::getGenType, taskId));
        }
        if (task == null) {
            return Map.of("error", "任务不存在: " + taskId);
        }
        return toMap(task);
    }

    public Map<String, Object> getTaskHistory(int page, int pageSize) {
        Long userId = SecurityUtil.requireCurrentUserId();
        Page<GenTask> result = genTaskMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<GenTask>()
                        .eq(GenTask::getUserId, userId)
                        .orderByDesc(GenTask::getCreatedAt));
        return Map.of(
                "items", result.getRecords().stream().map(this::toMap).toList(),
                "pagination", Map.of("page", page, "page_size", pageSize,
                        "total", result.getTotal(), "has_more", result.hasNext()));
    }

    private Map<String, Object> toMap(GenTask task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("task_id", task.getId());
        map.put("gen_type", task.getGenType());
        map.put("storyboard_tier", task.getStoryboardTier());
        map.put("status", task.getStatus());
        map.put("progress", task.getProgress() != null ? task.getProgress() : 0);
        map.put("input_params", parseJson(task.getInputParams()));
        map.put("result", parseJson(task.getOutputData()));
        map.put("tokens_used", task.getTokensUsed());
        map.put("duration_ms", task.getDurationMs());
        map.put("error_msg", task.getErrorMsg());
        map.put("created_at", task.getCreatedAt());
        map.put("completed_at", task.getCompletedAt());
        return map;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null) return Map.of();
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }
}
