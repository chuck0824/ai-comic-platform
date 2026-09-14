package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.entity.ContentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NovelAnalysisGateEvaluator implements StageGateEvaluator {

    private final StageGateSupport support;

    @Override
    public ScriptStageKey stageKey() {
        return ScriptStageKey.NOVEL_ANALYSIS;
    }

    @Override
    public StageGateResult evaluate(ContentProject project) {
        Map<String, Object> payload = support.loadUnitPayload(project.getId(), ScriptStageKey.NOVEL_ANALYSIS.value());
        StageGateSupport.Builder b = support.builder().evidence("hasPayload", !payload.isEmpty());

        List<Map<String, Object>> characters = support.asObjectList(
                firstNested(payload, "entities", "mainCharacters", "characters"));
        if (characters.isEmpty()) {
            characters = support.asObjectList(payload.get("characters"));
        }
        List<Map<String, Object>> scenes = support.asObjectList(
                firstNested(payload, "entities", "coreScenes", "locations"));
        if (scenes.isEmpty()) {
            scenes = support.asObjectList(payload.get("locations"));
        }
        List<Map<String, Object>> events = support.asObjectList(
                firstNested(payload, "entities", "events", "events"));
        if (events.isEmpty()) {
            events = support.asObjectList(payload.get("events"));
        }
        Object worldview = firstNested(payload, "entities", "worldview", "worldview");
        if (worldview == null) {
            worldview = payload.get("worldview");
        }

        b.evidence("characterCount", characters.size())
                .evidence("sceneCount", scenes.size())
                .evidence("eventCount", events.size())
                .require(!characters.isEmpty(), "MAIN_CHARACTERS_REQUIRED", "主要角色不能为空")
                .require(!scenes.isEmpty(), "CORE_SCENES_REQUIRED", "核心场景/地点不能为空")
                .require(!events.isEmpty(), "EVENTS_REQUIRED", "关键事件不能为空")
                .require(!support.blank(worldview), "WORLDVIEW_REQUIRED", "世界观不能为空");

        for (Map<String, Object> issue : support.asObjectList(payload.get("issues"))) {
            String severity = String.valueOf(issue.getOrDefault("severity", "")).toUpperCase(Locale.ROOT);
            String status = String.valueOf(issue.getOrDefault("status", "OPEN")).toUpperCase(Locale.ROOT);
            if ("BLOCKER".equals(severity) && ("OPEN".equals(status) || "open".equalsIgnoreCase(status))) {
                b.require(false, "ANALYSIS_BLOCKER_OPEN",
                        "存在未关闭的阻断问题：" + issue.getOrDefault("title", issue.getOrDefault("code", "")));
            }
        }
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private Object firstNested(Map<String, Object> payload, String parent, String nested, String fallback) {
        Object entity = payload.get(parent);
        if (entity instanceof Map<?, ?> map) {
            Object value = map.get(nested);
            if (value != null) {
                return value;
            }
        }
        return payload.get(fallback);
    }
}
