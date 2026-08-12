package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentUnitHook;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentUnitHookMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * M2: Hook analysis per content unit.
 * Generates 6 hook types with scoring (0-100) using AI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentHookService {

    private final ContentUnitHookMapper hookMapper;
    private final ContentUnitMapper unitMapper;
    private final ContentVersionSelector versionSelector;
    private final AiRouter aiRouter;
    private final AiResponseParser parser;

    static final String HOOK_SYSTEM_PROMPT = """
        你是一位资深的短剧钩子分析师。请分析以下剧本内容，提取7类钩子并评分(0-100)：

        1. previous_promise — 上一集留下的承诺/期待
        2. promise_payoff — 本集兑现了上一集的哪些承诺
        3. opening_hook — 本集开场3秒钩子
        4. mid_escalation — 中间升级/转折
        5. payoff_or_reversal — 本集结尾的反转/回报
        6. closing_hook — 本集结尾为下一集留下的悬念
        7. next_promise — 本集为下一集设定的新期待/钩子

        输出JSON：
        {
          "previous_promise": "上集承诺",
          "promise_payoff": "兑现情况",
          "opening_hook": "开场钩子",
          "mid_escalation": "中间升级",
          "payoff_or_reversal": "结尾反差",
          "closing_hook": "结尾悬念",
          "next_promise": "为下集设定的期待",
          "hook_score": 85
        }
        """;

    @Transactional
    public ContentUnitHook generateHooks(Long unitId) {
        ContentUnit unit = unitMapper.selectById(unitId);
        if (unit == null) return null;

        ContentVersion version = versionSelector.resolvePublic(unit);
        if (version == null || version.getPlainText() == null || version.getPlainText().isBlank()) {
            return null;
        }

        // Delete old hooks for regeneration
        ContentUnitHook existing = hookMapper.selectOne(
                new LambdaQueryWrapper<ContentUnitHook>()
                        .eq(ContentUnitHook::getContentUnitId, unitId));
        if (existing != null) hookMapper.deleteById(existing.getId());

        // Call AI
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("system_prompt", HOOK_SYSTEM_PROMPT);
        params.put("prompt", "请分析以下剧本内容的钩子：\n\n" + parser.ellipsis(version.getPlainText(), 4000));
        params.put("temperature", 0.3);
        params.put("max_tokens", 2048);

        Map<String, Object> result = aiRouter.chatCompletion(params);
        String text = parser.extractText(result);
        Map<String, Object> parsed = parser.parseJson(text);

        ContentUnitHook hook = new ContentUnitHook();
        hook.setContentUnitId(unitId);
        hook.setContentVersionId(version.getId());
        hook.setPreviousPromise(parser.str(parsed.get("previous_promise")));
        hook.setPromisePayoff(parser.str(parsed.get("promise_payoff")));
        hook.setOpeningHook(parser.str(parsed.get("opening_hook")));
        hook.setMidEscalation(parser.str(parsed.get("mid_escalation")));
        hook.setPayoffOrReversal(parser.str(parsed.get("payoff_or_reversal")));
        hook.setClosingHook(parser.str(parsed.get("closing_hook")));
        hook.setNextPromise(parser.str(parsed.get("next_promise")));
        hook.setHookScore(parser.toDouble(parsed.get("hook_score"), 75.0));
        hook.setLockedFields("[]");
        hookMapper.insert(hook);

        return hook;
    }

    @Transactional
    public int generateAllHooks(Long projectId) {
        List<ContentUnit> units = unitMapper.selectList(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getUnitType, "episode")
                        .eq(ContentUnit::getIsDeleted, 0)
                        .orderByAsc(ContentUnit::getDisplayNo));
        int count = 0;
        for (ContentUnit u : units) {
            try {
                ContentUnitHook h = generateHooks(u.getId());
                if (h != null) count++;
            } catch (Exception e) {
                log.warn("Hook generation failed for unit {}", u.getId(), e);
            }
        }
        return count;
    }

    public ContentUnitHook getHooks(Long unitId) {
        return hookMapper.selectOne(
                new LambdaQueryWrapper<ContentUnitHook>()
                        .eq(ContentUnitHook::getContentUnitId, unitId));
    }

    public Map<String, Object> getHookSummary(Long projectId) {
        List<ContentUnit> units = unitMapper.selectList(
                new LambdaQueryWrapper<ContentUnit>()
                        .eq(ContentUnit::getProjectId, projectId)
                        .eq(ContentUnit::getUnitType, "episode")
                        .eq(ContentUnit::getIsDeleted, 0)
                        .orderByAsc(ContentUnit::getDisplayNo));

        List<Map<String, Object>> summaries = new ArrayList<>();
        double totalScore = 0;
        int scored = 0;

        for (ContentUnit u : units) {
            ContentUnitHook h = getHooks(u.getId());
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("unit_id", u.getId());
            s.put("display_no", u.getDisplayNo());
            s.put("title", u.getTitle());
            if (h != null) {
                s.put("hook_score", h.getHookScore());
                s.put("opening_hook", h.getOpeningHook());
                s.put("closing_hook", h.getClosingHook());
                if (h.getHookScore() != null) { totalScore += h.getHookScore(); scored++; }
            }
            summaries.add(s);
        }

        return Map.of(
                "units", summaries,
                "average_score", scored > 0 ? Math.round(totalScore / scored * 10.0) / 10.0 : 0,
                "total_units", units.size(),
                "scored_units", scored);
    }
}
