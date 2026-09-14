package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.entity.ContentProject;

public interface StageGateEvaluator {

    ScriptStageKey stageKey();

    StageGateResult evaluate(ContentProject project);
}
