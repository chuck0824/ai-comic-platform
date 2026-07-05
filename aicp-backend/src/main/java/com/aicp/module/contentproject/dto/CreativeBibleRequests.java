package com.aicp.module.contentproject.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;

public interface CreativeBibleRequests {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CreateBibleDraftRequest(
            String summary,
            Long sourceVersionId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record UpsertEcosystemRuleRequest(
            @NotBlank String ruleType,
            @NotBlank String name,
            String summary,
            Map<String, Object> details,
            Map<String, Object> scope,
            List<Map<String, Object>> exceptions,
            String sourceType,
            Integer revision) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record UpsertWritingGuideRequest(
            @Pattern(regexp = "project|character|content_unit") String scopeType,
            Long scopeId,
            Map<String, Object> guide,
            Long parentGuideId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ResolveWritingGuideRequest(
            Long contentUnitId,
            List<Long> characterIds) {}
}
