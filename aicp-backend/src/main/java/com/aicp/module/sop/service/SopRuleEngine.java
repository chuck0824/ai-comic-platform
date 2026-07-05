package com.aicp.module.sop.service;

import com.aicp.module.sop.domain.SopCheckContext;
import com.aicp.module.sop.domain.SopEnums;
import com.aicp.module.sop.domain.SopRuleDefinition;
import com.aicp.module.sop.domain.SopRuleEvaluation;
import com.aicp.module.storyboard.entity.StoryboardScene;
import com.aicp.module.storyboard.entity.StoryboardShot;
import com.aicp.module.storyboard.entity.StoryboardShotVisualBinding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SopRuleEngine {

    private final ObjectMapper objectMapper;

    private static final int MAX_PROMPT_LENGTH = 500;

    public List<SopRuleEvaluation> evaluateAll(SopCheckContext context, List<SopRuleDefinition> rules) {
        List<SopRuleEvaluation> results = new ArrayList<>();
        for (SopRuleDefinition rule : rules) {
            try {
                if (!rule.enabled()) {
                    continue; // skip disabled rules
                }
                List<SopRuleEvaluation> ruleResults = evaluateRule(rule, context);
                results.addAll(ruleResults);
            } catch (Exception e) {
                log.error("Rule {} evaluation failed: {}", rule.code(), e.getMessage(), e);
                results.add(new SopRuleEvaluation(
                        rule.code(),
                        SopEnums.SopResult.ERROR,
                        rule.severity(),
                        rule.critical(),
                        "system", "rule-engine",
                        fingerprint(rule.code(), "system", "rule-engine"),
                        Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown"),
                        "规则执行异常，请联系管理员",
                        rule.fixPolicy()
                ));
            }
        }
        return results;
    }

    private List<SopRuleEvaluation> evaluateRule(SopRuleDefinition rule, SopCheckContext context) {
        return switch (rule.code()) {
            case "SCENE_GOAL" -> evaluateSceneGoal(rule, context);
            case "BEAT_COMPLETENESS" -> evaluateBeatCompleteness(rule, context);
            case "RELATIONSHIP_CHANGE" -> evaluateRelationshipChange(rule, context);
            case "KEY_DIALOGUE_LOCK" -> evaluateKeyDialogueLock(rule, context);
            case "ASSET_BINDING" -> evaluateAssetBinding(rule, context);
            case "PROMPT_LENGTH" -> evaluatePromptLength(rule, context);
            case "DUB_SUBTITLE_READY" -> evaluateDubSubtitleReady(rule, context);
            default -> List.of(new SopRuleEvaluation(
                    rule.code(), SopEnums.SopResult.NOT_READY, rule.severity(), rule.critical(),
                    "system", rule.code(),
                    fingerprint(rule.code(), "system", rule.code()),
                    Map.of("reason", "规则未实现"),
                    "规则 " + rule.code() + " 尚未实现", rule.fixPolicy()));
        };
    }

    // ===== Rule evaluators =====

    private List<SopRuleEvaluation> evaluateSceneGoal(SopRuleDefinition rule, SopCheckContext context) {
        if (!context.hasScenes()) {
            return List.of(notReady(rule, "project", String.valueOf(context.projectId()),
                    "无锁定版本分镜数据，无法检查场景目标"));
        }
        List<SopRuleEvaluation> results = new ArrayList<>();
        for (StoryboardScene scene : context.scenes()) {
            if (isBlank(scene.getDramaticGoal())) {
                results.add(blocked(rule, "scene", String.valueOf(scene.getId()),
                        Map.of("sceneNo", scene.getSceneNo(), "sceneTitle", scene.getTitle()),
                        "场景 “" + scene.getTitle() + "” 缺少戏剧目标 (dramaticGoal)"));
            } else {
                results.add(pass(rule, "scene", String.valueOf(scene.getId())));
            }
        }
        return results.isEmpty() ? List.of(passGlobal(rule)) : results;
    }

    private List<SopRuleEvaluation> evaluateBeatCompleteness(SopRuleDefinition rule, SopCheckContext context) {
        if (!context.hasScenes()) {
            return List.of(notReady(rule, "project", String.valueOf(context.projectId()),
                    "无锁定版本分镜数据，无法检查节拍完整性"));
        }
        List<SopRuleEvaluation> results = new ArrayList<>();
        for (StoryboardScene scene : context.scenes()) {
            if (isBlank(scene.getBeatDescription())) {
                results.add(blocked(rule, "scene", String.valueOf(scene.getId()),
                        Map.of("sceneNo", scene.getSceneNo(), "sceneTitle", scene.getTitle()),
                        "场景 “" + scene.getTitle() + "” 缺少节拍描述 (beatDescription)"));
            } else {
                results.add(pass(rule, "scene", String.valueOf(scene.getId())));
            }
        }
        return results.isEmpty() ? List.of(passGlobal(rule)) : results;
    }

    private List<SopRuleEvaluation> evaluateRelationshipChange(SopRuleDefinition rule, SopCheckContext context) {
        if (!context.hasShots()) {
            return List.of(notReady(rule, "project", String.valueOf(context.projectId()),
                    "无锁定版本镜头数据"));
        }
        List<SopRuleEvaluation> results = new ArrayList<>();
        List<StoryboardShot> dialogueShots = context.shots().stream()
                .filter(s -> !isBlank(s.getDialogueText()) || !isBlank(s.getCharacterAction()))
                .toList();
        for (StoryboardShot shot : dialogueShots) {
            if (isBlank(shot.getRelationshipBlocking())) {
                results.add(warning(rule, "shot", String.valueOf(shot.getId()),
                        Map.of("shotCode", shot.getShotCode() != null ? shot.getShotCode() : ""),
                        "镜头 " + shot.getShotCode() + " 有对白/动作但缺少人物关系阻塞 (relationshipBlocking)"));
            } else {
                results.add(pass(rule, "shot", String.valueOf(shot.getId())));
            }
        }
        return results.isEmpty() ? List.of(passGlobal(rule, "无对白镜头，跳过检查")) : results;
    }

    private List<SopRuleEvaluation> evaluateKeyDialogueLock(SopRuleDefinition rule, SopCheckContext context) {
        if (!context.hasLockedVersion()) {
            return List.of(notReady(rule, "project", String.valueOf(context.projectId()),
                    "分镜版本未锁定，无法检查关键对白"));
        }
        if (!context.hasShots()) {
            return List.of(passGlobal(rule, "无镜头数据"));
        }
        List<SopRuleEvaluation> results = new ArrayList<>();
        List<StoryboardShot> dialogueShots = context.shots().stream()
                .filter(s -> !isBlank(s.getDialogueText()) || !isBlank(s.getCharacterAction()))
                .toList();
        for (StoryboardShot shot : dialogueShots) {
            if (isBlank(shot.getDialogueText())) {
                results.add(blocked(rule, "shot", String.valueOf(shot.getId()),
                        Map.of("shotCode", shot.getShotCode() != null ? shot.getShotCode() : ""),
                        "镜头 " + shot.getShotCode() + " 有角色动作但缺少对白文本 (dialogueText)"));
            } else {
                results.add(pass(rule, "shot", String.valueOf(shot.getId())));
            }
        }
        return results.isEmpty() ? List.of(passGlobal(rule, "无对白镜头")) : results;
    }

    private List<SopRuleEvaluation> evaluateAssetBinding(SopRuleDefinition rule, SopCheckContext context) {
        List<SopRuleEvaluation> results = new ArrayList<>();

        // Check scenes for location
        if (context.hasScenes()) {
            for (StoryboardScene scene : context.scenes()) {
                if (scene.getLocationRefId() == null) {
                    results.add(blocked(rule, "scene", String.valueOf(scene.getId()),
                            Map.of("sceneNo", scene.getSceneNo(), "sceneTitle", scene.getTitle()),
                            "场景 “" + scene.getTitle() + "” 缺少场地资产绑定 (locationRefId)"));
                } else {
                    results.add(pass(rule, "scene", String.valueOf(scene.getId())));
                }
            }
        }

        // Check shots for visual bindings
        if (context.hasShots()) {
            for (StoryboardShot shot : context.shots()) {
                List<StoryboardShotVisualBinding> bindings = context.bindingsForShot(shot.getId());
                if (bindings.isEmpty()) {
                    results.add(blocked(rule, "shot", String.valueOf(shot.getId()),
                            Map.of("shotCode", shot.getShotCode() != null ? shot.getShotCode() : ""),
                            "镜头 " + shot.getShotCode() + " 缺少角色/视觉资产绑定"));
                } else {
                    results.add(pass(rule, "shot", String.valueOf(shot.getId())));
                }
            }
        }

        return results.isEmpty() ? List.of(notReady(rule, "project", String.valueOf(context.projectId()),
                "无场景或镜头数据")) : results;
    }

    private List<SopRuleEvaluation> evaluatePromptLength(SopRuleDefinition rule, SopCheckContext context) {
        if (!context.hasShots()) {
            return List.of(notReady(rule, "project", String.valueOf(context.projectId()),
                    "无镜头数据"));
        }
        List<SopRuleEvaluation> results = new ArrayList<>();
        for (StoryboardShot shot : context.shots()) {
            boolean imageTooLong = shot.getImagePrompt() != null && shot.getImagePrompt().length() > MAX_PROMPT_LENGTH;
            boolean videoTooLong = shot.getVideoMotionPrompt() != null && shot.getVideoMotionPrompt().length() > MAX_PROMPT_LENGTH;

            if (imageTooLong || videoTooLong) {
                String type = imageTooLong && videoTooLong ? "image+video" : (imageTooLong ? "image" : "video");
                results.add(blocked(rule, "shot", String.valueOf(shot.getId()),
                        Map.of("shotCode", shot.getShotCode() != null ? shot.getShotCode() : "",
                                "imagePromptLength", shot.getImagePrompt() != null ? shot.getImagePrompt().length() : 0,
                                "videoPromptLength", shot.getVideoMotionPrompt() != null ? shot.getVideoMotionPrompt().length() : 0),
                        "镜头 " + shot.getShotCode() + " 的 " + type + " Prompt 超过 " + MAX_PROMPT_LENGTH + " 字符限制"));
            } else {
                results.add(pass(rule, "shot", String.valueOf(shot.getId())));
            }
        }
        return results;
    }

    private List<SopRuleEvaluation> evaluateDubSubtitleReady(SopRuleDefinition rule, SopCheckContext context) {
        if (!context.hasShots()) {
            return List.of(notReady(rule, "project", String.valueOf(context.projectId()),
                    "无镜头数据"));
        }
        List<SopRuleEvaluation> results = new ArrayList<>();
        List<StoryboardShot> dialogueShots = context.shots().stream()
                .filter(s -> !isBlank(s.getDialogueText()))
                .toList();
        for (StoryboardShot shot : dialogueShots) {
            boolean dubMissing = isBlank(shot.getDubText());
            boolean subMissing = isBlank(shot.getSubtitleText());
            if (dubMissing || subMissing) {
                String missing = dubMissing && subMissing ? "配音和字幕" : (dubMissing ? "配音" : "字幕");
                results.add(blocked(rule, "shot", String.valueOf(shot.getId()),
                        Map.of("shotCode", shot.getShotCode() != null ? shot.getShotCode() : ""),
                        "镜头 " + shot.getShotCode() + " 有对白但缺少" + missing + "文本"));
            } else {
                results.add(pass(rule, "shot", String.valueOf(shot.getId())));
            }
        }
        return results.isEmpty() ? List.of(passGlobal(rule, "无对白镜头")) : results;
    }

    // ===== Helper methods =====

    private SopRuleEvaluation pass(SopRuleDefinition rule, String targetType, String targetId) {
        return new SopRuleEvaluation(rule.code(), SopEnums.SopResult.PASS, rule.severity(), rule.critical(),
                targetType, targetId, fingerprint(rule.code(), targetType, targetId),
                Map.of(), null, rule.fixPolicy());
    }

    private SopRuleEvaluation passGlobal(SopRuleDefinition rule) {
        return passGlobal(rule, null);
    }

    private SopRuleEvaluation passGlobal(SopRuleDefinition rule, String note) {
        return new SopRuleEvaluation(rule.code(), SopEnums.SopResult.PASS, rule.severity(), rule.critical(),
                "project", String.valueOf(0),
                fingerprint(rule.code(), "project", "0"),
                note != null ? Map.of("note", note) : Map.of(), null, rule.fixPolicy());
    }

    private SopRuleEvaluation blocked(SopRuleDefinition rule, String targetType, String targetId,
                                      Map<String, Object> evidence, String suggestion) {
        return new SopRuleEvaluation(rule.code(), SopEnums.SopResult.BLOCKED, rule.severity(), rule.critical(),
                targetType, targetId, fingerprint(rule.code(), targetType, targetId),
                evidence, suggestion, rule.fixPolicy());
    }

    private SopRuleEvaluation warning(SopRuleDefinition rule, String targetType, String targetId,
                                      Map<String, Object> evidence, String suggestion) {
        return new SopRuleEvaluation(rule.code(), SopEnums.SopResult.WARNING, rule.severity(), rule.critical(),
                targetType, targetId, fingerprint(rule.code(), targetType, targetId),
                evidence, suggestion, rule.fixPolicy());
    }

    private SopRuleEvaluation notReady(SopRuleDefinition rule, String targetType, String targetId, String suggestion) {
        return new SopRuleEvaluation(rule.code(), SopEnums.SopResult.NOT_READY, rule.severity(), rule.critical(),
                targetType, targetId, fingerprint(rule.code(), targetType, targetId),
                Map.of("reason", suggestion), suggestion, rule.fixPolicy());
    }

    // ===== Utility =====

    static String fingerprint(String ruleCode, String targetType, String targetId) {
        String input = ruleCode + ":" + targetType + ":" + targetId;
        return SopContextAssembler.sha256(input);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
