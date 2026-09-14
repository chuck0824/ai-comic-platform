package com.aicp.module.contentproject.service.stage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StageStructureImpactTest {

    private final StageStructureImpact impact = new StageStructureImpact(new ObjectMapper());

    @Test
    void textOnlyChangeDoesNotAlterStructuralHash() {
        String before = json(Map.of(
                "episodes", List.of(Map.of(
                        "id", "EP-1",
                        "scenes", List.of(Map.of(
                                "id", "S1",
                                "blocks", List.of(Map.of("id", "B1", "text", "甲推门"))
                        ))
                ))
        ));
        String after = json(Map.of(
                "episodes", List.of(Map.of(
                        "id", "EP-1",
                        "scenes", List.of(Map.of(
                                "id", "S1",
                                "blocks", List.of(Map.of("id", "B1", "text", "乙推门"))
                        ))
                ))
        ));
        StageStructureImpact.Delta delta = impact.compare(before, null, after, null);
        assertThat(delta.level()).isEqualTo(StageStructureImpact.Level.TEXT_ONLY);
        assertThat(delta.before().structuralHash()).isEqualTo(delta.after().structuralHash());
    }

    @Test
    void addingSceneRequiresRegen() {
        String before = json(Map.of(
                "episodes", List.of(Map.of(
                        "id", "EP-1",
                        "scenes", List.of(Map.of("id", "S1", "blocks", List.of()))
                ))
        ));
        String after = json(Map.of(
                "episodes", List.of(Map.of(
                        "id", "EP-1",
                        "scenes", List.of(
                                Map.of("id", "S1", "blocks", List.of()),
                                Map.of("id", "S2", "blocks", List.of())
                        )
                ))
        ));
        StageStructureImpact.Delta delta = impact.compare(before, null, after, null);
        assertThat(delta.level()).isEqualTo(StageStructureImpact.Level.STRUCTURE);
        assertThat(delta.after().sceneCount()).isEqualTo(2);
    }

    @Test
    void canonicalSha256IsStableAcrossMapInsertionOrder() {
        Map<String, Object> a = new java.util.LinkedHashMap<>();
        a.put("b", 2);
        a.put("a", 1);
        Map<String, Object> b = new java.util.LinkedHashMap<>();
        b.put("a", 1);
        b.put("b", 2);
        assertThat(impact.canonicalSha256(a)).isEqualTo(impact.canonicalSha256(b));
    }

    private String json(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
