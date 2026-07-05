package com.aicp.module.contentproject.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

public interface CreativeBibleViews {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BibleSummaryView(
            Long id,
            Long projectId,
            Integer versionNo,
            String status,
            Long sourceVersionId,
            String summary,
            String snapshotHash,
            Long confirmedBy,
            String confirmedAt,
            String createdAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record EcosystemRuleView(
            Long id,
            Long projectId,
            Long bibleVersionId,
            String ruleType,
            String name,
            String summary,
            Object details,
            Object scope,
            Object exceptions,
            String status,
            String sourceType,
            Object evidence,
            Integer revision,
            String createdAt,
            String updatedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record WritingGuideView(
            Long id,
            Long projectId,
            Long bibleVersionId,
            String scopeType,
            Long scopeId,
            Integer versionNo,
            String status,
            Object guide,
            Long parentGuideId,
            String sourceType,
            Long confirmedBy,
            String confirmedAt,
            String createdAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ResolvedWritingGuideView(
            Map<String, Object> resolved,
            Map<String, String> sourceByField,
            List<String> conflicts,
            Long projectGuideId,
            List<Long> characterGuideIds,
            Long unitGuideId) {}
}
