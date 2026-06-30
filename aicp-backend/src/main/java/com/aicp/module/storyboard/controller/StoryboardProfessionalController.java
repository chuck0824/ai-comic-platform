package com.aicp.module.storyboard.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.storyboard.domain.ProductionGate.GateResult;
import com.aicp.module.storyboard.dto.StoryboardRequests.*;
import com.aicp.module.storyboard.dto.StoryboardViews.*;
import com.aicp.module.storyboard.service.StoryboardProfessionalService;
import com.aicp.module.storyboard.service.StoryboardReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}")
@RequiredArgsConstructor
public class StoryboardProfessionalController {

    private final StoryboardProfessionalService professionalService;
    private final StoryboardReviewService reviewService;

    // ===== Emotion Segments =====
    @GetMapping("/emotion-segments")
    public ApiResponse<List<EmotionSegmentView>> listEmotionSegments(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId) {
        return ApiResponse.success(professionalService.listEmotionSegments(
                projectId, versionId, SecurityUtil.requireCurrentUserId()));
    }

    @PutMapping("/emotion-segments")
    public ApiResponse<List<EmotionSegmentView>> replaceEmotionSegments(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId,
            @Valid @RequestBody ReplaceEmotionSegmentsRequest request) {
        return ApiResponse.success(professionalService.replaceEmotionSegments(
                projectId, versionId, SecurityUtil.requireCurrentUserId(), request));
    }

    // ===== Prompt Templates =====
    @GetMapping("/prompt-templates")
    public ApiResponse<List<PromptTemplateView>> listPromptTemplates(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId) {
        return ApiResponse.success(professionalService.listPromptTemplates(
                projectId, versionId, SecurityUtil.requireCurrentUserId()));
    }

    @PutMapping("/prompt-templates")
    public ApiResponse<List<PromptTemplateView>> replacePromptTemplates(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId,
            @Valid @RequestBody ReplacePromptTemplatesRequest request) {
        return ApiResponse.success(professionalService.replacePromptTemplates(
                projectId, versionId, SecurityUtil.requireCurrentUserId(), request));
    }

    // ===== Creative Rules =====
    @GetMapping("/creative-rules")
    public ApiResponse<List<CreativeRuleView>> listCreativeRules(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId) {
        return ApiResponse.success(professionalService.listCreativeRules(
                projectId, versionId, SecurityUtil.requireCurrentUserId()));
    }

    @PutMapping("/creative-rules")
    public ApiResponse<List<CreativeRuleView>> replaceCreativeRules(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId,
            @Valid @RequestBody ReplaceCreativeRulesRequest request) {
        return ApiResponse.success(professionalService.replaceCreativeRules(
                projectId, versionId, SecurityUtil.requireCurrentUserId(), request));
    }

    // ===== Character Visuals =====
    @GetMapping("/character-visuals")
    public ApiResponse<List<CharacterVisualView>> listCharacterVisuals(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId) {
        return ApiResponse.success(professionalService.listCharacterVisuals(
                projectId, versionId, SecurityUtil.requireCurrentUserId()));
    }

    @PutMapping("/character-visuals")
    public ApiResponse<List<CharacterVisualView>> replaceCharacterVisuals(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId,
            @Valid @RequestBody ReplaceCharacterVisualsRequest request) {
        return ApiResponse.success(professionalService.replaceCharacterVisuals(
                projectId, versionId, SecurityUtil.requireCurrentUserId(), request));
    }

    // ===== Visual Bindings =====
    @GetMapping("/visual-bindings")
    public ApiResponse<List<VisualBindingView>> listVisualBindings(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId) {
        return ApiResponse.success(professionalService.listVisualBindings(
                projectId, versionId, SecurityUtil.requireCurrentUserId()));
    }

    @PutMapping("/visual-bindings")
    public ApiResponse<List<VisualBindingView>> replaceVisualBindings(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId,
            @Valid @RequestBody ReplaceVisualBindingsRequest request) {
        return ApiResponse.success(professionalService.replaceVisualBindings(
                projectId, versionId, SecurityUtil.requireCurrentUserId(), request));
    }

    // ===== Review Issues =====
    @GetMapping("/review-issues")
    public ApiResponse<List<ReviewIssueView>> listReviewIssues(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId) {
        return ApiResponse.success(reviewService.listIssues(
                projectId, versionId, SecurityUtil.requireCurrentUserId()));
    }

    @PostMapping("/review-issues/{issueId}/resolve")
    public ApiResponse<ReviewIssueView> resolveIssue(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId,
            @PathVariable Long issueId,
            @Valid @RequestBody ResolveIssueRequest request) {
        return ApiResponse.success(reviewService.resolveIssue(
                projectId, versionId, issueId, SecurityUtil.requireCurrentUserId(),
                request.status(), request.resolutionNote()));
    }

    @PostMapping("/jobs/check")
    public ApiResponse<List<ReviewIssueView>> runChecks(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId) {
        return ApiResponse.success(reviewService.runChecks(
                projectId, versionId, SecurityUtil.requireCurrentUserId()));
    }

    @GetMapping("/gate")
    public ApiResponse<GateResult> evaluateGate(
            @PathVariable Long projectId, @PathVariable Long storyboardId, @PathVariable Long versionId) {
        return ApiResponse.success(reviewService.evaluateGate(
                projectId, versionId, SecurityUtil.requireCurrentUserId()));
    }
}
