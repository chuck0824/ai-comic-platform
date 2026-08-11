package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.common.exception.BizException;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.dto.ContentProjectRequests.GenerationJobRequest;
import com.aicp.module.contentproject.dto.ContentProjectViews.ContextSnapshot;
import com.aicp.module.contentproject.dto.ContentProjectViews.GenerationJobView;
import com.aicp.module.contentproject.entity.ContentGenerationJob;
import com.aicp.module.contentproject.entity.GenerationContextSnapshot;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentGenerationJobMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.GenerationContextSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentGenerationJobLifecycleServiceTest {

    @Mock ContentGenerationJobMapper jobMapper;
    @Mock GenerationContextSnapshotMapper contextMapper;
    @Mock ContextAssembler contextAssembler;
    @Mock ContentGenerationExecutor executor;
    @Mock ContentVersionMapper versionMapper;
    @Mock ContentUnitMapper unitMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock AiRouter aiRouter;
    @Mock SchemaValidationService schemaValidation;
    @Mock ProjectAccessService projectAccessService;

    private ContentGenerationJobService service;

    @BeforeEach
    void setUp() {
        service = new ContentGenerationJobService(jobMapper, contextMapper, contextAssembler, executor,
                objectMapper, versionMapper, unitMapper, projectAccessService);
        lenient().when(unitMapper.selectById(17L)).thenReturn(unit(17L, 99L));
    }

    @Test
    void completedCandidateIsAcceptedAndDiscardedIdempotently() {
        when(versionMapper.update(isNull(), any())).thenReturn(1);
        ContentGenerationJob acceptedJob = job(71L, "completed");
        ContentVersion acceptedCandidate = version(171L, "candidate");
        ContentUnit acceptedUnit = unit(17L, 99L);
        when(jobMapper.selectById(71L)).thenReturn(acceptedJob);
        when(versionMapper.selectOne(any())).thenReturn(acceptedCandidate);
        when(unitMapper.selectById(17L)).thenReturn(acceptedUnit);

        when(unitMapper.update(isNull(), any())).thenReturn(1);
        GenerationJobView accepted = service.acceptJob(501L, 71L);

        assertThat(accepted.resultVersionId()).isEqualTo(171L);
        assertThat(accepted.artifactRef()).isEqualTo("/content-units/17/versions/171");
        assertThat(accepted.resultDisposition()).isEqualTo("accepted");
        assertThat(acceptedUnit.getCurrentVersionId()).isEqualTo(171L);
        assertThat(acceptedCandidate.getStatus()).isEqualTo("accepted");
        verify(unitMapper).update(isNull(), any());
        verify(versionMapper).update(isNull(), any());

        service.acceptJob(501L, 71L);
        verify(unitMapper, times(1)).update(isNull(), any());
        verify(versionMapper, times(1)).update(isNull(), any());

        ContentGenerationJob discardedJob = job(72L, "completed");
        discardedJob.setTargetId(18L);
        ContentVersion discardedCandidate = version(172L, "candidate");
        discardedCandidate.setContentUnitId(18L);
        ContentUnit untouchedUnit = unit(18L, 88L);
        when(jobMapper.selectById(72L)).thenReturn(discardedJob);
        when(versionMapper.selectOne(any())).thenReturn(discardedCandidate);
        when(unitMapper.selectById(18L)).thenReturn(untouchedUnit);

        GenerationJobView discarded = service.discardJob(501L, 72L);
        assertThat(discarded.resultDisposition()).isEqualTo("discarded");
        assertThat(untouchedUnit.getCurrentVersionId()).isEqualTo(88L);
        verify(unitMapper, never()).updateById(untouchedUnit);
        verify(versionMapper, times(2)).update(isNull(), any());

        service.discardJob(501L, 72L);
        verify(versionMapper, times(2)).update(isNull(), any());
        verify(versionMapper, never()).updateById(any(ContentVersion.class));
    }

    @Test
    void createJobCapturesTheTargetUnitBaselineInBothSnapshots() throws Exception {
        ContentUnit target = unit(17L, 99L);
        target.setRevision(3);
        when(unitMapper.selectById(17L)).thenReturn(target);
        when(contextAssembler.assemble(eq(9L), any())).thenReturn(new ContextSnapshot(
                Map.of(), null, null, List.of(), null, null, "{}", "original-hash"));
        doAnswer(invocation -> { ((ContentGenerationJob) invocation.getArgument(0)).setId(90L); return 1; })
                .when(jobMapper).insert(any(ContentGenerationJob.class));

        service.createJob(501L, 9L,
                new GenerationJobRequest("script_body_generate", "content_unit", 17L, Map.of(), null, "v1"),
                "baseline-key");

        ArgumentCaptor<ContentGenerationJob> job = ArgumentCaptor.forClass(ContentGenerationJob.class);
        ArgumentCaptor<GenerationContextSnapshot> persisted = ArgumentCaptor.forClass(GenerationContextSnapshot.class);
        verify(jobMapper).insert(job.capture());
        verify(contextMapper).insert(persisted.capture());
        assertThat(objectMapper.readTree(job.getValue().getInputSnapshotJson()).at("/_generation_target/revision").asInt()).isEqualTo(3);
        assertThat(objectMapper.readTree(job.getValue().getInputSnapshotJson()).at("/_generation_target/current_version_id").asLong()).isEqualTo(99L);
        assertThat(persisted.getValue().getPayloadJson()).isEqualTo(job.getValue().getInputSnapshotJson());
        assertThat(persisted.getValue().getPayloadHash()).isEqualTo(job.getValue().getInputSnapshotHash());
        verify(executor).execute(90L);
    }

    @Test
    void acceptRejectsNonCompletedOrDiscardedCandidates() {
        when(jobMapper.selectById(73L)).thenReturn(job(73L, "processing"));
        assertThatThrownBy(() -> service.acceptJob(501L, 73L)).isInstanceOf(BizException.class);

        when(jobMapper.selectById(74L)).thenReturn(job(74L, "completed"));
        when(versionMapper.selectOne(any())).thenReturn(version(174L, "discarded"));
        assertThatThrownBy(() -> service.acceptJob(501L, 74L)).isInstanceOf(BizException.class);
    }

    @Test
    void viewExposesActualCreditsFailureAndCandidateReference() {
        ContentGenerationJob failed = job(75L, "failed");
        failed.setEstimatedCredits(20);
        failed.setActualCredits(7);
        failed.setErrorCode("SCHEMA_VALIDATION_FAILED");
        when(jobMapper.selectById(75L)).thenReturn(failed);

        GenerationJobView failureView = service.getJob(501L, 75L);
        assertThat(failureView.actualCredits()).isEqualTo(7);
        assertThat(failureView.errorCode()).isEqualTo("SCHEMA_VALIDATION_FAILED");
        assertThat(failureView.errorMessage()).contains("结构");
        assertThat(failureView.resultVersionId()).isNull();
        assertThat(failureView.artifactRef()).isNull();
        verify(versionMapper, never()).selectOne(any());

        ContentGenerationJob completed = job(76L, "completed");
        when(jobMapper.selectById(76L)).thenReturn(completed);
        when(versionMapper.selectOne(any())).thenReturn(version(176L, "candidate"));
        GenerationJobView completedView = service.getJob(501L, 76L);
        assertThat(completedView.resultVersionId()).isEqualTo(176L);
        assertThat(completedView.artifactRef()).isEqualTo("/content-units/17/versions/176");
        assertThat(completedView.resultDisposition()).isEqualTo("candidate");
    }

    @Test
    void processingCancellationIsIdempotentAndExecutorDoesNotPersistCandidate() throws Exception {
        ContentGenerationJob processing = job(77L, "processing");
        when(jobMapper.selectById(77L)).thenReturn(processing);
        when(jobMapper.update(isNull(), any())).thenAnswer(invocation -> {
            processing.setStatus("cancelled");
            return 1;
        });

        service.cancelJob(501L, 77L);
        service.cancelJob(501L, 77L);
        assertThat(processing.getStatus()).isEqualTo("cancelled");

        ContentGenerationJob executing = job(78L, "pending");
        executing.setInputSnapshotJson("{}");
        when(jobMapper.selectById(78L)).thenReturn(executing);
        when(jobMapper.update(isNull(), any())).thenReturn(1);
        when(aiRouter.chatCompletion(any())).thenAnswer(invocation -> {
            executing.setStatus("cancelled");
            return Map.of("content", "不应保存的候选内容");
        });
        ContentGenerationExecutor realExecutor = new ContentGenerationExecutor(
                jobMapper, versionMapper, unitMapper, aiRouter, new ObjectMapper(), schemaValidation);

        realExecutor.execute(78L);

        verify(versionMapper, never()).insert(any(ContentVersion.class));
        verify(unitMapper, never()).updateById(any(ContentUnit.class));
        assertThat(executing.getStatus()).isEqualTo("cancelled");
    }

    @Test
    void executorCreatesCandidateWithoutChangingCurrentUnit() {
        ContentGenerationJob executing = job(79L, "pending");
        executing.setInputSnapshotJson("{}");
        ContentUnit unit = unit(17L, 99L);
        when(jobMapper.selectById(79L)).thenReturn(executing);
        when(jobMapper.update(isNull(), any())).thenReturn(1);
        when(aiRouter.chatCompletion(any())).thenReturn(Map.of("content", "新生成的候选内容"));
        when(unitMapper.selectById(17L)).thenReturn(unit);
        when(versionMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            ContentVersion candidate = invocation.getArgument(0);
            candidate.setId(179L);
            return 1;
        }).when(versionMapper).insert(any(ContentVersion.class));
        ContentGenerationExecutor realExecutor = new ContentGenerationExecutor(
                jobMapper, versionMapper, unitMapper, aiRouter, new ObjectMapper(), schemaValidation);

        realExecutor.execute(79L);

        ArgumentCaptor<ContentVersion> candidate = ArgumentCaptor.forClass(ContentVersion.class);
        verify(versionMapper).insert(candidate.capture());
        assertThat(candidate.getValue().getStatus()).isEqualTo("candidate");
        assertThat(candidate.getValue().getGenerationJobId()).isEqualTo(79L);
        assertThat(unit.getCurrentVersionId()).isEqualTo(99L);
        verify(unitMapper, never()).updateById(any(ContentUnit.class));
    }

    @Test
    void jobAccessRequiresProjectPermissionAndMatchingTargetUnit() {
        ContentGenerationJob job = job(80L, "completed");
        when(jobMapper.selectById(80L)).thenReturn(job);
        doThrow(new BizException(com.aicp.common.exception.ErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).require(9L, 999L, Action.VIEW);
        assertThatThrownBy(() -> service.getJob(999L, 80L)).isInstanceOf(BizException.class);

        ContentUnit foreignUnit = unit(17L, null);
        foreignUnit.setProjectId(10L);
        when(unitMapper.selectById(17L)).thenReturn(foreignUnit);
        assertThatThrownBy(() -> service.acceptJob(501L, 80L)).isInstanceOf(BizException.class);
        verify(versionMapper, never()).update(isNull(), any());
    }

    @Test
    void onlyOneCandidateCanWinTheSameUnitRevision() {
        ContentGenerationJob firstJob = job(81L, "completed");
        ContentGenerationJob secondJob = job(82L, "completed");
        ContentVersion first = version(181L, "candidate");
        ContentVersion second = version(182L, "candidate");
        ContentUnit initial = unit(17L, 99L);
        when(jobMapper.selectById(81L)).thenReturn(firstJob);
        when(jobMapper.selectById(82L)).thenReturn(secondJob);
        when(versionMapper.selectOne(any())).thenReturn(first, first, second);
        when(unitMapper.selectById(17L)).thenReturn(initial, initial);
        when(unitMapper.update(isNull(), any())).thenReturn(1, 0);
        when(versionMapper.update(isNull(), any())).thenReturn(1);

        assertThat(service.acceptJob(501L, 81L).resultDisposition()).isEqualTo("accepted");
        assertThatThrownBy(() -> service.acceptJob(501L, 82L)).isInstanceOf(BizException.class);

        assertThat(first.getStatus()).isEqualTo("accepted");
        assertThat(second.getStatus()).isEqualTo("candidate");
        verify(versionMapper, times(1)).update(isNull(), any());
    }

    @Test
    void executorRemovesCandidateWhenCompletionLosesCancellationRace() {
        ContentGenerationJob executing = job(83L, "pending");
        executing.setInputSnapshotJson("{}");
        when(jobMapper.selectById(83L)).thenReturn(executing);
        when(jobMapper.update(isNull(), any())).thenReturn(1, 0);
        when(aiRouter.chatCompletion(any())).thenReturn(Map.of("content", "取消竞态候选"));
        when(unitMapper.selectById(17L)).thenReturn(unit(17L, 99L));
        when(versionMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> { ((ContentVersion) invocation.getArgument(0)).setId(183L); return 1; })
                .when(versionMapper).insert(any(ContentVersion.class));
        ContentGenerationExecutor realExecutor = new ContentGenerationExecutor(
                jobMapper, versionMapper, unitMapper, aiRouter, new ObjectMapper(), schemaValidation);

        realExecutor.execute(83L);

        verify(versionMapper).deleteById(183L);
    }

    @Test
    void executorRemovesCandidateWhenPostInsertCompletionFails() {
        ContentGenerationJob executing = job(84L, "pending");
        executing.setInputSnapshotJson("{}");
        when(jobMapper.selectById(84L)).thenReturn(executing);
        when(jobMapper.update(isNull(), any())).thenReturn(1).thenThrow(new RuntimeException("completion write failed")).thenReturn(1);
        when(aiRouter.chatCompletion(any())).thenReturn(Map.of("content", "写入失败候选"));
        when(unitMapper.selectById(17L)).thenReturn(unit(17L, 99L));
        when(versionMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> { ((ContentVersion) invocation.getArgument(0)).setId(184L); return 1; })
                .when(versionMapper).insert(any(ContentVersion.class));
        ContentGenerationExecutor realExecutor = new ContentGenerationExecutor(
                jobMapper, versionMapper, unitMapper, aiRouter, new ObjectMapper(), schemaValidation);

        realExecutor.execute(84L);

        verify(versionMapper).deleteById(184L);
        verify(jobMapper, times(3)).update(isNull(), any());
    }

    private ContentGenerationJob job(Long id, String status) {
        ContentGenerationJob job = new ContentGenerationJob();
        job.setId(id);
        job.setUuid("job-" + id);
        job.setProjectId(9L);
        job.setJobType("script_body_generate");
        job.setTargetType("content_unit");
        job.setTargetId(17L);
        job.setStatus(status);
        job.setInputSnapshotJson("{\"_generation_target\":{\"unit_id\":17,\"revision\":0,\"current_version_id\":99}}");
        job.setEstimatedCredits(20);
        job.setActualCredits(0);
        job.setCreatedBy(501L);
        return job;
    }

    private ContentVersion version(Long id, String status) {
        ContentVersion version = new ContentVersion();
        version.setId(id);
        version.setProjectId(9L);
        version.setContentUnitId(17L);
        version.setStatus(status);
        version.setGenerationJobId(id - 100L);
        return version;
    }

    private ContentUnit unit(Long id, Long currentVersionId) {
        ContentUnit unit = new ContentUnit();
        unit.setId(id);
        unit.setProjectId(9L);
        unit.setCurrentVersionId(currentVersionId);
        unit.setRevision(0);
        return unit;
    }
}
