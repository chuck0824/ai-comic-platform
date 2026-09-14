package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.config.ScriptStageTruthProperties;
import com.aicp.module.contentproject.domain.ScriptStageKey;
import com.aicp.module.contentproject.dto.StageCheckpointRequests.StageForkRequest;
import com.aicp.module.contentproject.dto.StageCheckpointRequests.StageTransitionRequest;
import com.aicp.module.contentproject.dto.StageCheckpointRequests.StalenessResolutionRequest;
import com.aicp.module.contentproject.dto.StageCheckpointViews.StageProjection;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentStageCheckpoint;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.entity.StoryboardHandoffSnapshot;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentStageCheckpointMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.StoryboardHandoffSnapshotMapper;
import com.aicp.module.contentproject.service.stage.StageGateRegistry;
import com.aicp.module.contentproject.service.stage.StageGateResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentStageOrchestratorTest {

    @Mock ScriptStageTruthProperties truthProperties;
    @Mock ProjectAccessService accessService;
    @Mock ContentStageCheckpointService checkpointService;
    @Mock ContentStageCheckpointMapper checkpointMapper;
    @Mock ContentProjectMapper projectMapper;
    @Mock ContentUnitMapper contentUnitMapper;
    @Mock ContentVersionMapper contentVersionMapper;
    @Mock StoryboardHandoffSnapshotMapper handoffSnapshotMapper;
    @Mock ContentGenerationJobService generationJobService;
    @Mock StageGateRegistry gateRegistry;
    @Mock OutboxService outboxService;

    ContentStageOrchestrator orchestrator;
    ObjectMapper objectMapper = new ObjectMapper();

    ContentProject project;
    Map<ScriptStageKey, ContentStageCheckpoint> checkpoints = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        orchestrator = new ContentStageOrchestrator(
                truthProperties,
                accessService,
                checkpointService,
                checkpointMapper,
                projectMapper,
                contentUnitMapper,
                contentVersionMapper,
                handoffSnapshotMapper,
                generationJobService,
                gateRegistry,
                outboxService,
                objectMapper);

        when(truthProperties.isStageTruthEnabled()).thenReturn(true);

        project = new ContentProject();
        project.setId(100L);
        project.setRevision(1);
        project.setLastStageKey("creation_settings");
        project.setCurrentParameterVersionId(55L);

        for (ScriptStageKey key : ScriptStageKey.ordered()) {
            ContentStageCheckpoint row = new ContentStageCheckpoint();
            row.setId(key.order() + 1L);
            row.setProjectId(100L);
            row.setStageKey(key.value());
            row.setState("NOT_STARTED");
            row.setRevision(0);
            checkpoints.put(key, row);
        }
        checkpoints.get(ScriptStageKey.CREATION_SETTINGS).setState("IN_PROGRESS");

        lenient().when(checkpointService.requireProject(100L)).thenReturn(project);
        lenient().when(checkpointService.requireCheckpoint(eq(100L), any(ScriptStageKey.class)))
                .thenAnswer(inv -> checkpoints.get(inv.getArgument(1)));
        lenient().when(checkpointService.listProjection(anyLong(), eq(100L)))
                .thenReturn(new StageProjection(100L, 2, List.of(), "novel_upload", true));
    }

    @Test
    void transition_success_completesSource_opensTarget_emitsStageCompleted() {
        when(gateRegistry.evaluate(eq(ScriptStageKey.CREATION_SETTINGS), any()))
                .thenReturn(StageGateResult.pass(Map.of("ok", true)));

        StageTransitionRequest request = new StageTransitionRequest(
                "creation_settings", "novel_upload", null,
                1, 0, 0, null, null, List.of());

        StageProjection projection = orchestrator.transition(7L, 100L, request);

        assertThat(projection.projectId()).isEqualTo(100L);
        assertThat(checkpoints.get(ScriptStageKey.CREATION_SETTINGS).getState()).isEqualTo("COMPLETED");
        assertThat(checkpoints.get(ScriptStageKey.NOVEL_UPLOAD).getState()).isEqualTo("IN_PROGRESS");
        assertThat(project.getLastStageKey()).isEqualTo("novel_upload");
        assertThat(project.getRevision()).isEqualTo(2);
        verify(outboxService).append(eq("StageCompleted"), eq(100L), eq(2), any());
        verify(outboxService, never()).append(eq("GateBlocked"), anyLong(), anyInt(), any());
    }

    @Test
    void transition_gateBlocked_setsBLOCKED_throwsWithDetails_emitsGateBlocked() {
        StageGateResult blocked = StageGateResult.of(
                List.of(StageGateResult.issue("GENRE_REQUIRED", "缺少题材")),
                List.of(),
                Map.of("hasPayload", true));
        when(gateRegistry.evaluate(eq(ScriptStageKey.CREATION_SETTINGS), any())).thenReturn(blocked);

        StageTransitionRequest request = new StageTransitionRequest(
                "creation_settings", "novel_upload", null,
                1, 0, 0, null, null, List.of());

        assertThatThrownBy(() -> orchestrator.transition(7L, 100L, request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(ErrorCode.STAGE_GATE_BLOCKED.getCode());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> details = (Map<String, Object>) biz.getDetails();
                    assertThat(details).containsKeys("blockers", "warnings", "evidence");
                    assertThat(details.get("blockers")).asList().isNotEmpty();
                });

        assertThat(checkpoints.get(ScriptStageKey.CREATION_SETTINGS).getState()).isEqualTo("BLOCKED");
        assertThat(checkpoints.get(ScriptStageKey.NOVEL_UPLOAD).getState()).isEqualTo("NOT_STARTED");
        verify(outboxService).append(eq("GateBlocked"), eq(100L), anyInt(), any());
        verify(outboxService, never()).append(eq("StageCompleted"), anyLong(), anyInt(), any());
    }

    @Test
    void transition_warningWithoutAck_throwsSTAGE_GATE_BLOCKED() {
        StageGateResult warned = StageGateResult.of(
                List.of(),
                List.of(StageGateResult.issue("DURATION_VARIANCE", "偏差 20%")),
                Map.of());
        when(gateRegistry.evaluate(eq(ScriptStageKey.CREATION_SETTINGS), any())).thenReturn(warned);

        StageTransitionRequest request = new StageTransitionRequest(
                "creation_settings", "novel_upload", null,
                1, 0, 0, null, null, List.of());

        assertThatThrownBy(() -> orchestrator.transition(7L, 100L, request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(ErrorCode.STAGE_GATE_BLOCKED.getCode());
                    assertThat(biz.getMessage()).contains("DURATION_VARIANCE");
                });

        assertThat(checkpoints.get(ScriptStageKey.CREATION_SETTINGS).getState()).isEqualTo("IN_PROGRESS");
        verify(checkpointMapper, never()).updateById(any());
        verify(outboxService, never()).append(eq("StageCompleted"), anyLong(), anyInt(), any());
    }

    @Test
    void fork_completedStage_marksDownstreamPossiblyStale_emitsForkEvents() {
        checkpoints.get(ScriptStageKey.ADAPTATION).setState("COMPLETED");
        checkpoints.get(ScriptStageKey.ADAPTATION).setAdoptedContentVersionId(11L);
        checkpoints.get(ScriptStageKey.ADAPTATION).setRevision(2);
        checkpoints.get(ScriptStageKey.STRUCTURED_SCRIPT).setState("COMPLETED");
        checkpoints.get(ScriptStageKey.SCRIPT_BODY).setState("COMPLETED");

        when(contentUnitMapper.selectOne(any())).thenReturn(null);

        StageForkRequest request = new StageForkRequest(2, 11L, 1);
        orchestrator.fork(7L, 100L, "adaptation", request);

        assertThat(checkpoints.get(ScriptStageKey.ADAPTATION).getState()).isEqualTo("IN_PROGRESS");
        assertThat(checkpoints.get(ScriptStageKey.STRUCTURED_SCRIPT).getState()).isEqualTo("POSSIBLY_STALE");
        assertThat(checkpoints.get(ScriptStageKey.SCRIPT_BODY).getState()).isEqualTo("POSSIBLY_STALE");
        assertThat(checkpoints.get(ScriptStageKey.STRUCTURED_SCRIPT).getStaleReasonJson())
                .contains("causeForkId")
                .contains("POSSIBLY_STALE");
        verify(outboxService).append(eq("StageForked"), eq(100L), anyInt(), any());
        verify(outboxService).append(eq("StageMarkedStale"), eq(100L), anyInt(), any());
    }

    @Test
    void resolveStaleness_discardFork_removesOnlyMatchingCause() throws Exception {
        ContentStageCheckpoint row = checkpoints.get(ScriptStageKey.STRUCTURED_SCRIPT);
        row.setState("POSSIBLY_STALE");
        row.setRevision(3);
        List<Map<String, Object>> reasons = new ArrayList<>();
        reasons.add(reason("fork-a", "OPEN", false));
        reasons.add(reason("fork-b", "OPEN", false));
        row.setStaleReasonJson(objectMapper.writeValueAsString(reasons));

        StalenessResolutionRequest request = new StalenessResolutionRequest(
                "DISCARD_FORK",
                3,
                Map.of("cause_fork_id", "fork-a"),
                "discard a");

        orchestrator.resolveStaleness(7L, 100L, "structured_script", request);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> remaining = objectMapper.readValue(
                row.getStaleReasonJson(), List.class);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).get("causeForkId")).isEqualTo("fork-b");
        assertThat(row.getState()).isEqualTo("POSSIBLY_STALE");
        verify(outboxService).append(eq("StalenessResolved"), eq(100L), anyInt(), any());
    }

    @Test
    void transition_completeHandoff_persistsStoryboardHandoffSnapshot() {
        checkpoints.get(ScriptStageKey.TEXT_STORYBOARD).setState("IN_PROGRESS");
        checkpoints.get(ScriptStageKey.TEXT_STORYBOARD).setRevision(1);

        when(gateRegistry.evaluate(eq(ScriptStageKey.TEXT_STORYBOARD), any()))
                .thenReturn(StageGateResult.pass(Map.of("ok", true)));

        ContentStageCheckpoint review = new ContentStageCheckpoint();
        review.setStageKey("review_revision");
        review.setAdoptedContentVersionId(77L);
        when(checkpointMapper.selectOne(any())).thenReturn(review);

        ContentVersion approved = new ContentVersion();
        approved.setId(77L);
        approved.setStatus("approved");
        when(contentVersionMapper.selectById(77L)).thenReturn(approved);

        ContentUnit storyboardUnit = new ContentUnit();
        storyboardUnit.setId(40L);
        storyboardUnit.setProjectId(100L);
        storyboardUnit.setUnitType("text_storyboard");
        storyboardUnit.setIsDeleted(0);
        when(contentUnitMapper.selectOne(any())).thenReturn(storyboardUnit);

        ContentVersion draft = new ContentVersion();
        draft.setContentJson("""
                {"shots":[{"id":1},{"id":2}],"continuity":{"passed":true},"contentHash":"h1"}
                """);
        draft.setContentHash("h1");
        draft.setPlainText("");
        when(contentVersionMapper.selectOne(any())).thenReturn(draft);
        when(contentVersionMapper.selectList(any())).thenReturn(List.of());
        doAnswer(inv -> {
            ContentVersion version = inv.getArgument(0);
            version.setId(88L);
            return 1;
        }).when(contentVersionMapper).insert(any());

        doAnswer(inv -> {
            StoryboardHandoffSnapshot snapshot = inv.getArgument(0);
            snapshot.setId(501L);
            return 1;
        }).when(handoffSnapshotMapper).insert(any());

        StageTransitionRequest request = new StageTransitionRequest(
                "text_storyboard", null, "COMPLETE_HANDOFF",
                1, 1, null, null, null, List.of());

        orchestrator.transition(7L, 100L, request);

        ContentStageCheckpoint source = checkpoints.get(ScriptStageKey.TEXT_STORYBOARD);
        assertThat(source.getState()).isEqualTo("COMPLETED");
        assertThat(source.getPrimaryArtifactType()).isEqualTo("STORYBOARD_HANDOFF");
        assertThat(source.getPrimaryArtifactId()).isEqualTo(501L);

        ArgumentCaptor<StoryboardHandoffSnapshot> snapCaptor =
                ArgumentCaptor.forClass(StoryboardHandoffSnapshot.class);
        verify(handoffSnapshotMapper).insert(snapCaptor.capture());
        assertThat(snapCaptor.getValue().getReviewedScriptBodyVersionId()).isEqualTo(77L);
        assertThat(snapCaptor.getValue().getContinuityCheckResult()).isEqualToIgnoringCase("PASS");
        verify(outboxService, atLeastOnce()).append(eq("StageCompleted"), eq(100L), anyInt(), any());
        verify(outboxService).append(eq("StoryboardHandoffCaptured"), eq(100L), anyInt(), any());
    }

    @Test
    void transition_checkpointRevisionMismatch_throwsSTAGE_REVISION_CONFLICT() {
        checkpoints.get(ScriptStageKey.CREATION_SETTINGS).setRevision(5);

        StageTransitionRequest request = new StageTransitionRequest(
                "creation_settings", "novel_upload", null,
                1, 0, 0, null, null, List.of());

        assertThatThrownBy(() -> orchestrator.transition(7L, 100L, request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(ErrorCode.STAGE_REVISION_CONFLICT.getCode()));

        verify(gateRegistry, never()).evaluate(any(), any());
        verify(outboxService, never()).append(any(), anyLong(), anyInt(), any());
    }

    private static Map<String, Object> reason(String causeForkId, String status, boolean wasLocked) {
        Map<String, Object> reason = new LinkedHashMap<>();
        reason.put("causeForkId", causeForkId);
        reason.put("status", status);
        reason.put("wasLocked", wasLocked);
        reason.put("impactLevel", "POSSIBLY_STALE");
        return reason;
    }
}
