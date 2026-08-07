package com.aicp.module.contentproject.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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
            List<@Valid CreateVariantRequest> variants) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record UpdateSceneAssetRequest(
            @Pattern(regexp = ".*\\S.*", message = "name 不能为空白") String name,
            @Pattern(regexp = ".*\\S.*", message = "space_type 不能为空白") String spaceType,
            @Pattern(regexp = ".*\\S.*", message = "reusability 不能为空白") String reusability,
            @Pattern(regexp = ".*\\S.*", message = "reality_type 不能为空白") String realityType,
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
    record FromWorldLocationRequest(
            @NotBlank String worldLocationRef,
            @NotBlank String name,
            String spaceType,
            String reusability,
            String realityType,
            String layout,
            String lighting) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CreateSceneVariantRequest(
            @NotBlank String name,
            String time,
            String lightingDelta,
            Object prompts,
            Object references) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record UpdateSceneVariantRequest(
            @Pattern(regexp = ".*\\S.*", message = "name 不能为空白") String name,
            String time,
            String lightingDelta,
            Object prompts,
            Object references) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RestoreSceneAssetRequest(String changeNote) {}
}
