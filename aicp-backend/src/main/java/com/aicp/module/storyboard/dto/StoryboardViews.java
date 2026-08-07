package com.aicp.module.storyboard.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class StoryboardViews {

    public record StoryboardSummary(
            Long id,
            String uuid,
            Long projectId,
            Long contentUnitId,
            String title,
            String purpose,
            String currentTier,
            Integer currentVersionNo,
            String productionStatus,
            Integer totalShots,
            Integer totalScenes,
            Long totalDurationMs,
            Integer openIssueCount,
            String editorPath,
            LocalDateTime updatedAt) {}

    public record StoryboardDetail(
            Long id,
            String uuid,
            Long projectId,
            Long contentUnitId,
            Long sourceContentVersionId,
            String title,
            String purpose,
            Long currentDraftVersionId,
            Long currentLockedVersionId,
            String productionStatus,
            Long createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record VersionSummary(
            Long id,
            String uuid,
            Long storyboardId,
            Long parentVersionId,
            String tier,
            Integer versionNo,
            String status,
            Integer revision,
            Integer totalScenes,
            Integer totalShots,
            Long totalDurationMs,
            String createdFrom,
            Long lockedBy,
            LocalDateTime lockedAt,
            LocalDateTime createdAt) {}

    public record VersionDetail(
            Long id,
            String uuid,
            Long storyboardId,
            Long parentVersionId,
            Long sourceContentVersionId,
            String tier,
            Integer versionNo,
            String status,
            Integer revision,
            Integer schemaVersion,
            Integer totalScenes,
            Integer totalShots,
            Long totalDurationMs,
            String createdFrom,
            Long lockedBy,
            LocalDateTime lockedAt,
            Long createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record VersionDiff(
            Long versionId,
            Long comparedVersionId,
            List<FieldDiff> sceneDiffs,
            List<FieldDiff> shotDiffs) {}

    public record FieldDiff(
            String entityType,
            Long entityId,
            String fieldName,
            Object oldValue,
            Object newValue) {}

    public record SceneView(
            Long id,
            String sceneKey,
            Integer sceneNo,
            String title,
            String dramaticGoal,
            String beatDescription,
            Long locationRefId,
            Long durationMs,
            String emotionLabel,
            Integer emotionIntensity,
            Integer sortOrder,
            Integer shotCount) {}

    public record ShotSummary(
            Long id,
            String uuid,
            Long versionId,
            Long sceneId,
            String shotKey,
            String shotCode,
            Long durationMs,
            String shotSize,
            String visualDescriptionSummary,
            String dialogueText,
            String status,
            Integer sortOrder,
            Long sceneAssetId,
            Long sceneAssetVersionId,
            String sceneVariantId,
            Integer sceneVariantVersion,
            Map<String, Object> sceneAssetSnapshot) {}

    public record ShotDetail(
            Long id,
            String uuid,
            Long versionId,
            Long sceneId,
            String shotKey,
            String shotCode,
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
            String directorIntention,
            String actionMotivation,
            String relationshipBlocking,
            String informationGap,
            String audioVisualRelation,
            String editPoint,
            String dubText,
            String subtitleText,
            String failureStrategy,
            String status,
            Integer sortOrder,
            Long sceneAssetId,
            Long sceneAssetVersionId,
            String sceneVariantId,
            Integer sceneVariantVersion,
            Map<String, Object> sceneAssetSnapshot) {}

    public record ContinuityCheckView(
            boolean valid,
            List<ContinuityIssueView> issues) {}

    public record ContinuityIssueView(
            String code,
            Long shotId,
            String shotCode,
            String message,
            String repairAction) {}

    public record EmotionSegmentView(
            Long id,
            String emotionType,
            String shotRange,
            Integer intensity,
            String coreExpression,
            Integer sortOrder) {}

    public record PromptTemplateView(
            Long id,
            String templateCode,
            String emotionName,
            List<String> shotRefs,
            String imagePrompt,
            String videoMotionPrompt,
            Integer sortOrder) {}

    public record CreativeRuleView(
            Long id,
            String ruleType,
            String dimensionName,
            String principle,
            String implementationText,
            List<String> targetRefs,
            String effectText,
            String status,
            Integer sortOrder) {}

    public record CharacterVisualView(
            Long id,
            Long characterRefId,
            String characterName,
            String coreIdentity,
            String dailyLook,
            String taskLook,
            String performanceAnchor,
            String promptLock,
            Integer sortOrder) {}

    public record VisualBindingView(
            Long id,
            Long shotId,
            Long characterVisualId,
            String applicationNote,
            String antiDriftRequirement) {}

    public record ReviewIssueView(
            Long id,
            String fingerprint,
            String issueType,
            String severity,
            Long shotId,
            String message,
            String evidence,
            String suggestion,
            String status,
            String resolutionNote,
            Long resolvedBy,
            LocalDateTime resolvedAt) {}

    public record JobView(
            Long id,
            String uuid,
            Long projectId,
            Long storyboardId,
            Long versionId,
            String jobType,
            String status,
            String idempotencyKey,
            Integer progressPercent,
            String currentStage,
            String errorCode,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt) {}

    public record CanvasSnapshotView(
            Long id,
            String uuid,
            Long projectId,
            Long storyboardId,
            Long versionId,
            String snapshotType,
            String snapshotHash,
            LocalDateTime createdAt) {}

    private StoryboardViews() {}
}
