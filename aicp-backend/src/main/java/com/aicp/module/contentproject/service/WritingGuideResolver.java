package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.dto.CreativeBibleViews.ResolvedWritingGuideView;
import com.aicp.module.contentproject.entity.ProjectWritingGuide;
import com.aicp.module.contentproject.mapper.ProjectWritingGuideMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingGuideResolver {

    private static final Set<String> NON_OVERRIDABLE = Set.of(
            "hard_bans", "platform_rules", "compliance_rules");

    private final ProjectWritingGuideMapper guideMapper;
    private final ObjectMapper objectMapper;

    /**
     * Resolve the final writing guide by merging L1 (project) → L2 (character) → L3 (content_unit).
     * <p>
     * Merge order: project baseline → each requested character (sorted by id) → content unit.
     * Character values live under resolved.characters.&lt;characterId&gt;.
     * Unit values override project scalar fields only when explicitly present.
     * NON_OVERRIDABLE keys cannot be overridden by character or unit guides.
     */
    public ResolvedWritingGuideView resolve(Long projectId, Long bibleVersionId,
                                             Long contentUnitId, List<Long> characterIds) {
        // Load confirmed guides for this bible version
        List<ProjectWritingGuide> allGuides = guideMapper.selectList(
                new LambdaQueryWrapper<ProjectWritingGuide>()
                        .eq(ProjectWritingGuide::getProjectId, projectId)
                        .eq(ProjectWritingGuide::getBibleVersionId, bibleVersionId)
                        .eq(ProjectWritingGuide::getStatus, "confirmed"));

        // Find project-level guide (scope_type=project, scope_id=0)
        ProjectWritingGuide projectGuide = allGuides.stream()
                .filter(g -> "project".equals(g.getScopeType()))
                .findFirst().orElse(null);

        // Find character guides for requested IDs, sorted for determinism
        List<Long> sortedCharIds = characterIds != null
                ? characterIds.stream().sorted().distinct().toList()
                : List.of();
        List<ProjectWritingGuide> charGuides = allGuides.stream()
                .filter(g -> "character".equals(g.getScopeType())
                        && sortedCharIds.contains(g.getScopeId()))
                .toList();

        // Find unit guide
        ProjectWritingGuide unitGuide = null;
        if (contentUnitId != null) {
            unitGuide = allGuides.stream()
                    .filter(g -> "content_unit".equals(g.getScopeType())
                            && contentUnitId.equals(g.getScopeId()))
                    .findFirst().orElse(null);
        }

        // Merge
        Map<String, Object> resolved = new LinkedHashMap<>();
        Map<String, String> sourceByField = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();

        // Step 1: project baseline
        Map<String, Object> projectMap = parseGuide(projectGuide);
        if (projectMap != null) {
            projectMap.forEach((k, v) -> {
                resolved.put(k, v);
                sourceByField.put(k, "project:" + (projectGuide != null ? projectGuide.getId() : 0));
            });
        }

        // Step 2: each character (sorted by id)
        Map<String, Map<String, Object>> characterResolved = new LinkedHashMap<>();
        for (ProjectWritingGuide cg : charGuides) {
            Map<String, Object> charMap = parseGuide(cg);
            if (charMap == null) continue;
            Map<String, Object> charResult = new LinkedHashMap<>();
            String prefix = "characters." + cg.getScopeId();
            for (Map.Entry<String, Object> entry : charMap.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                if (NON_OVERRIDABLE.contains(field)) {
                    // Cannot override non-overridable fields from project level
                    Object projectValue = resolved.get(field);
                    if (projectValue != null && !projectValue.equals(value)) {
                        conflicts.add(prefix + "." + field);
                        charResult.put(field, projectValue); // keep project value
                    } else {
                        charResult.put(field, value);
                    }
                } else {
                    charResult.put(field, value);
                    sourceByField.put(prefix + "." + field, "character:" + cg.getId());
                }
            }
            characterResolved.put(prefix, charResult);
        }

        // Step 3: content unit (L3 override)
        Map<String, Object> unitMap = parseGuide(unitGuide);
        if (unitMap != null) {
            for (Map.Entry<String, Object> entry : unitMap.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                if (NON_OVERRIDABLE.contains(field)) {
                    Object projectValue = resolved.get(field);
                    if (projectValue != null && !projectValue.equals(value)) {
                        conflicts.add("content_unit." + field);
                    }
                    // Keep project value for non-overridable fields
                } else {
                    resolved.put(field, value);
                    sourceByField.put(field, "content_unit:" + (unitGuide != null ? unitGuide.getId() : 0));
                }
            }
        }

        // Embed character results
        if (!characterResolved.isEmpty()) {
            resolved.put("characters", characterResolved);
        }

        return new ResolvedWritingGuideView(
                resolved,
                sourceByField,
                conflicts,
                projectGuide != null ? projectGuide.getId() : null,
                charGuides.stream().map(ProjectWritingGuide::getId).toList(),
                unitGuide != null ? unitGuide.getId() : null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseGuide(ProjectWritingGuide guide) {
        if (guide == null || guide.getGuideJson() == null) return null;
        try {
            return objectMapper.readValue(guide.getGuideJson(), Map.class);
        } catch (JsonProcessingException e) {
            log.warn("写作口径 JSON 解析失败: guideId={}", guide.getId(), e);
            return null;
        }
    }
}
