package com.aicp.module.contentproject;

import com.aicp.module.contentproject.domain.ContentProjectEnums.*;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("M1 短剧纵向切片集成测试")
class ContentProjectM1IntegrationTest {

    @Mock ContentUnitMapper unitMapper;
    @Mock ContentVersionMapper versionMapper;
    @Mock ContentProjectMapper projectMapper;
    @Mock ProjectParameterVersionMapper parameterVersionMapper;
    @Mock ObjectMapper objectMapper;

    private ProjectWorkflowService workflowService;
    private ContentUnitService unitService;

    @BeforeEach
    void setUp() throws Exception {
        workflowService = new ProjectWorkflowService(projectMapper, parameterVersionMapper, objectMapper);
        unitService = new ContentUnitService(unitMapper, versionMapper);
    }

    @Test
    @DisplayName("路径A：创建→各阶段→destination→跳过storyboard→进度100%")
    void pathA_skipStoryboardCompleteContent() {
        ContentProject project = buildProject("ai_manual", "not_decided", "story_seed");

        WorkflowView wf1 = workflowService.calculate(project, Map.of());
        assertThat(wf1.currentStageKey()).isEqualTo("story_seed");

        project.setLastStageKey("characters");
        WorkflowView wf2 = workflowService.calculate(project, Map.of("story_seed", true));
        assertThat(wf2.currentStageKey()).isEqualTo("characters");

        project.setLastStageKey("destination");
        Map<String, Boolean> allDone = Map.of(
                "story_seed", true, "characters", true, "synopsis", true,
                "outline", true, "content", true, "review", true);
        WorkflowView wf3 = workflowService.calculate(project, allDone);
        assertThat(wf3.currentStageKey()).isEqualTo("destination");

        project.setStoryboardIntentStatus("skipped");
        WorkflowView wf4 = workflowService.calculate(project, allDone);
        assertThat(wf4.progress()).isEqualTo(100);
        assertThat(wf4.stages().stream()
                .filter(s -> "storyboard".equals(s.key()))
                .findFirst().orElseThrow().status()).isEqualTo("skipped");
    }

    @Test
    @DisplayName("路径B：destination→选择storyboard→pending状态")
    void pathB_chooseStoryboardEnterCanvas() {
        ContentProject project = buildProject("ai_manual", "requested", "destination");

        WorkflowView wf = workflowService.calculate(project, Map.of(
                "story_seed", true, "characters", true, "synopsis", true,
                "outline", true, "content", true, "review", true));

        StageView sbStage = wf.stages().stream()
                .filter(s -> "storyboard".equals(s.key()))
                .findFirst().orElseThrow();
        assertThat(sbStage.status()).isEqualTo("pending");
        assertThat(sbStage.required()).isFalse();
    }

    @Test
    @DisplayName("上传项目：跳过story_seed，从import_review开始")
    void uploadedProjectStartsAtImportReview() {
        ContentProject project = buildProject("uploaded", "not_decided", "story_seed");

        WorkflowView wf = workflowService.calculate(project, Map.of("story_seed", true));
        assertThat(wf.currentStageKey()).isEqualTo("import_review");

        StageView seedStage = wf.stages().stream()
                .filter(s -> "story_seed".equals(s.key()))
                .findFirst().orElseThrow();
        assertThat(seedStage.status()).isEqualTo("completed");
    }

    @Test
    @DisplayName("两个内容单元各自独立，草稿互不影响")
    void contentUnitIndependence() throws Exception {
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"blocks\":[\"第一集\"]}");

        ContentUnit unit1 = buildUnit(1L, 1);
        ContentUnit unit2 = buildUnit(2L, 2);

        when(unitMapper.selectById(1L)).thenReturn(unit1);
        when(unitMapper.selectById(2L)).thenReturn(unit2);
        when(versionMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> 1).when(versionMapper).insert(any(ContentVersion.class));

        DraftView draft1 = unitService.saveDraft(7L, 1L,
                new SaveDraftRequest(0, "{\"blocks\":[\"第一集\"]}", "第一集"));
        DraftView draft2 = unitService.getDraft(7L, 2L);

        // unit2 has no draft — plainText should be empty string
        assertThat(draft2.plainText()).isEmpty();
        // unit1 revision incremented by saveDraft
        assertThat(draft1.revision()).isGreaterThan(0);
    }

    @Test
    @DisplayName("角色权限矩阵：Owner全权限，Viewer只读")
    void rolePermissionMatrix() {
        assertThat(Role.OWNER.allows(Action.DELETE_PROJECT)).isTrue();
        assertThat(Role.OWNER.allows(Action.MANAGE_MEMBERS)).isTrue();
        assertThat(Role.EDITOR.allows(Action.EDIT_CONTENT)).isTrue();
        assertThat(Role.EDITOR.allows(Action.DELETE_PROJECT)).isFalse();
        assertThat(Role.REVIEWER.allows(Action.REVIEW)).isTrue();
        assertThat(Role.REVIEWER.allows(Action.EDIT_CONTENT)).isFalse();
        assertThat(Role.PRODUCER.allows(Action.PRODUCE)).isTrue();
        assertThat(Role.VIEWER.allows(Action.VIEW)).isTrue();
        assertThat(Role.VIEWER.allows(Action.EDIT_CONTENT)).isFalse();
    }

    private ContentProject buildProject(String sourceMode, String storyboardIntent, String lastStage) {
        ContentProject p = new ContentProject();
        p.setId(100L); p.setUuid("CP_test"); p.setName("测试短剧");
        p.setCreationMode("short_drama"); p.setSourceMode(sourceMode);
        p.setStoryboardIntentStatus(storyboardIntent); p.setContentStatus("draft");
        p.setLastStageKey(lastStage); p.setIsDeleted(0);
        return p;
    }

    private ContentUnit buildUnit(Long id, int displayNo) {
        ContentUnit u = new ContentUnit();
        u.setId(id); u.setProjectId(100L); u.setUnitType("episode");
        u.setDisplayNo(displayNo); u.setRevision(0); u.setIsDeleted(0);
        return u;
    }
}
