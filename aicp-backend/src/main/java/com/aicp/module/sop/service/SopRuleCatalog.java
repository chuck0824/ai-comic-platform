package com.aicp.module.sop.service;

import com.aicp.module.sop.domain.SopEnums;
import com.aicp.module.sop.domain.SopRuleDefinition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class SopRuleCatalog {

    public static final String ACTIVE_RULE_SET_VERSION = "production-readiness-v1";

    private static final Set<SopEnums.GateType> ADMISSION_GATE = Set.of(SopEnums.GateType.PRODUCTION_ADMISSION);

    private static final List<SopRuleDefinition> ALL_RULES = List.of(
            // ===== ENABLED rules (7) =====
            new SopRuleDefinition("SCENE_GOAL", "场景目标完整性", "剧本完整性",
                    SopEnums.Severity.P1, true, ADMISSION_GATE, SopEnums.FixPolicy.MANUAL_ONLY, true),
            new SopRuleDefinition("BEAT_COMPLETENESS", "节拍完整性", "剧本完整性",
                    SopEnums.Severity.P1, true, ADMISSION_GATE, SopEnums.FixPolicy.MANUAL_ONLY, true),
            new SopRuleDefinition("RELATIONSHIP_CHANGE", "人物关系变化", "人物关系",
                    SopEnums.Severity.P2, false, ADMISSION_GATE, SopEnums.FixPolicy.CONFIRM_REQUIRED, true),
            new SopRuleDefinition("KEY_DIALOGUE_LOCK", "关键对白锁定", "对白与配音",
                    SopEnums.Severity.P1, true, ADMISSION_GATE, SopEnums.FixPolicy.MANUAL_ONLY, true),
            new SopRuleDefinition("ASSET_BINDING", "资产绑定完整性", "资产绑定",
                    SopEnums.Severity.P1, true, ADMISSION_GATE, SopEnums.FixPolicy.CONFIRM_REQUIRED, true),
            new SopRuleDefinition("PROMPT_LENGTH", "Prompt 长度合规", "Prompt 质量",
                    SopEnums.Severity.P1, true, ADMISSION_GATE, SopEnums.FixPolicy.AUTO_SAFE, true),
            new SopRuleDefinition("DUB_SUBTITLE_READY", "配音字幕就绪", "对白与配音",
                    SopEnums.Severity.P1, true, ADMISSION_GATE, SopEnums.FixPolicy.MANUAL_ONLY, true),

            // ===== DISABLED rules (6) — upstream data sources not yet available =====
            new SopRuleDefinition("PLOT_FIDELITY", "剧情忠实度", "剧本一致性",
                    SopEnums.Severity.P0, true, ADMISSION_GATE, SopEnums.FixPolicy.MANUAL_ONLY, false),
            new SopRuleDefinition("RISK_SHOT_MARKING", "风险镜头标注", "风险控制",
                    SopEnums.Severity.P1, true, ADMISSION_GATE, SopEnums.FixPolicy.MANUAL_ONLY, false),
            new SopRuleDefinition("COMPLEX_SHOT_SPLIT", "复杂镜头拆分", "分镜质量",
                    SopEnums.Severity.P1, true, ADMISSION_GATE, SopEnums.FixPolicy.CONFIRM_REQUIRED, false),
            new SopRuleDefinition("IMAGE_VIDEO_TABLE_SPLIT", "图表分离完整性", "多模态生成",
                    SopEnums.Severity.P2, false, ADMISSION_GATE, SopEnums.FixPolicy.AUTO_SAFE, false),
            new SopRuleDefinition("VOICE_BINDING", "配音角色绑定", "对白与配音",
                    SopEnums.Severity.P1, true, ADMISSION_GATE, SopEnums.FixPolicy.MANUAL_ONLY, false),
            new SopRuleDefinition("CONTINUITY_INHERITANCE", "连续性继承", "连续性与继承",
                    SopEnums.Severity.P0, true, ADMISSION_GATE, SopEnums.FixPolicy.MANUAL_ONLY, false)
    );

    public List<SopRuleDefinition> getActiveRules() {
        return ALL_RULES.stream().filter(SopRuleDefinition::enabled).toList();
    }

    public List<SopRuleDefinition> getAllRules() {
        return List.copyOf(ALL_RULES);
    }

    public String getActiveRuleSetVersion() {
        return ACTIVE_RULE_SET_VERSION;
    }

    public int getEnabledCount() {
        return (int) ALL_RULES.stream().filter(SopRuleDefinition::enabled).count();
    }

    public int getTotalCount() {
        return ALL_RULES.size();
    }
}
