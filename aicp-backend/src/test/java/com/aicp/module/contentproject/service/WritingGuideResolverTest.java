package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.entity.ProjectWritingGuide;
import com.aicp.module.contentproject.mapper.ProjectWritingGuideMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WritingGuideResolver 单元测试")
class WritingGuideResolverTest {

    @Mock ProjectWritingGuideMapper guideMapper;
    @Mock ObjectMapper objectMapper;
    @InjectMocks WritingGuideResolver resolver;

    @Test
    @DisplayName("单集覆盖显式字段并继承项目字段")
    void unitOverridesExplicitFieldAndInheritsRemainingProjectFields() throws Exception {
        when(guideMapper.selectList(any())).thenReturn(List.of(
                guide(1L, "project", 0L, "{\"pov\":\"third\",\"pace\":\"fast\",\"hard_bans\":[\"辱骂\"]}"),
                guide(2L, "content_unit", 8L, "{\"pace\":\"slow\"}")
        ));
        when(objectMapper.readValue(any(String.class), eq(Map.class)))
                .thenAnswer(inv -> new ObjectMapper().readValue((String) inv.getArgument(0), Map.class));

        var result = resolver.resolve(4L, 3L, 8L, List.of());

        assertThat(result.resolved()).containsEntry("pov", "third");
        assertThat(result.resolved()).containsEntry("pace", "slow");
        assertThat(result.sourceByField()).containsEntry("pace", "content_unit:2");
    }

    @Test
    @DisplayName("角色口径不可覆盖 hard_bans")
    void characterGuideCannotOverrideHardBans() throws Exception {
        when(guideMapper.selectList(any())).thenReturn(List.of(
                guide(1L, "project", 0L, "{\"hard_bans\":[\"辱骂\"]}"),
                guide(3L, "character", 9L, "{\"hard_bans\":[]}")
        ));
        when(objectMapper.readValue(any(String.class), eq(Map.class)))
                .thenAnswer(inv -> new ObjectMapper().readValue((String) inv.getArgument(0), Map.class));

        var result = resolver.resolve(4L, 3L, null, List.of(9L));

        assertThat(result.resolved().toString()).contains("辱骂");
        assertThat(result.conflicts()).contains("characters.9.hard_bans");
    }

    @Test
    @DisplayName("角色口径不可覆盖 platform_rules")
    void characterGuideCannotOverridePlatformRules() throws Exception {
        when(guideMapper.selectList(any())).thenReturn(List.of(
                guide(1L, "project", 0L, "{\"platform_rules\":[\"禁止色情描写\"]}"),
                guide(3L, "character", 9L, "{\"platform_rules\":[\"允许擦边\"]}")
        ));
        when(objectMapper.readValue(any(String.class), eq(Map.class)))
                .thenAnswer(inv -> new ObjectMapper().readValue((String) inv.getArgument(0), Map.class));

        var result = resolver.resolve(4L, 3L, null, List.of(9L));

        assertThat(result.resolved().toString()).contains("禁止色情描写");
        assertThat(result.conflicts()).contains("characters.9.platform_rules");
    }

    @Test
    @DisplayName("角色口径不可覆盖 compliance_rules")
    void characterGuideCannotOverrideComplianceRules() throws Exception {
        when(guideMapper.selectList(any())).thenReturn(List.of(
                guide(1L, "project", 0L, "{\"compliance_rules\":[\"禁止未成年人饮酒\"]}"),
                guide(3L, "character", 9L, "{\"compliance_rules\":[]}")
        ));
        when(objectMapper.readValue(any(String.class), eq(Map.class)))
                .thenAnswer(inv -> new ObjectMapper().readValue((String) inv.getArgument(0), Map.class));

        var result = resolver.resolve(4L, 3L, null, List.of(9L));

        assertThat(result.resolved().toString()).contains("禁止未成年人饮酒");
        assertThat(result.conflicts()).contains("characters.9.compliance_rules");
    }

    @Test
    @DisplayName("无项目口径时返回空结果")
    void noProjectGuideReturnsEmptyResolution() throws Exception {
        when(guideMapper.selectList(any())).thenReturn(List.of());

        var result = resolver.resolve(4L, 3L, null, List.of());

        assertThat(result.resolved()).isEmpty();
        assertThat(result.projectGuideId()).isNull();
    }

    @Test
    @DisplayName("多个角色分别解析且排序确定")
    void multipleCharactersResolvedWithSortedIds() throws Exception {
        when(guideMapper.selectList(any())).thenReturn(List.of(
                guide(1L, "project", 0L, "{\"pov\":\"third\"}"),
                guide(3L, "character", 9L, "{\"addressing\":\"尊称\"}"),
                guide(4L, "character", 5L, "{\"addressing\":\"昵称\"}")
        ));
        when(objectMapper.readValue(any(String.class), eq(Map.class)))
                .thenAnswer(inv -> new ObjectMapper().readValue((String) inv.getArgument(0), Map.class));

        var result = resolver.resolve(4L, 3L, null, List.of(9L, 5L));

        assertThat(result.characterGuideIds()).hasSize(2);
        assertThat(result.characterGuideIds().get(0)).isLessThan(result.characterGuideIds().get(1));
        // Character 5 (id=4, sorted first) + Character 9 (id=3)
        assertThat(result.characterGuideIds()).containsExactly(3L, 4L); // 5 < 9, so char 5 first
    }

    // ── helpers ──

    private ProjectWritingGuide guide(Long id, String scopeType, Long scopeId, String guideJson) {
        ProjectWritingGuide g = new ProjectWritingGuide();
        g.setId(id);
        g.setScopeType(scopeType);
        g.setScopeId(scopeId != null ? scopeId : 0L);
        g.setGuideJson(guideJson);
        g.setStatus("confirmed");
        g.setBibleVersionId(3L);
        g.setProjectId(4L);
        return g;
    }
}
