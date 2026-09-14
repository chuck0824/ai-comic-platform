package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.entity.ContentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StructuredScriptGateEvaluator implements StageGateEvaluator {

    private static final double DURATION_VARIANCE_THRESHOLD = 0.15;

    private final StageGateSupport support;

    @Override
    public ScriptStageKey stageKey() {
        return ScriptStageKey.STRUCTURED_SCRIPT;
    }

    @Override
    public StageGateResult evaluate(ContentProject project) {
        Map<String, Object> payload = support.loadUnitPayload(project.getId(), ScriptStageKey.STRUCTURED_SCRIPT.value());
        StageGateSupport.Builder b = support.builder().evidence("hasPayload", !payload.isEmpty());

        List<Map<String, Object>> episodes = support.asObjectList(payload.get("episodes"));
        b.evidence("episodeCount", episodes.size())
                .require(!episodes.isEmpty(), "STRUCTURED_EPISODES_REQUIRED", "结构化剧本至少需要一集");

        int beatCount = 0;
        int sceneCount = 0;
        for (Map<String, Object> episode : episodes) {
            List<Map<String, Object>> beats = support.asObjectList(episode.get("beats"));
            beatCount += beats.size();
            List<Map<String, Object>> scenes = support.asObjectList(episode.get("scenes"));
            sceneCount += scenes.size();
            for (Map<String, Object> scene : scenes) {
                beatCount += support.asObjectList(scene.get("beats")).size();
            }
        }
        b.evidence("beatCount", beatCount)
                .evidence("sceneCount", sceneCount)
                .require(beatCount > 0, "STRUCTURED_BEATS_REQUIRED", "节拍/场次结构不能为空");

        Object targetDuration = support.firstNonBlank(payload, "targetDuration", "target_duration");
        Object actualDuration = support.firstNonBlank(payload, "actualDuration", "actual_duration", "estimatedDuration");
        boolean warningConfirmed = Boolean.TRUE.equals(payload.get("durationVarianceConfirmed"))
                || Boolean.TRUE.equals(payload.get("duration_variance_confirmed"));
        if (targetDuration != null && actualDuration != null) {
            try {
                double target = Double.parseDouble(String.valueOf(targetDuration));
                double actual = Double.parseDouble(String.valueOf(actualDuration));
                if (target > 0) {
                    double variance = Math.abs(actual - target) / target;
                    b.evidence("durationVariance", variance);
                    if (variance > DURATION_VARIANCE_THRESHOLD) {
                        b.warn(true, "DURATION_VARIANCE",
                                String.format("目标时长偏差 %.0f%%", variance * 100));
                        if (!warningConfirmed) {
                            b.require(false, "DURATION_VARIANCE_UNCONFIRMED",
                                    "目标时长偏差超阈值，请确认警告后继续");
                        }
                    }
                }
            } catch (Exception ignored) {
                // 非数值时跳过偏差检查
            }
        }
        return b.build();
    }
}
