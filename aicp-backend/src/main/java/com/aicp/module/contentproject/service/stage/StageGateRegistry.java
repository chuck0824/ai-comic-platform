package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.entity.ContentProject;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class StageGateRegistry {

    private final Map<ScriptStageKey, StageGateEvaluator> evaluators = new EnumMap<>(ScriptStageKey.class);

    public StageGateRegistry(List<StageGateEvaluator> evaluatorList) {
        for (StageGateEvaluator evaluator : evaluatorList) {
            evaluators.put(evaluator.stageKey(), evaluator);
        }
    }

    public StageGateResult evaluate(ScriptStageKey stageKey, ContentProject project) {
        StageGateEvaluator evaluator = evaluators.get(stageKey);
        if (evaluator == null) {
            return StageGateResult.pass(Map.of("stageKey", stageKey.value(), "note", "no_evaluator"));
        }
        return evaluator.evaluate(project);
    }
}
