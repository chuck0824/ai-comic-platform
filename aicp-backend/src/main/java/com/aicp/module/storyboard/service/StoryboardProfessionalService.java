package com.aicp.module.storyboard.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.storyboard.domain.StoryboardStateMachine;
import com.aicp.module.storyboard.dto.StoryboardRequests.*;
import com.aicp.module.storyboard.dto.StoryboardViews.*;
import com.aicp.module.storyboard.entity.*;
import com.aicp.module.storyboard.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardProfessionalService {

    private final StoryboardEmotionSegmentMapper emotionSegmentMapper;
    private final StoryboardPromptTemplateMapper promptTemplateMapper;
    private final StoryboardCreativeRuleMapper creativeRuleMapper;
    private final StoryboardCharacterVisualMapper characterVisualMapper;
    private final StoryboardShotVisualBindingMapper visualBindingMapper;
    private final StoryboardAuditLogMapper auditLogMapper;
    private final StoryboardAccessService accessService;
    private final StoryboardVersionService versionService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ===== Emotion Segments =====

    public List<EmotionSegmentView> listEmotionSegments(Long projectId, Long versionId, Long userId) {
        accessService.requireVersion(projectId, versionId, userId, Action.VIEW);
        return emotionSegmentMapper.selectList(
                new LambdaQueryWrapper<StoryboardEmotionSegment>()
                        .eq(StoryboardEmotionSegment::getVersionId, versionId)
                        .orderByAsc(StoryboardEmotionSegment::getSortOrder))
                .stream().map(this::toEmotionSegmentView).toList();
    }

    @Transactional
    public List<EmotionSegmentView> replaceEmotionSegments(Long projectId, Long versionId, Long userId,
                                                            ReplaceEmotionSegmentsRequest request) {
        var version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        versionService.bumpRevision(version, request.revision());

        emotionSegmentMapper.delete(
                new LambdaQueryWrapper<StoryboardEmotionSegment>()
                        .eq(StoryboardEmotionSegment::getVersionId, versionId));

        List<StoryboardEmotionSegment> segments = new ArrayList<>();
        for (var item : request.items()) {
            StoryboardEmotionSegment seg = new StoryboardEmotionSegment();
            seg.setVersionId(versionId);
            seg.setEmotionType(item.emotionType());
            seg.setShotRange(item.shotRange());
            seg.setIntensity(item.intensity());
            seg.setCoreExpression(item.coreExpression());
            seg.setSortOrder(item.sortOrder());
            emotionSegmentMapper.insert(seg);
            segments.add(seg);
        }
        writeAudit(versionId, userId, "replace_emotions", "version", versionId, null);
        return segments.stream().map(this::toEmotionSegmentView).toList();
    }

    // ===== Prompt Templates =====

    public List<PromptTemplateView> listPromptTemplates(Long projectId, Long versionId, Long userId) {
        accessService.requireVersion(projectId, versionId, userId, Action.VIEW);
        return promptTemplateMapper.selectList(
                new LambdaQueryWrapper<StoryboardPromptTemplate>()
                        .eq(StoryboardPromptTemplate::getVersionId, versionId)
                        .orderByAsc(StoryboardPromptTemplate::getSortOrder))
                .stream().map(this::toPromptTemplateView).toList();
    }

    @Transactional
    public List<PromptTemplateView> replacePromptTemplates(Long projectId, Long versionId, Long userId,
                                                            ReplacePromptTemplatesRequest request) {
        var version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        versionService.bumpRevision(version, request.revision());

        promptTemplateMapper.delete(
                new LambdaQueryWrapper<StoryboardPromptTemplate>()
                        .eq(StoryboardPromptTemplate::getVersionId, versionId));

        List<StoryboardPromptTemplate> templates = new ArrayList<>();
        for (var item : request.items()) {
            StoryboardPromptTemplate tmpl = new StoryboardPromptTemplate();
            tmpl.setVersionId(versionId);
            tmpl.setTemplateCode(item.templateCode());
            tmpl.setEmotionName(item.emotionName());
            tmpl.setShotRefsJson(toJson(item.shotCodes()));
            tmpl.setImagePrompt(item.imagePrompt());
            tmpl.setVideoMotionPrompt(item.videoMotionPrompt());
            tmpl.setSortOrder(templates.size());
            promptTemplateMapper.insert(tmpl);
            templates.add(tmpl);
        }
        writeAudit(versionId, userId, "replace_prompts", "version", versionId, null);
        return templates.stream().map(this::toPromptTemplateView).toList();
    }

    // ===== Creative Rules =====

    public List<CreativeRuleView> listCreativeRules(Long projectId, Long versionId, Long userId) {
        accessService.requireVersion(projectId, versionId, userId, Action.VIEW);
        return creativeRuleMapper.selectList(
                new LambdaQueryWrapper<StoryboardCreativeRule>()
                        .eq(StoryboardCreativeRule::getVersionId, versionId)
                        .orderByAsc(StoryboardCreativeRule::getSortOrder))
                .stream().map(this::toCreativeRuleView).toList();
    }

    @Transactional
    public List<CreativeRuleView> replaceCreativeRules(Long projectId, Long versionId, Long userId,
                                                        ReplaceCreativeRulesRequest request) {
        var version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        versionService.bumpRevision(version, request.revision());

        creativeRuleMapper.delete(
                new LambdaQueryWrapper<StoryboardCreativeRule>()
                        .eq(StoryboardCreativeRule::getVersionId, versionId));

        List<StoryboardCreativeRule> rules = new ArrayList<>();
        for (var item : request.items()) {
            StoryboardCreativeRule rule = new StoryboardCreativeRule();
            rule.setVersionId(versionId);
            rule.setRuleType(item.ruleType());
            rule.setDimensionName(item.dimensionName());
            rule.setPrinciple(item.principle());
            rule.setImplementationText(item.implementationText());
            rule.setTargetRefsJson(toJson(item.targetRefs()));
            rule.setEffectText(item.effectText());
            rule.setStatus(item.status() != null ? item.status() : "active");
            rule.setSortOrder(rules.size());
            creativeRuleMapper.insert(rule);
            rules.add(rule);
        }
        writeAudit(versionId, userId, "replace_rules", "version", versionId, null);
        return rules.stream().map(this::toCreativeRuleView).toList();
    }

    // ===== Character Visuals =====

    public List<CharacterVisualView> listCharacterVisuals(Long projectId, Long versionId, Long userId) {
        accessService.requireVersion(projectId, versionId, userId, Action.VIEW);
        return characterVisualMapper.selectList(
                new LambdaQueryWrapper<StoryboardCharacterVisual>()
                        .eq(StoryboardCharacterVisual::getVersionId, versionId)
                        .orderByAsc(StoryboardCharacterVisual::getSortOrder))
                .stream().map(this::toCharacterVisualView).toList();
    }

    @Transactional
    public List<CharacterVisualView> replaceCharacterVisuals(Long projectId, Long versionId, Long userId,
                                                              ReplaceCharacterVisualsRequest request) {
        var version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        versionService.bumpRevision(version, request.revision());

        characterVisualMapper.delete(
                new LambdaQueryWrapper<StoryboardCharacterVisual>()
                        .eq(StoryboardCharacterVisual::getVersionId, versionId));

        List<StoryboardCharacterVisual> visuals = new ArrayList<>();
        for (var item : request.items()) {
            StoryboardCharacterVisual visual = new StoryboardCharacterVisual();
            visual.setVersionId(versionId);
            visual.setCharacterRefId(item.characterRefId());
            visual.setCharacterName(item.characterName());
            visual.setCoreIdentity(item.coreIdentity());
            visual.setDailyLook(item.dailyLook());
            visual.setTaskLook(item.taskLook());
            visual.setPerformanceAnchor(item.performanceAnchor());
            visual.setPromptLock(item.promptLock());
            visual.setSortOrder(visuals.size());
            characterVisualMapper.insert(visual);
            visuals.add(visual);
        }
        writeAudit(versionId, userId, "replace_visuals", "version", versionId, null);
        return visuals.stream().map(this::toCharacterVisualView).toList();
    }

    // ===== Visual Bindings =====

    public List<VisualBindingView> listVisualBindings(Long projectId, Long versionId, Long userId) {
        accessService.requireVersion(projectId, versionId, userId, Action.VIEW);
        return visualBindingMapper.selectList(
                new LambdaQueryWrapper<StoryboardShotVisualBinding>()
                        .eq(StoryboardShotVisualBinding::getVersionId, versionId))
                .stream().map(this::toVisualBindingView).toList();
    }

    @Transactional
    public List<VisualBindingView> replaceVisualBindings(Long projectId, Long versionId, Long userId,
                                                          ReplaceVisualBindingsRequest request) {
        var version = accessService.requireVersion(projectId, versionId, userId, Action.EDIT_CONTENT);
        requireEditable(version);
        versionService.bumpRevision(version, request.revision());

        visualBindingMapper.delete(
                new LambdaQueryWrapper<StoryboardShotVisualBinding>()
                        .eq(StoryboardShotVisualBinding::getVersionId, versionId));

        List<StoryboardShotVisualBinding> bindings = new ArrayList<>();
        for (var item : request.items()) {
            StoryboardShotVisualBinding binding = new StoryboardShotVisualBinding();
            binding.setVersionId(versionId);
            binding.setShotId(item.shotId());
            binding.setCharacterVisualId(item.characterVisualId());
            binding.setApplicationNote(item.applicationNote());
            binding.setAntiDriftRequirement(item.antiDriftRequirement());
            visualBindingMapper.insert(binding);
            bindings.add(binding);
        }
        writeAudit(versionId, userId, "replace_bindings", "version", versionId, null);
        return bindings.stream().map(this::toVisualBindingView).toList();
    }

    // ===== Helpers =====

    private void requireEditable(StoryboardVersion version) {
        if (!StoryboardStateMachine.isEditable(
                com.aicp.module.storyboard.domain.StoryboardEnums.VersionStatus.valueOf(
                        version.getStatus().toUpperCase()))) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_LOCKED);
        }
    }

    private void writeAudit(Long versionId, Long userId, String actionType,
                             String targetType, Long targetId, String detail) {
        StoryboardAuditLog log = new StoryboardAuditLog();
        log.setVersionId(versionId);
        log.setActorUserId(userId);
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setOperationId(UUID.randomUUID().toString());
        auditLogMapper.insert(log);
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (JsonProcessingException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, List.class); } catch (Exception e) { return List.of(); }
    }

    private EmotionSegmentView toEmotionSegmentView(StoryboardEmotionSegment s) {
        return new EmotionSegmentView(s.getId(), s.getEmotionType(), s.getShotRange(),
                s.getIntensity(), s.getCoreExpression(), s.getSortOrder());
    }

    private PromptTemplateView toPromptTemplateView(StoryboardPromptTemplate t) {
        return new PromptTemplateView(t.getId(), t.getTemplateCode(), t.getEmotionName(),
                parseJsonList(t.getShotRefsJson()), t.getImagePrompt(), t.getVideoMotionPrompt(), t.getSortOrder());
    }

    private CreativeRuleView toCreativeRuleView(StoryboardCreativeRule r) {
        return new CreativeRuleView(r.getId(), r.getRuleType(), r.getDimensionName(),
                r.getPrinciple(), r.getImplementationText(), parseJsonList(r.getTargetRefsJson()),
                r.getEffectText(), r.getStatus(), r.getSortOrder());
    }

    private CharacterVisualView toCharacterVisualView(StoryboardCharacterVisual v) {
        return new CharacterVisualView(v.getId(), v.getCharacterRefId(), v.getCharacterName(),
                v.getCoreIdentity(), v.getDailyLook(), v.getTaskLook(), v.getPerformanceAnchor(),
                v.getPromptLock(), v.getSortOrder());
    }

    private VisualBindingView toVisualBindingView(StoryboardShotVisualBinding b) {
        return new VisualBindingView(b.getId(), b.getShotId(), b.getCharacterVisualId(),
                b.getApplicationNote(), b.getAntiDriftRequirement());
    }
}
