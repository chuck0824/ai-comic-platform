package com.aicp.module.contentproject.service.stage;

import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ProjectParameterVersion;
import com.aicp.module.contentproject.entity.UploadFile;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.ProjectParameterVersionMapper;
import com.aicp.module.contentproject.mapper.StoryboardHandoffSnapshotMapper;
import com.aicp.module.contentproject.mapper.UploadFileMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StageGateEvaluatorsTest {

    @Mock ProjectParameterVersionMapper parameterVersionMapper;
    @Mock ContentUnitMapper unitMapper;
    @Mock ContentVersionMapper versionMapper;
    @Mock UploadFileMapper uploadFileMapper;
    @Mock StoryboardHandoffSnapshotMapper handoffSnapshotMapper;

    StageGateSupport support;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        support = new StageGateSupport(
                parameterVersionMapper, unitMapper, versionMapper,
                uploadFileMapper, handoffSnapshotMapper, objectMapper);
    }

    @Test
    void creationSettingsBlocksMissingFields() {
        ContentProject project = project(11L, 99L);
        ProjectParameterVersion pv = new ProjectParameterVersion();
        pv.setId(99L);
        pv.setPayloadJson("{\"kind\":\"script_workbench_settings\",\"creationType\":\"drama\"}");
        when(parameterVersionMapper.selectById(99L)).thenReturn(pv);

        StageGateResult result = new CreationSettingsGateEvaluator(support).evaluate(project);
        assertThat(result.blocked()).isTrue();
        assertThat(result.blockers()).extracting(m -> m.get("code"))
                .contains("GENRE_REQUIRED", "EPISODE_COUNT_REQUIRED", "MODEL_REQUIRED");
    }

    @Test
    void creationSettingsPassesCompletePayload() throws Exception {
        ContentProject project = project(11L, 99L);
        Map<String, Object> payload = Map.of(
                "creationType", "drama",
                "genre", "都市",
                "episodeCount", 12,
                "episodeDuration", 90,
                "outputFormat", "9:16",
                "tone", "紧张",
                "model", Map.of("id", "m1", "demo", true),
                "estimatedPoints", 0
        );
        ProjectParameterVersion pv = new ProjectParameterVersion();
        pv.setId(99L);
        pv.setPayloadJson(objectMapper.writeValueAsString(payload));
        when(parameterVersionMapper.selectById(99L)).thenReturn(pv);

        StageGateResult result = new CreationSettingsGateEvaluator(support).evaluate(project);
        assertThat(result.blocked()).isFalse();
    }

    @Test
    void novelUploadRequiresParseCompleted() {
        ContentProject project = project(3L, null);
        ContentUnit unit = new ContentUnit();
        unit.setId(7L);
        unit.setProjectId(3L);
        unit.setUnitType("novel_upload");
        when(unitMapper.selectOne(any())).thenReturn(unit);
        when(versionMapper.selectOne(any())).thenReturn(null);

        StageGateResult empty = new NovelUploadGateEvaluator(support).evaluate(project);
        assertThat(empty.blocked()).isTrue();
    }

    @Test
    void novelUploadAcceptsCompletedUpload() throws Exception {
        ContentProject project = project(3L, null);
        ContentUnit unit = new ContentUnit();
        unit.setId(7L);
        unit.setProjectId(3L);
        unit.setUnitType("novel_upload");
        when(unitMapper.selectOne(any())).thenReturn(unit);

        var draft = new com.aicp.module.contentproject.entity.ContentVersion();
        draft.setContentJson(objectMapper.writeValueAsString(Map.of(
                "source", "file", "uploadId", 55)));
        draft.setStatus("draft");
        when(versionMapper.selectOne(any())).thenReturn(draft);

        UploadFile upload = new UploadFile();
        upload.setId(55L);
        upload.setParseStatus("completed");
        upload.setParsedText("novel text");
        when(uploadFileMapper.selectById(55L)).thenReturn(upload);

        StageGateResult result = new NovelUploadGateEvaluator(support).evaluate(project);
        assertThat(result.blocked()).isFalse();
    }

    @Test
    void scriptBodyRequiresScenesAndBindings() throws Exception {
        ContentProject project = project(8L, null);
        ContentUnit unit = new ContentUnit();
        unit.setId(1L);
        unit.setRevision(2);
        when(unitMapper.selectOne(any())).thenReturn(unit);

        var draft = new com.aicp.module.contentproject.entity.ContentVersion();
        draft.setContentJson(objectMapper.writeValueAsString(Map.of(
                "episodes", List.of(Map.of(
                        "id", "EP-001",
                        "scenes", List.of(Map.of(
                                "id", "S1",
                                "blocks", List.of(Map.of("id", "B1", "text", "动作")),
                                "bindingState", "UNBOUND"
                        ))
                ))
        )));
        when(versionMapper.selectOne(any())).thenReturn(draft);

        StageGateResult result = new ScriptBodyGateEvaluator(support).evaluate(project);
        assertThat(result.blocked()).isTrue();
        assertThat(result.blockers()).extracting(m -> m.get("code"))
                .contains("SCENE_ASSET_BINDING_REQUIRED");
    }

    @Test
    void adaptationRequiresConfirmation() throws Exception {
        ContentProject project = project(5L, null);
        when(unitMapper.selectOne(any())).thenReturn(new ContentUnit());
        var draft = new com.aicp.module.contentproject.entity.ContentVersion();
        draft.setContentJson(objectMapper.writeValueAsString(Map.of(
                "selectedHookId", "h1",
                "hooks", List.of(Map.of("id", "h1")),
                "rules", List.of(Map.of("title", "r1")),
                "confirmed", false
        )));
        when(versionMapper.selectOne(any())).thenReturn(draft);

        StageGateResult result = new AdaptationGateEvaluator(support).evaluate(project);
        assertThat(result.blocked()).isTrue();
        assertThat(result.blockers()).extracting(m -> m.get("code"))
                .contains("ADAPTATION_NOT_CONFIRMED");
    }

    @Test
    void structuredScriptWarnsOnDurationVarianceAndBlocksUntilConfirmed() throws Exception {
        ContentProject project = project(6L, null);
        ContentUnit unit = new ContentUnit();
        unit.setId(2L);
        when(unitMapper.selectOne(any())).thenReturn(unit);

        var draft = new com.aicp.module.contentproject.entity.ContentVersion();
        draft.setContentJson(objectMapper.writeValueAsString(Map.of(
                "episodes", List.of(Map.of(
                        "id", "EP-001",
                        "beats", List.of(Map.of("id", "B1"))
                )),
                "targetDuration", 100,
                "actualDuration", 130
        )));
        when(versionMapper.selectOne(any())).thenReturn(draft);

        StageGateResult blocked = new StructuredScriptGateEvaluator(support).evaluate(project);
        assertThat(blocked.blocked()).isTrue();
        assertThat(blocked.warnings()).extracting(m -> m.get("code")).contains("DURATION_VARIANCE");
        assertThat(blocked.blockers()).extracting(m -> m.get("code"))
                .contains("DURATION_VARIANCE_UNCONFIRMED");

        draft.setContentJson(objectMapper.writeValueAsString(Map.of(
                "episodes", List.of(Map.of(
                        "id", "EP-001",
                        "beats", List.of(Map.of("id", "B1"))
                )),
                "targetDuration", 100,
                "actualDuration", 130,
                "durationVarianceConfirmed", true
        )));
        when(versionMapper.selectOne(any())).thenReturn(draft);

        StageGateResult warned = new StructuredScriptGateEvaluator(support).evaluate(project);
        assertThat(warned.blocked()).isFalse();
        assertThat(warned.warnings()).extracting(m -> m.get("code")).contains("DURATION_VARIANCE");
    }

    @Test
    void textStoryboardRequiresContinuityPass() throws Exception {
        ContentProject project = project(9L, null);
        ContentUnit storyboardUnit = new ContentUnit();
        storyboardUnit.setId(40L);
        storyboardUnit.setUnitType("text_storyboard");
        when(unitMapper.selectOne(any())).thenReturn(storyboardUnit);

        var draft = new com.aicp.module.contentproject.entity.ContentVersion();
        draft.setContentJson(objectMapper.writeValueAsString(Map.of(
                "shots", List.of(Map.of("id", 1)),
                "contentVersionId", 100,
                "continuity", Map.of("passed", false, "status", "FAILED"),
                "archived", true
        )));
        when(versionMapper.selectOne(any())).thenReturn(draft);

        StageGateResult result = new TextStoryboardGateEvaluator(support).evaluate(project);
        assertThat(result.blocked()).isTrue();
        assertThat(result.blockers()).extracting(m -> m.get("code"))
                .contains("CONTINUITY_NOT_PASS");
    }

    @Test
    void novelAnalysisRequiresEntityQuad() throws Exception {
        ContentProject project = project(8L, null);
        ContentUnit unit = new ContentUnit();
        unit.setId(81L);
        unit.setUnitType("novel_analysis");
        when(unitMapper.selectOne(any())).thenReturn(unit);

        var draft = new com.aicp.module.contentproject.entity.ContentVersion();
        draft.setContentJson(objectMapper.writeValueAsString(Map.of(
                "characters", List.of(),
                "locations", List.of(Map.of("id", "L1")),
                "events", List.of(Map.of("id", "E1")),
                "worldview", "都市异能"
        )));
        when(versionMapper.selectOne(any())).thenReturn(draft);

        StageGateResult result = new NovelAnalysisGateEvaluator(support).evaluate(project);
        assertThat(result.blocked()).isTrue();
        assertThat(result.blockers()).extracting(m -> m.get("code"))
                .contains("MAIN_CHARACTERS_REQUIRED");
    }

    @Test
    void reviewRevisionRequiresApproval() throws Exception {
        ContentProject project = project(7L, null);
        ContentUnit reviewUnit = new ContentUnit();
        reviewUnit.setId(71L);
        reviewUnit.setUnitType("review_revision");
        ContentUnit scriptUnit = new ContentUnit();
        scriptUnit.setId(72L);
        scriptUnit.setUnitType("script_body");
        scriptUnit.setCurrentVersionId(720L);
        when(unitMapper.selectOne(any())).thenReturn(reviewUnit, scriptUnit);

        var reviewDraft = new com.aicp.module.contentproject.entity.ContentVersion();
        reviewDraft.setContentJson(objectMapper.writeValueAsString(Map.of(
                "issues", List.of(Map.of("severity", "BLOCKER", "status", "OPEN", "title", "钩子弱"))
        )));
        var scriptVersion = new com.aicp.module.contentproject.entity.ContentVersion();
        scriptVersion.setId(720L);
        scriptVersion.setStatus("draft");
        when(versionMapper.selectOne(any())).thenReturn(reviewDraft);
        when(versionMapper.selectById(720L)).thenReturn(scriptVersion);

        StageGateResult result = new ReviewRevisionGateEvaluator(support).evaluate(project);
        assertThat(result.blocked()).isTrue();
        assertThat(result.blockers()).extracting(m -> m.get("code"))
                .contains("REVIEW_BLOCKER_OPEN", "REVIEW_APPROVAL_REQUIRED");
    }

    private ContentProject project(Long id, Long parameterVersionId) {
        ContentProject project = new ContentProject();
        project.setId(id);
        project.setCurrentParameterVersionId(parameterVersionId);
        return project;
    }
}
