package com.aicp.module.script.service;

import com.aicp.module.script.entity.*;
import com.aicp.module.script.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HookService {

    private final EpisodeHookMapper hookMapper;
    private final ScriptEpisodeMapper episodeMapper;
    private final ScriptMapper scriptMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 钩子生成 System Prompt */
    private static final String HOOK_SYSTEM_PROMPT = """
            你是一位专业的短剧钩子/悬念策略师。你的任务是为短剧的每一集设计开场钩子和结尾悬念。

            ## 钩子类型定义
            - 开场钩子(opening): 前3-5秒抓住观众注意力的情节/对话/画面
            - 结尾悬念(closing): 集末留下的未解问题、危机、误会或反转暗示
            - 反转(reversal): 剧情中关键的身份/关系/认知反转点
            - 伏笔(plant): 为后续集数埋设的线索

            ## 评分标准 (0.0-1.0)
            - 0.8-1.0: 强钩子 -- 观众几乎必须看下一集
            - 0.5-0.8: 中等钩子 -- 有吸引力但可等待
            - 0.0-0.5: 弱钩子 -- 缺乏紧迫感

            ## 输出格式
            请以JSON格式输出，不要有其他文字：
            {
              "hooks": [
                {"type": "opening", "content": "...", "position": 0},
                {"type": "reversal", "content": "...", "position": 350},
                {"type": "closing", "content": "...", "position": -1}
              ],
              "strength_analysis": {
                "opening_score": 0.85,
                "closing_score": 0.90,
                "reversal_count": 2,
                "overall_assessment": "结尾悬念强烈，观众必然想看下一集"
              }
            }""";

    /**
     * 为单集生成钩子
     */
    @Async("genTaskExecutor")
    public void generateHooksForEpisode(Long episodeId) {
        ScriptEpisode episode = episodeMapper.selectById(episodeId);
        if (episode == null || episode.getContent() == null) return;

        Script script = scriptMapper.selectById(episode.getScriptId());
        if (script == null) return;

        try {
            String userPrompt = buildHookUserPrompt(script, episode);
            Map<String, Object> aiResult = callAiForHooks(userPrompt);

            // 解析 AI 结果
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hooks = (List<Map<String, Object>>) aiResult.getOrDefault("hooks", List.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> analysis = (Map<String, Object>) aiResult.getOrDefault("strength_analysis", Map.of());

            // 删除旧钩子
            hookMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EpisodeHook>()
                    .eq(EpisodeHook::getEpisodeId, episodeId));

            // 插入新钩子
            double totalScore = 0;
            int count = 0;
            for (Map<String, Object> h : hooks) {
                EpisodeHook hook = new EpisodeHook();
                hook.setEpisodeId(episodeId);
                hook.setScriptId(script.getId());
                hook.setHookType(String.valueOf(h.getOrDefault("type", "closing")));
                hook.setContent(String.valueOf(h.getOrDefault("content", "")));
                hook.setPosition(toInt(h.get("position"), 0));
                hook.setStatus("draft");
                hookMapper.insert(hook);
                count++;

                // 从分析中取评分
                String type = hook.getHookType();
                if ("opening".equals(type)) {
                    hook.setStrengthScore(toDouble(analysis.get("opening_score")));
                } else if ("closing".equals(type)) {
                    hook.setStrengthScore(toDouble(analysis.get("closing_score")));
                }
                totalScore += hook.getStrengthScore() != null ? hook.getStrengthScore() : 0.5;
            }

            // 更新 episode 缓存
            double avgScore = count > 0 ? totalScore / count : 0;
            episode.setHookScoreAvg(Math.round(avgScore * 100.0) / 100.0);
            episode.setHookCount(count);
            episodeMapper.updateById(episode);

            log.info("钩子生成完成: episodeId={}, hooks={}, avgScore={}", episodeId, count, avgScore);

        } catch (Exception e) {
            log.warn("钩子生成失败(使用模拟数据): episodeId={}, error={}", episodeId, e.getMessage());
            generateMockHooks(episodeId);
        }
    }

    /**
     * 批量生成所有集的钩子
     */
    public void generateAllHooks(Long scriptId) {
        List<ScriptEpisode> episodes = episodeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScriptEpisode>()
                        .eq(ScriptEpisode::getScriptId, scriptId)
                        .orderByAsc(ScriptEpisode::getEpisodeNumber));
        for (ScriptEpisode ep : episodes) {
            generateHooksForEpisode(ep.getId());
        }
    }

    /**
     * 获取剧本的钩子摘要
     */
    public Map<String, Object> getHookSummary(Long scriptId) {
        List<EpisodeHook> allHooks = hookMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EpisodeHook>()
                        .eq(EpisodeHook::getScriptId, scriptId)
                        .orderByAsc(EpisodeHook::getEpisodeId)
                        .orderByAsc(EpisodeHook::getHookType));

        // 按集分组
        Map<Long, List<Map<String, Object>>> byEpisode = new LinkedHashMap<>();
        double totalScore = 0;
        int totalCount = 0;
        for (EpisodeHook h : allHooks) {
            Map<String, Object> hookMap = new LinkedHashMap<>();
            hookMap.put("id", h.getId());
            hookMap.put("hook_type", h.getHookType());
            hookMap.put("content", h.getContent());
            hookMap.put("strength_score", h.getStrengthScore());
            hookMap.put("strength_reason", h.getStrengthReason());
            hookMap.put("status", h.getStatus());

            byEpisode.computeIfAbsent(h.getEpisodeId(), k -> new ArrayList<>()).add(hookMap);
            if (h.getStrengthScore() != null) {
                totalScore += h.getStrengthScore();
                totalCount++;
            }
        }

        double aggregateScore = totalCount > 0 ? Math.round(totalScore / totalCount * 100.0) / 100.0 : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("script_id", scriptId);
        result.put("hooks_by_episode", byEpisode);
        result.put("total_hooks", allHooks.size());
        result.put("aggregate_score", aggregateScore);
        result.put("assessment", scoreToAssessment(aggregateScore));
        return result;
    }

    // ===== 内部方法 =====

    private String buildHookUserPrompt(Script script, ScriptEpisode episode) {
        return String.format("""
                剧本信息:
                - 标题: %s
                - 题材: %s

                第%d集剧本:
                %s

                请为这一集设计钩子，并评分。""",
                script.getTitle() != null ? script.getTitle() : "未命名",
                script.getGenreTag() != null ? script.getGenreTag() : "未知",
                episode.getEpisodeNumber() != null ? episode.getEpisodeNumber() : 1,
                episode.getContent() != null ? episode.getContent().substring(0, Math.min(2000, episode.getContent().length())) : "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callAiForHooks(String userPrompt) {
        // TODO: 接入 AiRouter 的 LLM 调用（待 AiRouter 支持通用 chat 方法）
        log.debug("钩子系统暂使用 mock 数据。Prompt: {}", userPrompt.substring(0, Math.min(200, userPrompt.length())));
        return mockAiResponse();
    }

    /** 从 AI 响应中提取 JSON */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    /** 模拟 AI 钩子生成（dev fallback） */
    private Map<String, Object> mockAiResponse() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> hooks = List.of(
                Map.of("type", "opening", "content", "闪回上一集结尾的危机画面，主角面临两难抉择", "position", 0),
                Map.of("type", "reversal", "content", "原本以为是敌人的人，突然出手相助，揭示隐藏身份", "position", 350),
                Map.of("type", "closing", "content", "主角刚松了一口气，却发现更大的阴谋正在暗中展开", "position", -1)
        );
        result.put("hooks", hooks);
        result.put("strength_analysis", Map.of(
                "opening_score", 0.85,
                "closing_score", 0.90,
                "reversal_count", 1,
                "overall_assessment", "结尾悬念强烈，观众必然想看下一集"
        ));
        return result;
    }

    /** 快速生成 mock 钩子（不调用 AI） */
    private void generateMockHooks(Long episodeId) {
        ScriptEpisode episode = episodeMapper.selectById(episodeId);
        if (episode == null) return;

        hookMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EpisodeHook>()
                .eq(EpisodeHook::getEpisodeId, episodeId));

        Script script = scriptMapper.selectById(episode.getScriptId());
        Long scriptId = script != null ? script.getId() : null;

        List<Map<String, String>> mockHooks = List.of(
                Map.of("type", "opening", "content", "闪回上一集结尾的危机画面，主角面临关键抉择"),
                Map.of("type", "closing", "content", "主角刚松了一口气，新的危机悄然降临")
        );

        for (Map<String, String> h : mockHooks) {
            EpisodeHook hook = new EpisodeHook();
            hook.setEpisodeId(episodeId);
            hook.setScriptId(scriptId);
            hook.setHookType(h.get("type"));
            hook.setContent(h.get("content"));
            hook.setStrengthScore(0.75 + Math.random() * 0.15);
            hook.setPosition("opening".equals(h.get("type")) ? 0 : -1);
            hook.setStatus("draft");
            hookMapper.insert(hook);
        }

        episode.setHookScoreAvg(0.80);
        episode.setHookCount(mockHooks.size());
        episodeMapper.updateById(episode);
    }

    private String scoreToAssessment(double score) {
        if (score >= 0.8) return "钩子强度优秀，观众粘性高";
        if (score >= 0.5) return "钩子强度中等，建议优化结尾悬念";
        return "钩子偏弱，建议强化开场和结尾";
    }

    private int toInt(Object v, int fallback) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); }
        catch (Exception e) { return fallback; }
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); }
        catch (Exception e) { return 0.5; }
    }
}
