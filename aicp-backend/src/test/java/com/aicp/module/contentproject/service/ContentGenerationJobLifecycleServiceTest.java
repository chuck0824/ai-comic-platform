package com.aicp.module.contentproject.service;

import com.aicp.common.ai.AiRouter;
import com.aicp.common.exception.BizException;
import com.aicp.module.contentproject.dto.ContentProjectViews.GenerationJobView;
import com.aicp.module.contentproject.entity.ContentGenerationJob;
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
    @Mock ObjectMapper objectMapper;
    @Mock AiRouter aiRouter;
    @Mock SchemaValidationService schemaValidation;

    private ContentGenerationJobService service;

    @BeforeEach
    void setUp() {
        service = new ContentGenerationJobService(jobMapper, contextMapper, contextAssembler, executor,
                objectMapper, versionMapper, unitMapper);
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

        GenerationJobView accepted = service.acceptJob(71L);

        assertThat(accepted.resultVersionId()).isEqualTo(171L);
        assertThat(accepted.artifactRef()).isEqualTo("/content-units/17/versions/171");
        assertThat(accepted.resultDisposition()).isEqualTo("accepted");
        assertThat(acceptedUnit.getCurrentVersionId()).isEqualTo(171L);
        assertThat(acceptedCandidate.getStatus()).isEqualTo("accepted");
        verify(unitMapper).updateById(acceptedUnit);
        verify(versionMapper).update(isNull(), any());

        service.acceptJob(71L);
        verify(unitMapper, times(1)).updateById(acceptedUnit);
        verify(versionMapper, times(1)).update(isNull(), any());

        ContentGenerationJob discardedJob = job(72L, "completed");
        ContentVersion discardedCandidate = version(172L, "candidate");
        discardedCandidate.setContentUnitId(18L);
        ContentUnit untouchedUnit = unit(18L, 88L);
        when(jobMapper.selectById(72L)).thenReturn(discardedJob);
        when(versionMapper.selectOne(any())).thenReturn(discardedCandidate);
        when(unitMapper.selectById(18L)).thenReturn(untouchedUnit);

        GenerationJobView discarded = service.discardJob(72L);
        assertThat(discarded.resultDisposition()).isEqualTo("discarded");
        assertThat(untouchedUnit.getCurrentVersionId()).isEqualTo(88L);
        verify(unitMapper, never()).updateById(untouchedUnit);
        verify(versionMapper, times(2)).update(isNull(), any());

        service.discardJob(72L);
        verify(versionMapper, times(2)).update(isNull(), any());
        verify(versionMapper, never()).updateById(any(ContentVersion.class));
    }

    @Test
    void acceptRejectsNonCompletedOrDiscardedCandidates() {
        when(jobMapper.selectById(73L)).thenReturn(job(73L, "processing"));
        assertThatThrownBy(() -> service.acceptJob(73L)).isInstanceOf(BizException.class);

        when(jobMapper.selectById(74L)).thenReturn(job(74L, "completed"));
        when(versionMapper.selectOne(any())).thenReturn(version(174L, "discarded"));
        assertThatThrownBy(() -> service.acceptJob(74L)).isInstanceOf(BizException.class);
    }

    @Test
    void viewExposesActualCreditsFailureAndCandidateReference() {
        ContentGenerationJob failed = job(75L, "failed");
        failed.setEstimatedCredits(20);
        failed.setActualCredits(7);
        failed.setErrorCode("SCHEMA_VALIDATION_FAILED");
        when(jobMapper.selectById(75L)).thenReturn(failed);

        GenerationJobView failureView = service.getJob(75L);
        assertThat(failureView.actualCredits()).isEqualTo(7);
        assertThat(failureView.errorCode()).isEqualTo("SCHEMA_VALIDATION_FAILED");
        assertThat(failureView.errorMessage()).contains("结构");

        ContentGenerationJob completed = job(76L, "completed");
        when(jobMapper.selectById(76L)).thenReturn(completed);
        when(versionMapper.selectOne(any())).thenReturn(version(176L, "candidate"));
        GenerationJobView completedView = service.getJob(76L);
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

        service.cancelJob(77L);
        service.cancelJob(77L);
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

    private ContentGenerationJob job(Long id, String status) {
        ContentGenerationJob job = new ContentGenerationJob();
        job.setId(id);
        job.setUuid("job-" + id);
        job.setProjectId(9L);
        job.setJobType("script_body_generate");
        job.setTargetType("content_unit");
        job.setTargetId(17L);
        job.setStatus(status);
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
