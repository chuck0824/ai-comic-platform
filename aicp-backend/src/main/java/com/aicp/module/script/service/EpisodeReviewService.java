package com.aicp.module.script.service;

import com.aicp.module.script.entity.EpisodeReviewReport;
import com.aicp.module.script.entity.Script;
import com.aicp.module.script.entity.ScriptEpisode;
import com.aicp.module.script.mapper.EpisodeReviewReportMapper;
import com.aicp.module.script.mapper.ScriptEpisodeMapper;
import com.aicp.module.script.mapper.ScriptMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EpisodeReviewService {

    private final EpisodeReviewReportMapper reportMapper;
    private final ScriptEpisodeMapper episodeMapper;
    private final ScriptMapper scriptMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> reviewPreview(Map<String, Object> body) {
        ReviewContext ctx = ReviewContext.fromBody(body);
        return buildReviewReport(ctx, false);
    }

    public Map<String, Object> reviewEpisode(Long episodeId, Map<String, Object> body) {
        ScriptEpisode episode = episodeMapper.selectById(episodeId);
        if (episode == null) {
            return Map.of("error", "episode_not_found", "message", "分集不存在");
        }
        Script script = scriptMapper.selectById(episode.getScriptId());
        ReviewContext ctx = ReviewContext.fromEpisode(script, episode, body);
        Map<String, Object> report = buildReviewReport(ctx, true);

        EpisodeReviewReport entity = new EpisodeReviewReport();
        entity.setScriptId(ctx.scriptId);
        entity.setEpisodeId(ctx.episodeId);
        entity.setEpisodeNumber(ctx.episodeNumber);
        entity.setOverallStatus(String.valueOf(report.get("overall_status")));
        entity.setOverallScore(asDouble(report.get("overall_score")));
        entity.setHookScore(asDouble(report.get("hook_score")));
        entity.setShowrunnerScore(asDouble(report.get("showrunner_score")));
        entity.setDirectorScore(asDouble(report.get("director_score")));
        entity.setReportJson(toJson(report));
        reportMapper.insert(entity);
        report.put("report_id", entity.getId());
        return report;
    }

    public Map<String, Object> getLatestReport(Long episodeId) {
        EpisodeReviewReport report = reportMapper.selectOne(
                new LambdaQueryWrapper<EpisodeReviewReport>()
                        .eq(EpisodeReviewReport::getEpisodeId, episodeId)
                        .orderByDesc(EpisodeReviewReport::getCreatedAt)
                        .last("LIMIT 1"));
        if (report == null) return Map.of("episode_id", episodeId, "status", "not_reviewed");
        Map<String, Object> parsed = parseJson(report.getReportJson());
        parsed.put("report_id", report.getId());
        parsed.put("overall_status", report.getOverallStatus());
        return parsed;
    }

    public Map<String, Object> approveEpisode(Long episodeId) {
        EpisodeReviewReport report = reportMapper.selectOne(
                new LambdaQueryWrapper<EpisodeReviewReport>()
                        .eq(EpisodeReviewReport::getEpisodeId, episodeId)
                        .orderByDesc(EpisodeReviewReport::getCreatedAt)
                        .last("LIMIT 1"));
        if (report == null) return Map.of("episode_id", episodeId, "status", "not_reviewed");
        report.setOverallStatus("approved");
        reportMapper.updateById(report);
        return Map.of("episode_id", episodeId, "report_id", report.getId(), "status", "approved");
    }

    private Map<String, Object> buildReviewReport(ReviewContext ctx, boolean persistable) {
        Map<String, Object> hookReview = reviewHook(ctx);
        Map<String, Object> showrunnerReview = reviewShowrunner(ctx);
        Map<String, Object> directorReview = reviewDirector(ctx);

        double hookScore = asDouble(hookReview.get("score"));
        double showrunnerScore = asDouble(showrunnerReview.get("score"));
        double directorScore = asDouble(directorReview.get("score"));
        double overallScore = round2(hookScore * 0.4 + showrunnerScore * 0.35 + directorScore * 0.25);
        String status = overallScore >= 0.78 ? "pass" : "needs_revision";

        List<Map<String, Object>> reviews = List.of(hookReview, showrunnerReview, directorReview);
        List<Map<String, Object>> keyIssues = reviews.stream()
                .flatMap(r -> castList(r.get("issues")).stream())
                .limit(6)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("script_id", ctx.scriptId);
        result.put("episode_id", ctx.episodeId);
        result.put("episode_number", ctx.episodeNumber);
        result.put("episode_title", ctx.title);
        result.put("persistable", persistable);
        result.put("overall_status", status);
        result.put("overall_score", overallScore);
        result.put("hook_score", hookScore);
        result.put("showrunner_score", showrunnerScore);
        result.put("director_score", directorScore);
        result.put("agent_reviews", reviews);
        result.put("key_issues", keyIssues);
        result.put("actions", List.of("optimize_hook", "optimize_dialogue", "compress_scenes", "approve"));
        result.put("created_at", LocalDateTime.now().toString());
        return result;
    }

    private Map<String, Object> reviewHook(ReviewContext ctx) {
        List<Map<String, Object>> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        double score = 0.35;

        if (hasText(ctx.openingHook)) score += 0.18;
        else addIssue(issues, "high", "opening_hook", "缺少开场钩子，前 3-5 秒抓人不足。");

        if (hasText(ctx.closingHook)) score += 0.18;
        else addIssue(issues, "high", "closing_hook", "缺少结尾悬念，下一集点击理由不足。");

        if (containsAny(ctx.content, "秘密", "真相", "发现", "曝光", "误会", "反转", "危机", "选择")) score += 0.12;
        else addIssue(issues, "medium", "content", "本集缺少明显信息差、秘密或危机。");

        if (containsAny(ctx.closingHook + ctx.content, "下一集", "突然", "刚要", "却", "发现", "门外", "电话", "照片")) score += 0.10;
        else suggestions.add("结尾建议使用未解问题、危险逼近或关键证据作为下一集承诺。");

        if (ctx.content.length() >= 180) score += 0.07;
        else addIssue(issues, "medium", "content", "剧本文本偏短，钩子兑现空间不足。");

        if (hasText(ctx.nextEpisodePromise)) score += 0.08;
        else suggestions.add("补充“下一集承诺”，说明用户为什么要继续看。");

        if (suggestions.isEmpty()) suggestions.add("保留当前开场和结尾钩子，中段可继续强化冲突升级。");

        score = clamp(score);
        return agentReview("hook", "钩子 Agent", score,
                score >= 0.78 ? "钩子承接基本成立。" : "钩子强度不足，需要补强开场、结尾或下一集承诺。",
                issues, suggestions);
    }

    private Map<String, Object> reviewShowrunner(ReviewContext ctx) {
        List<Map<String, Object>> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        double score = 0.35;

        if (hasText(ctx.coreEvent)) score += 0.12;
        else addIssue(issues, "medium", "core_event", "缺少本集核心事件，编导难以判断本集任务。");

        if (countDialogueLines(ctx.content) >= 2) score += 0.12;
        else addIssue(issues, "medium", "dialogue", "对白偏少，人物关系和情绪不够明确。");

        if (containsAny(ctx.content, "因为", "为了", "必须", "不能", "害怕", "想要")) score += 0.12;
        else addIssue(issues, "high", "motivation", "人物行动动机不够明确。");

        if (containsAny(ctx.content, "冲突", "质问", "反击", "拒绝", "威胁", "打脸", "争执", "逼问")) score += 0.14;
        else addIssue(issues, "high", "conflict", "中段冲突升级不足，容易变成平铺直叙。");

        if (containsAny(ctx.content, "沉默", "愣住", "眼神", "冷笑", "颤抖", "松了一口气", "脸色")) score += 0.08;
        else suggestions.add("增加人物反应和情绪转折，让观众感到关系变化。");

        if (ctx.content.length() >= 260) score += 0.07;
        else suggestions.add("单集正文偏短，建议补充冲突升级或情绪转折。");

        if (suggestions.isEmpty()) suggestions.add("保持当前人物关系推进，下一版可优化台词节奏。");

        score = clamp(score);
        return agentReview("showrunner", "编导 Agent", score,
                score >= 0.78 ? "故事逻辑和人物动机基本成立。" : "故事成立度不足，需要补强动机、冲突和情绪节奏。",
                issues, suggestions);
    }

    private Map<String, Object> reviewDirector(ReviewContext ctx) {
        List<Map<String, Object>> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        double score = 0.38;
        int sceneCount = estimateSceneCount(ctx.content);

        if (sceneCount <= 3) score += 0.12;
        else addIssue(issues, "medium", "scene_count", "场景数量偏多，AI 漫剧生产成本会上升。");

        if (containsAny(ctx.content, "△", "镜头", "特写", "近景", "远景", "推近", "定格", "画面")) score += 0.16;
        else addIssue(issues, "medium", "visual", "画面描述不足，后续分镜拆解会偏弱。");

        if (containsAny(ctx.content, "旧疤", "照片", "手机", "门外", "背影", "眼神", "黑卡", "血迹")) score += 0.10;
        else suggestions.add("为关键钩子增加可视化物件或特写，例如照片、手机消息、伤疤、门外身影。");

        if (containsAny(ctx.content, "群演", "万人", "爆炸", "战争", "复杂打斗", "追车", "大规模")) {
            addIssue(issues, "high", "production_risk", "存在高成本或高难度 AI 生成画面，建议简化。");
            score -= 0.12;
        } else {
            score += 0.08;
        }

        if (hasText(ctx.closingHook)) score += 0.08;
        else suggestions.add("结尾需要一个可被分镜强调的停顿画面。");

        if (suggestions.isEmpty()) suggestions.add("可在分镜阶段突出开场特写和结尾定格，强化追更。");

        score = clamp(score);
        return agentReview("director", "导演 Agent", score,
                score >= 0.78 ? "画面表达和生产可行性基本达标。" : "画面和生产落地存在风险，需要补充视觉点或压缩场景。",
                issues, suggestions);
    }

    private Map<String, Object> agentReview(String type, String name, double score, String summary,
                                            List<Map<String, Object>> issues, List<String> suggestions) {
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("agent_type", type);
        review.put("agent_name", name);
        review.put("score", round2(score));
        review.put("score_text", scoreText(score));
        review.put("status", score >= 0.78 ? "pass" : "needs_revision");
        review.put("summary", summary);
        review.put("issues", issues);
        review.put("suggestions", suggestions);
        return review;
    }

    private void addIssue(List<Map<String, Object>> issues, String severity, String position, String message) {
        issues.add(Map.of("severity", severity, "position", position, "message", message));
    }

    private int countDialogueLines(String text) {
        if (text == null || text.isBlank()) return 0;
        int count = 0;
        for (String line : text.split("\\R")) {
            if (line.contains("：") || line.contains(":")) count++;
        }
        return count;
    }

    private int estimateSceneCount(String text) {
        if (text == null || text.isBlank()) return 0;
        int count = 0;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[场景") || trimmed.startsWith("场景")) count++;
        }
        return count == 0 ? 1 : count;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) return false;
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private double clamp(double score) {
        return round2(Math.max(0.0, Math.min(1.0, score)));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? 0.0 : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private String scoreText(double score) {
        if (score >= 0.85) return "★★★★★";
        if (score >= 0.70) return "★★★★☆";
        if (score >= 0.50) return "★★★☆☆";
        if (score >= 0.30) return "★★☆☆☆";
        return "★☆☆☆☆";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    raw.forEach((k, v) -> map.put(String.valueOf(k), v));
                    result.add(map);
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try { return json == null ? new LinkedHashMap<>() : objectMapper.readValue(json, Map.class); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { return "{}"; }
    }

    private static class ReviewContext {
        Long scriptId;
        Long episodeId;
        Integer episodeNumber;
        String title;
        String content;
        String openingHook;
        String closingHook;
        String coreEvent;
        String nextEpisodePromise;
        String genreTag;
        String audienceMode;

        static ReviewContext fromBody(Map<String, Object> body) {
            ReviewContext ctx = new ReviewContext();
            ctx.scriptId = asLong(body.get("script_id"));
            ctx.episodeId = asLong(body.get("episode_id"));
            ctx.episodeNumber = asInt(body.get("episode_number"), 1);
            ctx.title = stringValue(body.get("title"), "当前集");
            ctx.content = stringValue(firstNonNull(body.get("content"), body.get("script_text")), "");
            ctx.openingHook = stringValue(firstNonNull(body.get("opening_hook"), body.get("openingHook")), "");
            ctx.closingHook = stringValue(firstNonNull(body.get("closing_hook"), body.get("closingHook")), "");
            ctx.coreEvent = stringValue(firstNonNull(body.get("core_event"), body.get("coreEvent")), "");
            ctx.nextEpisodePromise = stringValue(firstNonNull(body.get("next_episode_promise"), body.get("nextEpisodePromise")), "");
            ctx.genreTag = stringValue(firstNonNull(body.get("genre_tag"), body.get("genre")), "");
            ctx.audienceMode = stringValue(firstNonNull(body.get("audience_mode"), body.get("audience")), "");
            return ctx;
        }

        static ReviewContext fromEpisode(Script script, ScriptEpisode episode, Map<String, Object> body) {
            ReviewContext ctx = fromBody(body == null ? Map.of() : body);
            ctx.scriptId = episode.getScriptId();
            ctx.episodeId = episode.getId();
            ctx.episodeNumber = episode.getEpisodeNumber();
            ctx.title = hasValue(episode.getTitle()) ? episode.getTitle() : ctx.title;
            ctx.content = hasValue(episode.getContent()) ? episode.getContent() : ctx.content;
            ctx.openingHook = hasValue(episode.getOpeningHook()) ? episode.getOpeningHook() : ctx.openingHook;
            ctx.closingHook = hasValue(episode.getClosingHook()) ? episode.getClosingHook() : ctx.closingHook;
            if (script != null) ctx.genreTag = script.getGenreTag();
            return ctx;
        }

        private static Object firstNonNull(Object primary, Object fallback) {
            return primary != null ? primary : fallback;
        }

        private static String stringValue(Object value, String fallback) {
            return value == null ? fallback : String.valueOf(value);
        }

        private static boolean hasValue(String value) {
            return value != null && !value.isBlank();
        }

        private static Long asLong(Object value) {
            if (value instanceof Number n) return n.longValue();
            try { return value == null ? null : Long.parseLong(String.valueOf(value)); }
            catch (NumberFormatException e) { return null; }
        }

        private static Integer asInt(Object value, int fallback) {
            if (value instanceof Number n) return n.intValue();
            try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
            catch (NumberFormatException e) { return fallback; }
        }
    }
}
