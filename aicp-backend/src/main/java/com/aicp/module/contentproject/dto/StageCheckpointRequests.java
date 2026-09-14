package com.aicp.module.contentproject.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

public final class StageCheckpointRequests {

    private StageCheckpointRequests() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StageTransitionRequest(
            String sourceStageKey,
            String targetStageKey,
            String action,
            Integer projectRevision,
            Integer sourceCheckpointRevision,
            Integer targetCheckpointRevision,
            Long contentUnitId,
            Integer contentUnitRevision,
            List<String> warningAcknowledgements
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StageForkRequest(
            Integer checkpointRevision,
            Long baseAdoptedContentVersionId,
            Integer projectRevision
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StalenessResolutionRequest(
            String action,
            Integer checkpointRevision,
            Map<String, Object> reasonFilter,
            String note
    ) {}
}
