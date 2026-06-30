package com.aicp.module.storyboard.domain;

import com.aicp.module.storyboard.entity.StoryboardShot;
import com.aicp.module.storyboard.entity.StoryboardVersion;

import java.util.ArrayList;
import java.util.List;

public final class ProductionGate {

    private ProductionGate() {}

    public record GateResult(boolean allowed, List<String> violations) {}

    public static GateResult evaluate(StoryboardVersion version, List<StoryboardShot> shots,
                                       List<String> boundCharacterNames, List<String> openIssueFingerprints) {
        List<String> violations = new ArrayList<>();

        String tier = version.getTier();

        // A-tier: basic completeness
        if ("A".equals(tier)) {
            for (StoryboardShot shot : shots) {
                String code = shot.getShotCode() != null ? shot.getShotCode() : "S??-C??";
                if (shot.getDurationMs() == null || shot.getDurationMs() <= 0) {
                    violations.add(code + " 缺少时长");
                }
            }
        }

        // B-tier: director fields
        if ("B".equals(tier)) {
            for (StoryboardShot shot : shots) {
                String code = shot.getShotCode() != null ? shot.getShotCode() : "S??-C??";
                if (isBlank(shot.getDirectorIntention())) {
                    violations.add(code + " 缺少导演意图 (B档必需)");
                }
            }
        }

        // C-tier: production fields
        if ("C".equals(tier)) {
            for (StoryboardShot shot : shots) {
                String code = shot.getShotCode() != null ? shot.getShotCode() : "S??-C??";
                if (isBlank(shot.getImagePrompt())) {
                    violations.add(code + " 缺少图片提示词");
                }
                if (isBlank(shot.getVideoMotionPrompt())) {
                    violations.add(code + " 缺少视频动作提示词");
                }
                if (isBlank(shot.getDubText()) && isBlank(shot.getSubtitleText())) {
                    violations.add(code + " 缺少配音或字幕文本");
                }
                if (isBlank(shot.getFailureStrategy())) {
                    violations.add(code + " 缺少失败策略");
                }
            }
            if (boundCharacterNames.isEmpty()) {
                violations.add("C档缺少角色视觉绑定");
            }
        }

        // Open blocking issues block all tiers
        if (!openIssueFingerprints.isEmpty()) {
            violations.add("仍有 " + openIssueFingerprints.size() + " 个未解决的阻断问题");
        }

        return new GateResult(violations.isEmpty(), violations);
    }

    public static boolean isConceptEligible(StoryboardVersion version, List<StoryboardShot> shots,
                                             List<String> openBlockingIssues) {
        if (!"locked".equalsIgnoreCase(version.getStatus())) return false;
        if (!openBlockingIssues.isEmpty()) return false;
        return true;
    }

    public static boolean isProductionEligible(StoryboardVersion version, List<StoryboardShot> shots,
                                                List<String> boundCharacterNames, List<String> openBlockingIssues) {
        if (!"C".equals(version.getTier())) return false;
        GateResult result = evaluate(version, shots, boundCharacterNames, openBlockingIssues);
        return result.allowed();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
