package com.aicp.module.contentproject.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum ScriptStageState {
    NOT_STARTED,
    IN_PROGRESS,
    BLOCKED,
    COMPLETED,
    POSSIBLY_STALE,
    REGEN_REQUIRED,
    LOCKED;

    private static final Set<ScriptStageState> DOWNSTREAM_ALLOWED =
            EnumSet.of(COMPLETED, LOCKED);

    public boolean allowsDownstream() {
        return DOWNSTREAM_ALLOWED.contains(this);
    }

    public static ScriptStageState parse(String raw) {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean canTransition(ScriptStageState from, ScriptStageState to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case NOT_STARTED -> to == IN_PROGRESS;
            case IN_PROGRESS -> to == BLOCKED || to == COMPLETED || to == LOCKED;
            case BLOCKED -> to == IN_PROGRESS;
            case COMPLETED -> to == IN_PROGRESS || to == POSSIBLY_STALE || to == REGEN_REQUIRED;
            case LOCKED -> to == IN_PROGRESS || to == POSSIBLY_STALE || to == REGEN_REQUIRED;
            case POSSIBLY_STALE -> to == COMPLETED || to == IN_PROGRESS || to == REGEN_REQUIRED;
            case REGEN_REQUIRED -> to == IN_PROGRESS;
        };
    }
}
