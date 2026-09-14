package com.aicp.module.contentproject.domain;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 剧本八阶段权威 stage_key，以及旧轨映射。
 */
public enum ScriptStageKey {
    CREATION_SETTINGS("creation_settings"),
    NOVEL_UPLOAD("novel_upload"),
    NOVEL_ANALYSIS("novel_analysis"),
    ADAPTATION("adaptation"),
    STRUCTURED_SCRIPT("structured_script"),
    SCRIPT_BODY("script_body"),
    REVIEW_REVISION("review_revision"),
    TEXT_STORYBOARD("text_storyboard");

    private static final Map<String, ScriptStageKey> LEGACY = new LinkedHashMap<>();

    static {
        LEGACY.put("story_seed", CREATION_SETTINGS);
        LEGACY.put("import_review", NOVEL_UPLOAD);
        LEGACY.put("characters", NOVEL_ANALYSIS);
        LEGACY.put("synopsis", NOVEL_ANALYSIS);
        LEGACY.put("outline", STRUCTURED_SCRIPT);
        LEGACY.put("content", SCRIPT_BODY);
        LEGACY.put("review", REVIEW_REVISION);
        LEGACY.put("destination", TEXT_STORYBOARD);
        LEGACY.put("storyboard", TEXT_STORYBOARD);
        for (ScriptStageKey key : values()) {
            LEGACY.put(key.value, key);
        }
    }

    private final String value;

    ScriptStageKey(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String label() {
        return switch (this) {
            case CREATION_SETTINGS -> "创作设置";
            case NOVEL_UPLOAD -> "小说上传";
            case NOVEL_ANALYSIS -> "小说分析";
            case ADAPTATION -> "改编方案";
            case STRUCTURED_SCRIPT -> "结构化剧本";
            case SCRIPT_BODY -> "剧本正文";
            case REVIEW_REVISION -> "审核修订";
            case TEXT_STORYBOARD -> "文字分镜";
        };
    }

    public int order() {
        return ordinal();
    }

    public Optional<ScriptStageKey> next() {
        int i = ordinal() + 1;
        return i < values().length ? Optional.of(values()[i]) : Optional.empty();
    }

    public Optional<ScriptStageKey> previous() {
        int i = ordinal() - 1;
        return i >= 0 ? Optional.of(values()[i]) : Optional.empty();
    }

    public static List<ScriptStageKey> ordered() {
        return Arrays.asList(values());
    }

    public static ScriptStageKey parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("stage_key 不能为空");
        }
        ScriptStageKey key = LEGACY.get(raw.trim().toLowerCase(Locale.ROOT));
        if (key == null) {
            throw new IllegalArgumentException("未知 stage_key: " + raw);
        }
        return key;
    }

    public static ScriptStageKey normalizeOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return CREATION_SETTINGS;
        }
        try {
            return parse(raw);
        } catch (IllegalArgumentException e) {
            return CREATION_SETTINGS;
        }
    }
}
