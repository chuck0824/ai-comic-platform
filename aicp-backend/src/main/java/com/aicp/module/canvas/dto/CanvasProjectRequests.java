package com.aicp.module.canvas.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public interface CanvasProjectRequests {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CreateCanvasProjectRequest(
            @NotBlank @Size(max = 200) String name,
            Long contentProjectId,
            @Size(max = 32) String productionUnitType,
            Long productionUnitId,
            Long sourceContentVersionId,
            Long sourceStoryboardVersionId,
            @Pattern(regexp = "official|alternative|experiment") String purpose,
            @NotNull Long ownerId,
            @NotBlank @Size(max = 200) String idempotencyKey) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CanvasProjectQuery(
            Integer page,
            Integer pageSize,
            String status,
            String creationMode,
            Long contentProjectId,
            String keyword) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CopyCanvasProjectRequest(
            @NotBlank @Size(max = 200) String name,
            Long targetContentProjectId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record MoveCanvasProjectRequest(
            @NotNull Long targetProductionUnitId,
            @NotBlank @Size(max = 32) String targetProductionUnitType) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record UpdateCanvasProjectRequest(
            @Size(max = 200) String name,
            String status,
            String thumbnailUrl,
            Integer revision) {}
}
