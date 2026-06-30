package com.aicp.module.storyboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class StoryboardRequests {

    public record CreateStoryboardRequest(
            @NotNull Long contentUnitId,
            @NotNull Long sourceContentVersionId,
            @NotBlank String title,
            @NotBlank String purpose) {}

    public record CreateVersionRequest(
            @NotBlank String createdFrom) {}

    public record PatchShotRequest(
            Integer revision,
            Long durationMs,
            String shotSize,
            String visualDescription,
            String lightingAtmosphere,
            String characterAction,
            String emotionDescription,
            String dialogueText,
            List<String> sceneTags,
            String soundEffect,
            String referenceText,
            String imagePrompt,
            String videoMotionPrompt,
            String status) {}

    public record BatchPatchShotsRequest(
            @NotNull Integer revision,
            @NotEmpty List<ShotFieldPatch> patches) {}

    public record ShotFieldPatch(
            @NotNull Long shotId,
            Long durationMs,
            String shotSize,
            String visualDescription,
            String lightingAtmosphere,
            String characterAction,
            String emotionDescription,
            String dialogueText,
            List<String> sceneTags,
            String soundEffect,
            String referenceText,
            String imagePrompt,
            String videoMotionPrompt,
            String status) {}

    public record CreateSceneRequest(
            String title,
            String dramaticGoal,
            String beatDescription,
            Long locationRefId,
            String emotionLabel,
            Integer emotionIntensity) {}

    public record PatchSceneRequest(
            Integer revision,
            String title,
            String dramaticGoal,
            String beatDescription,
            Long locationRefId,
            Long durationMs,
            String emotionLabel,
            Integer emotionIntensity) {}

    public record CreateShotRequest(
            @NotNull Long sceneId,
            Long durationMs,
            String shotSize,
            String visualDescription,
            String lightingAtmosphere,
            String characterAction,
            String emotionDescription,
            String dialogueText,
            List<String> sceneTags,
            String soundEffect,
            String referenceText,
            String imagePrompt,
            String videoMotionPrompt) {}

    public record ReorderScenesRequest(
            @NotNull Integer revision,
            @NotEmpty List<ReorderItem> items) {}

    public record ReorderShotsRequest(
            @NotNull Integer revision,
            @NotEmpty List<ReorderShotItem> items) {}

    public record ReorderItem(
            @NotNull Long id,
            @NotNull Integer sortOrder) {}

    public record ReorderShotItem(
            @NotNull Long shotId,
            @NotNull Long sceneId,
            @NotNull Integer sortOrder) {}

    public record SplitShotRequest(
            @NotNull Long firstDurationMs) {}

    public record MergeShotsRequest(
            @NotNull Integer revision,
            @NotEmpty List<Long> shotIds) {}

    public record SubmitReviewRequest(@NotNull Integer revision) {}

    public record LockRequest(@NotNull Integer revision) {}

    public record ForkRequest(@NotBlank String idempotencyKey) {}

    public record UpgradeRequest(
            @NotBlank String targetTier,
            @NotBlank String idempotencyKey) {}

    public record ReplaceEmotionSegmentsRequest(
            int revision,
            @NotEmpty List<EmotionSegmentInput> items) {}

    public record EmotionSegmentInput(
            String emotionType,
            String shotRange,
            Integer intensity,
            String coreExpression,
            Integer sortOrder) {}

    public record ReplacePromptTemplatesRequest(
            int revision,
            @NotEmpty List<PromptTemplateInput> items) {}

    public record PromptTemplateInput(
            String templateCode,
            String emotionName,
            List<String> shotCodes,
            String imagePrompt,
            String videoMotionPrompt) {}

    public record ReplaceCreativeRulesRequest(
            int revision,
            @NotEmpty List<CreativeRuleInput> items) {}

    public record CreativeRuleInput(
            String ruleType,
            String dimensionName,
            String principle,
            String implementationText,
            List<String> targetRefs,
            String effectText,
            String status) {}

    public record ReplaceCharacterVisualsRequest(
            int revision,
            @NotEmpty List<CharacterVisualInput> items) {}

    public record CharacterVisualInput(
            Long characterRefId,
            String characterName,
            String coreIdentity,
            String dailyLook,
            String taskLook,
            String performanceAnchor,
            String promptLock) {}

    public record ReplaceVisualBindingsRequest(
            int revision,
            @NotEmpty List<VisualBindingInput> items) {}

    public record VisualBindingInput(
            Long shotId,
            Long characterVisualId,
            String applicationNote,
            String antiDriftRequirement) {}

    public record ResolveIssueRequest(
            @NotBlank String status,
            String resolutionNote) {}

    public record CreateJobRequest(
            @NotBlank String jobType,
            @NotBlank String idempotencyKey,
            String requestJson) {}

    public record CreateCanvasSnapshotRequest(
            @NotBlank String snapshotType,
            @NotBlank String idempotencyKey) {}

    public record ImportWorkbookRequest(
            @NotBlank String idempotencyKey) {}

    public record ExportRequest(
            @NotBlank String exportMode,
            @NotBlank String idempotencyKey) {}

    private StoryboardRequests() {}
}
