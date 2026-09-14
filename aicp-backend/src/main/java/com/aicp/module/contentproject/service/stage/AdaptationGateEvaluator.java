package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdaptationGateEvaluator implements StageGateEvaluator {

    private final StageGateSupport support;

    @Override
    public ScriptStageKey stageKey() {
        return ScriptStageKey.ADAPTATION;
    }

    @Override
    public StageGateResult evaluate(ContentProject project) {
        ContentUnit unit = support.findUnit(project.getId(), ScriptStageKey.ADAPTATION.value());
        Map<String, Object> payload = support.loadUnitPayload(project.getId(), ScriptStageKey.ADAPTATION.value());
        StageGateSupport.Builder b = support.builder()
                .evidence("contentUnitId", unit == null ? null : unit.getId());

        Object episodeGoals = support.firstNonBlank(payload, "episodeGoals", "episode_goals");
        if (episodeGoals == null) {
            List<Map<String, Object>> rules = support.asObjectList(payload.get("rules"));
            episodeGoals = rules.isEmpty() ? null : rules;
        }
        Object mainConflict = support.firstNonBlank(payload, "mainConflict", "main_conflict");
        if (mainConflict == null) {
            mainConflict = support.firstNonBlank(payload, "selectedHookId", "selected_hook_id");
        }
        Object endingHook = support.firstNonBlank(payload, "endingHook", "ending_hook");
        if (endingHook == null) {
            List<Map<String, Object>> hooks = support.asObjectList(payload.get("hooks"));
            endingHook = hooks.isEmpty() ? null : hooks.get(0);
        }
        Object confirmedAt = support.firstNonBlank(payload, "confirmedAt", "confirmed_at");
        boolean confirmed = Boolean.TRUE.equals(payload.get("confirmed")) || confirmedAt != null;

        b.evidence("confirmed", confirmed)
                .require(!support.blank(episodeGoals), "EPISODE_GOALS_REQUIRED", "集目标/改编规则不能为空")
                .require(!support.blank(mainConflict), "MAIN_CONFLICT_REQUIRED", "主冲突/高压开场不能为空")
                .require(!support.blank(endingHook), "ENDING_HOOK_REQUIRED", "结尾钩子/开场方案不能为空")
                .require(confirmed, "ADAPTATION_NOT_CONFIRMED", "改编方案尚未确认");

        ContentVersion current = support.loadCurrentVersion(unit);
        b.evidence("adoptedVersionId", current == null ? null : current.getId());
        return b.build();
    }
}
