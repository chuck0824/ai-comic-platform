package com.aicp.module.contentproject.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Request contracts for project-scoped scene masters and their variants. */
public interface ProjectSceneAssetRequests {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CreateSceneAssetRequest(
            @NotBlank String name,
            @NotBlank String spaceType,
            @NotBlank String reusability,
            @NotBlank String realityType,
            String worldLocationRef,
            String layout,
            Object materials,
            Object palette,
            String lighting,
            Object landmarks,
            Object fixedProps,
            Object movableProps,
            Object entrancesExits,
            List<String> continuityRules,
            Object references,
            Object prompts,
            List<CreateVariantRequest> variants) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record UpdateSceneAssetRequest(
            String name,
            String spaceType,
            String reusability,
            String realityType,
            String worldLocationRef,
            String layout,
            Object materials,
            Object palette,
            String lighting,
            Object landmarks,
            Object fixedProps,
            Object movableProps,
            Object entrancesExits,
            List<String> continuityRules,
            Object references,
            Object prompts,
            List<CreateVariantRequest> variants,
            String changeNote) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CreateVariantRequest(
            String id,
            Integer version,
            @NotBlank String name,
            String time,
            String lightingDelta,
            Object prompts,
            Object references) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RestoreSceneAssetRequest(String changeNote) {}
}
