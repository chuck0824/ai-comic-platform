package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * M1: Three-Agent review adapted for V7 content units.
 * Hook Agent (40%), Director/Editor Agent (35%), Production Agent (25%).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentReviewService {

    private final ContentUnitMapper unitMapper;
    private final ContentVersionSelector versionSelector;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    static final String HOOK_SYSTEM_PROMPT = """
        你是一位资深的短剧钩子分析师。请从以下维度审核剧本钩子质量：
        1. 开场钩子（是否在3秒内抓住注意力）
        2. 每集结尾悬念（是否制造足够的期待）
        3. 反转设计（是否出人意料但合理）
        4. 情感共鸣（是否让观众产生代入感）

        请给出每个维度的评分（0-100）和具体建议。输出JSON格式：
        {"hook_score": 85, "strengths": [...], "weaknesses": [...], "suggestions": [...]}
        """;

    static final String DIRECTOR_SYSTEM_PROMPT = """
        你是一位资深的剧本导演/编辑。请从以下维度审核剧本：
        1. 剧情逻辑一致性（人物行为是否符合设定）
        2. 冲突递进（冲突是否逐级升级）
        3. 对白质量（是否符合人物性格，是否推进剧情）
        4. 节奏把控（信息密度和情绪节奏是否合理）

        请给出每个维度的评分（0-100）和具体建议。输出JSON格式：
        {"director_score": 80, "logic_issues": [...], "dialogue_notes": [...], "pacing_notes": [...]}
        """;

    static final String PRODUCTION_SYSTEM_PROMPT = """
        你是一位资深的影视制片。请从以下维度审核剧本可拍性：
        1. 场景数量与复杂度（是否在预算范围内）
        2. 视觉描述充分性（是否为导演提供足够的画面信息）
        3. 特殊效果需求（是否需要CGI/特效）
        4. 单集时长估算（对白量和动作量的平衡）

        请给出每个维度的评分（0-100）和具体建议。输出JSON格式：
        {"production_score": 75, "scene_notes": [...], "feasibility": "high|medium|low", "estimated_duration_sec": 120}
        """;

    public Map<String, Object> reviewUnit(Long unitId) {
        ContentUnit unit = unitMapper.selectById(unitId);
        if (unit == null) return Map.of("error", "unit_not_found");

        // Get latest version content
        ContentVersion version = versionSelector.resolvePublic(unit);

        String content = "";
        if (version != null) {
            content = version.getPlainText();
            if (content == null || content.isBlank()) content = version.getContentJson();
        }
        if (content == null || content.isBlank()) {
            return Map.of("error", "no_content", "message", "没有可审核的内容");
        }

        // Run three agents in sequence (can be parallelized with @Async in future)
        Map<String, Object> hookResult = runAgent(HOOK_SYSTEM_PROMPT, content, "hook");
        Map<String, Object> directorResult = runAgent(DIRECTOR_SYSTEM_PROMPT, content, "director");
        Map<String, Object> productionResult = runAgent(PRODUCTION_SYSTEM_PROMPT, content, "production");

        // Aggregate
        int hookScore = extractScore(hookResult, "hook_score");
        int directorScore = extractScore(directorResult, "director_score");
        int productionScore = extractScore(productionResult, "production_score");

        double weightedScore = hookScore * 0.40 + directorScore * 0.35 + productionScore * 0.25;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("unit_id", unitId);
        report.put("weighted_score", Math.round(weightedScore));
        report.put("hook", Map.of("score", hookScore, "weight", 0.40, "details", hookResult));
        report.put("director", Map.of("score", directorScore, "weight", 0.35, "details", directorResult));
        report.put("production", Map.of("score", productionScore, "weight", 0.25, "details", productionResult));
        report.put("verdict", weightedScore >= 70 ? "approved" : "needs_revision");
        report.put("reviewed_at", java.time.LocalDateTime.now().toString());

        return report;
    }

    private Map<String, Object> runAgent(String systemPrompt, String content, String agentType) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("system_prompt", systemPrompt);
            params.put("prompt", "请审核以下剧本内容：\n\n" + ellipsis(content, 4000));
            params.put("temperature", 0.3);
            params.put("max_tokens", 2048);

            Map<String, Object> result = aiRouter.chatCompletion(params);
            String text = extractText(result);
            return parseJson(text);
        } catch (Exception e) {
            log.warn("{} agent review failed, using fallback", agentType, e);
            return Map.of("score", 75, "note", "AI审核暂不可用，使用默认评分");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> result) {
        Object choices = result.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map) {
                Object message = ((Map<String, Object>) first).get("message");
                if (message instanceof Map) {
                    Object content = ((Map<String, Object>) message).get("content");
                    if (content != null) return String.valueOf(content);
                }
            }
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String text) {
        try {
            String json = text;
            if (text.contains("```json")) {
                int s = text.indexOf("```json") + 7;
                int e = text.indexOf("```", s);
                if (e > s) json = text.substring(s, e).trim();
            }
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("raw", text);
        }
    }

    private int extractScore(Map<String, Object> result, String key) {
        Object val = result.get(key);
        if (val instanceof Number n) return n.intValue();
        Object score = result.get("score");
        if (score instanceof Number n) return n.intValue();
        return 75; // default
    }

    private String ellipsis(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
