package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.module.contentproject.dto.ContentProjectRequests.GenerationJobRequest;
import com.aicp.module.contentproject.dto.ContentProjectViews.ContextSnapshot;
import com.aicp.module.contentproject.dto.CreativeBibleViews.ResolvedWritingGuideView;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.entity.CreativeBibleVersion;
import com.aicp.module.contentproject.entity.EcosystemRule;
import com.aicp.module.contentproject.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContextAssembler Creative Bible 集成测试")
class ContextAssemblerCreativeBibleTest {

    @Mock ContentProjectMapper projectMapper;
    @Mock ProjectParameterVersionMapper parameterMapper;
    @Mock ContentVersionMapper contentVersionMapper;
    @Mock CreativeBibleVersionMapper bibleMapper;
    @Mock EcosystemRuleMapper ecosystemMapper;
    @Mock ProjectSettingEntityMapper settingMapper;
    @Mock WritingGuideResolver guideResolver;
    @Mock ObjectMapper objectMapper;
    @InjectMocks ContextAssembler assembler;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(objectMapper.readValue(anyString(), eq(Object.class))).thenReturn(Map.of());
        lenient().when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(Map.of());
        lenient().when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(Map.of());
    }

    @Test
    @DisplayName("缺少已确认圣经时抛出异常")
    void assembleThrowsWhenNoConfirmedBibleExists() {
        when(projectMapper.selectById(3L)).thenReturn(activeProject(3L));
        GenerationJobRequest request = new GenerationJobRequest(
                "synopsis", "project", 3L, Map.of(), "v1", null);

        assertThatThrownBy(() -> assembler.assemble(3L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("创作圣经尚未确认");
    }

    @Test
    @DisplayName("策略中 allow_unconfirmed_bible 允许绕过圣经检查")
    void allowUnconfirmedBibleBypassesCheck() throws Exception {
        when(projectMapper.selectById(3L)).thenReturn(activeProject(3L));
        when(bibleMapper.selectOne(any())).thenReturn(null);
        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenReturn(Map.of("allow_unconfirmed_bible", true));
        GenerationJobRequest request = new GenerationJobRequest(
                "synopsis", "project", 3L, Map.of(),
                "{\"allow_unconfirmed_bible\":true}", null);

        ContextSnapshot snapshot = assembler.assemble(3L, request);

        assertThat(snapshot.bibleVersionId()).isNull();
    }

    @Test
    @DisplayName("已确认圣经时组装包含圣经版本和口径")
    void assembleIncludesConfirmedBibleAndResolvedGuide() throws Exception {
        when(projectMapper.selectById(3L)).thenReturn(activeProject(3L));
        CreativeBibleVersion bible = confirmedBible(11L, 2);
        bible.setSnapshotJson("{\"ecosystem_rules\":[{\"rule_type\":\"world_rule\",\"name\":\"能力有代价\"}]}");
        when(bibleMapper.selectOne(any())).thenReturn(bible);
        when(objectMapper.readValue(eq("{\"ecosystem_rules\":[{\"rule_type\":\"world_rule\",\"name\":\"能力有代价\"}]}"), eq(Object.class)))
                .thenReturn(Map.of("ecosystem_rules", List.of(Map.of("rule_type", "world_rule", "name", "能力有代价"))));
        when(guideResolver.resolve(eq(3L), eq(11L), eq(8L), anyList()))
                .thenReturn(new ResolvedWritingGuideView(
                        Map.of("pov", "third", "pace", "fast"),
                        Map.of("pov", "project:1"),
                        List.of(), 1L, List.of(), null));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"pov\":\"third\",\"pace\":\"fast\"}");

        GenerationJobRequest request = new GenerationJobRequest(
                "synopsis", "content_unit", 8L, Map.of(), "v1", null);

        ContextSnapshot snapshot = assembler.assemble(3L, request);

        assertThat(snapshot.selectedVersions()).containsEntry("creative_bible", 11L);
        assertThat(snapshot.bibleVersionId()).isEqualTo(11L);
        assertThat(snapshot.projectGuideId()).isEqualTo(1L);
        assertThat(snapshot.resolvedGuideJson()).contains("pace");
    }

    @Test
    @DisplayName("显式选择的候选或已丢弃版本不得进入生成上下文")
    void explicitSelectedVersionMustBePublic() {
        when(projectMapper.selectById(3L)).thenReturn(activeProject(3L));
        GenerationJobRequest request = new GenerationJobRequest(
                "content_generate", "content_unit", 8L, Map.of("script_body", 55L),
                "{\"allow_unconfirmed_bible\":true}", null);

        for (String status : List.of("candidate", "discarded")) {
            ContentVersion hidden = new ContentVersion();
            hidden.setId(55L);
            hidden.setProjectId(3L);
            hidden.setStatus(status);
            hidden.setContentJson("{}");
            when(contentVersionMapper.selectById(55L)).thenReturn(hidden);
            assertThatThrownBy(() -> assembler.assemble(3L, request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("未采用");
        }
    }

    // ── helpers ──

    private ContentProject activeProject(Long id) {
        ContentProject p = new ContentProject();
        p.setId(id);
        p.setName("测试项目");
        p.setIsDeleted(0);
        p.setCreationMode("long_form");
        return p;
    }

    private CreativeBibleVersion confirmedBible(Long id, int versionNo) {
        CreativeBibleVersion v = new CreativeBibleVersion();
        v.setId(id);
        v.setProjectId(3L);
        v.setVersionNo(versionNo);
        v.setStatus("confirmed");
        v.setSnapshotJson("{}");
        return v;
    }
}
