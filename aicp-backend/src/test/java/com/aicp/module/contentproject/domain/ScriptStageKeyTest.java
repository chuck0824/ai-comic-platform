package com.aicp.module.contentproject.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScriptStageKeyTest {

    @Test
    void normalizesLegacyKeys() {
        assertEquals(ScriptStageKey.CREATION_SETTINGS, ScriptStageKey.parse("story_seed"));
        assertEquals(ScriptStageKey.NOVEL_UPLOAD, ScriptStageKey.parse("import_review"));
        assertEquals(ScriptStageKey.STRUCTURED_SCRIPT, ScriptStageKey.parse("outline"));
        assertEquals(ScriptStageKey.TEXT_STORYBOARD, ScriptStageKey.parse("destination"));
        assertEquals(ScriptStageKey.SCRIPT_BODY, ScriptStageKey.parse("script_body"));
    }

    @Test
    void orderedEightStages() {
        assertEquals(8, ScriptStageKey.ordered().size());
        assertEquals(ScriptStageKey.CREATION_SETTINGS, ScriptStageKey.ordered().get(0));
        assertEquals(ScriptStageKey.TEXT_STORYBOARD, ScriptStageKey.ordered().get(7));
    }

    @Test
    void stateMachineAllowsAdjacentTransitions() {
        assertTrue(ScriptStageState.canTransition(ScriptStageState.NOT_STARTED, ScriptStageState.IN_PROGRESS));
        assertTrue(ScriptStageState.canTransition(ScriptStageState.IN_PROGRESS, ScriptStageState.COMPLETED));
        assertFalse(ScriptStageState.canTransition(ScriptStageState.NOT_STARTED, ScriptStageState.COMPLETED));
    }
}
