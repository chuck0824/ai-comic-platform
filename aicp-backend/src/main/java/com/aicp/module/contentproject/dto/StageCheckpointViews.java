package com.aicp.module.contentproject.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

public final class StageCheckpointViews {

    private StageCheckpointViews() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StageProjection(
            Long projectId,
            Integer projectRevision,
            List<StageCheckpointView> stages,
            String lastStageKey,
            boolean stageTruthEnabled,
            StoryboardHandoffView latestStoryboardHandoff
    ) {
        /** 兼容未附带交接快照的旧构造调用 */
        public StageProjection(
                Long projectId,
                Integer projectRevision,
                List<StageCheckpointView> stages,
                String lastStageKey,
                boolean stageTruthEnabled) {
            this(projectId, projectRevision, stages, lastStageKey, stageTruthEnabled, null);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StageCheckpointView(
            String stageKey,
            String state,
            Integer checkpointRevision,
            String primaryArtifactType,
            Long primaryArtifactId,
            Long adoptedContentVersionId,
            Map<String, Object> gateSummary,
            List<Map<String, Object>> staleReasons,
            List<String> allowedActions
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GateView(
            String stageKey,
            List<Map<String, Object>> blockers,
            List<Map<String, Object>> warnings,
            Map<String, Object> evidence
    ) {}

    /** R2-B 可读的文字分镜交接快照（最小字段） */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StoryboardHandoffView(
            Long id,
            Long projectId,
            Long checkpointId,
            Long reviewedScriptBodyVersionId,
            String continuityCheckResult,
            Integer sceneCount,
            String contentHash,
            String capturedAt,
            Map<String, Object> payload
    ) {}
}
