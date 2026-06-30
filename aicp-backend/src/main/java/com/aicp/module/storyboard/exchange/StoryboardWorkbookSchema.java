package com.aicp.module.storyboard.exchange;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StoryboardWorkbookSchema {

    public static final int SCHEMA_VERSION = 1;

    public static final String SHEET_SHOTS = "分镜头脚本";
    public static final String SHEET_EMOTION = "情绪强度总览";
    public static final String SHEET_PROMPTS = "提示词模板";
    public static final String SHEET_RULES = "奥斯卡三线修订表";
    public static final String SHEET_CONSISTENCY = "设定一致性修订表";
    public static final String SHEET_VISUALS = "人物三视图视觉规范";
    public static final String SHEET_BINDINGS = "三视图分镜应用表";

    public static final List<String> REQUIRED_SHEETS = List.of(
            SHEET_SHOTS, SHEET_EMOTION, SHEET_PROMPTS, SHEET_RULES,
            SHEET_CONSISTENCY, SHEET_VISUALS, SHEET_BINDINGS);

    public static final String HIDDEN_SHEET = "_schema";

    public static final List<String> SHOT_HEADERS = List.of(
            "镜号", "时长(s)", "景别", "画面描述", "光影氛围", "角色动作", "情绪",
            "对白", "场景标签", "音效", "参考", "分镜提示词", "视频动作提示词");

    public static final Map<String, Integer> DURATION_COL = Map.of("时长(s)", 1);

    private StoryboardWorkbookSchema() {}
}
