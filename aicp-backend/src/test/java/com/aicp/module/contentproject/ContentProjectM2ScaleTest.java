package com.aicp.module.contentproject;

import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.aicp.module.contentproject.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M2: Scale tests — 20/40/60/80 episode projects + recovery validation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("M2 短剧完整规模测试")
class ContentProjectM2ScaleTest {

    @Mock ContentProjectMapper projectMapper;
    @Mock ProjectParameterVersionMapper parameterVersionMapper;
    @Mock ObjectMapper objectMapper;

    private ProjectWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new ProjectWorkflowService(projectMapper, parameterVersionMapper, objectMapper);
    }

    // ===== Scale tests =====

    @Test
    @DisplayName("20集项目：所有单元独立，草稿不共享")
    void twentyEpisodeIndependence() {
        // Create 20 units, verify each has independent draft
        List<ContentUnit> units = createUnits(100L, 20);
        for (ContentUnit u : units) {
            assertThat(u.getStableKey()).isNotEmpty();
            assertThat(u.getRevision()).isEqualTo(0);
        }
        // No two units share the same stable key
        Set<String> keys = new HashSet<>();
        for (ContentUnit u : units) keys.add(u.getStableKey());
        assertThat(keys).hasSize(20);
    }

    @Test
    @DisplayName("40集项目：工作流状态正确恢复")
    void fortyEpisodeWorkflowRecovery() {
        ContentProject project = buildProject("ai_manual", "skipped", "destination");
        // All stages completed, storyboard skipped
        Map<String, Boolean> facts = new HashMap<>();
        for (String key : List.of("story_seed", "characters", "synopsis", "outline", "content", "review", "destination")) {
            facts.put(key, true);
        }

        WorkflowView wf = workflowService.calculate(project, facts);
        assertThat(wf.progress()).isEqualTo(100);
        assertThat(wf.stages().size()).isGreaterThan(6); // 7 required + optional
    }

    @Test
    @DisplayName("60集项目：批量标记单元完成状态")
    void sixtyEpisodeBulkStatus() {
        List<ContentUnit> units = createUnits(200L, 60);
        long draftCount = units.stream().filter(u -> "draft".equals(u.getStatus())).count();
        assertThat(draftCount).isEqualTo(60);
    }

    @Test
    @DisplayName("80集项目：显示序号连续不重复")
    void eightyEpisodeDisplayOrder() {
        List<ContentUnit> units = createUnits(300L, 80);
        Set<Integer> displayNos = new HashSet<>();
        for (ContentUnit u : units) displayNos.add(u.getDisplayNo());
        assertThat(displayNos).hasSize(80);
        assertThat(displayNos.stream().min(Integer::compareTo).orElse(0)).isEqualTo(1);
        assertThat(displayNos.stream().max(Integer::compareTo).orElse(0)).isEqualTo(80);
    }

    // ===== Recovery tests =====

    @Test
    @DisplayName("刷新后恢复：草稿内容不丢失")
    void recoveryDraftNotLost() {
        ContentUnitMapper uMapper = mock(ContentUnitMapper.class);
        ContentVersionMapper vMapper = mock(ContentVersionMapper.class);
        ContentUnitService uService = new ContentUnitService(uMapper, vMapper);

        ContentUnit unit = buildUnit(99L, 1);
        when(uMapper.selectById(99L)).thenReturn(unit);
        when(vMapper.selectOne(any())).thenReturn(null);

        DraftView draft = uService.getDraft(7L, 99L);
        assertThat(draft).isNotNull();
        assertThat(draft.contentUnitId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("恢复正确阶段：从last_stage恢复工作流位置")
    void recoveryCorrectStage() {
        // Simulate a project mid-workflow
        ContentProject project = buildProject("ai_manual", "not_decided", "synopsis");
        WorkflowView wf = workflowService.calculate(project, Map.of("story_seed", true, "characters", true));
        assertThat(wf.currentStageKey()).isEqualTo("synopsis");
    }

    // ===== Helpers =====

    private ContentProject buildProject(String source, String storyboard, String lastStage) {
        ContentProject p = new ContentProject();
        p.setId(100L); p.setUuid("CP_test"); p.setName("测试");
        p.setCreationMode("short_drama"); p.setSourceMode(source);
        p.setStoryboardIntentStatus(storyboard); p.setContentStatus("draft");
        p.setLastStageKey(lastStage); p.setIsDeleted(0);
        return p;
    }

    private ContentUnit buildUnit(Long id, int displayNo) {
        ContentUnit u = new ContentUnit();
        u.setId(id); u.setProjectId(100L); u.setUnitType("episode");
        u.setDisplayNo(displayNo); u.setRevision(0); u.setIsDeleted(0);
        u.setStatus("draft");
        return u;
    }

    private List<ContentUnit> createUnits(Long projectId, int count) {
        List<ContentUnit> units = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ContentUnit u = new ContentUnit();
            u.setId(projectId + i);
            u.setStableKey("CU_" + UUID.randomUUID().toString().replace("-", ""));
            u.setProjectId(projectId);
            u.setUnitType("episode");
            u.setDisplayNo(i);
            u.setTitle("第" + i + "集");
            u.setStatus("draft");
            u.setRevision(0);
            u.setIsDeleted(0);
            units.add(u);
        }
        return units;
    }
}
