package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.config.ScriptStageTruthProperties;
import com.aicp.module.contentproject.dto.StageCheckpointViews.StoryboardHandoffView;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.StoryboardHandoffSnapshot;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentStageCheckpointMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.mapper.StoryboardHandoffSnapshotMapper;
import com.aicp.module.contentproject.service.stage.StageGateRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryboardHandoffReadTest {

    @Mock ContentStageCheckpointMapper checkpointMapper;
    @Mock ContentProjectMapper projectMapper;
    @Mock ContentUnitMapper contentUnitMapper;
    @Mock ContentVersionMapper contentVersionMapper;
    @Mock StoryboardHandoffSnapshotMapper handoffSnapshotMapper;
    @Mock ProjectAccessService accessService;
    @Mock StageGateRegistry gateRegistry;
    @Mock ScriptStageTruthProperties truthProperties;

    ContentStageCheckpointService service;

    @BeforeEach
    void setUp() {
        service = new ContentStageCheckpointService(
                checkpointMapper, projectMapper, contentUnitMapper, contentVersionMapper,
                handoffSnapshotMapper, accessService, gateRegistry, truthProperties, new ObjectMapper());
    }

    @Test
    void requireLatestHandoff_returnsReviewedVersionFields() {
        ContentProject project = new ContentProject();
        project.setId(9L);
        project.setIsDeleted(0);
        when(projectMapper.selectById(9L)).thenReturn(project);

        StoryboardHandoffSnapshot row = new StoryboardHandoffSnapshot();
        row.setId(501L);
        row.setProjectId(9L);
        row.setCheckpointId(80L);
        row.setReviewedScriptBodyVersionId(77L);
        row.setContinuityCheckResult("PASS");
        row.setSceneCount(2);
        row.setContentHash("abc");
        row.setCapturedAt(LocalDateTime.of(2026, 9, 14, 10, 0));
        row.setPayloadJson("{\"schemaVersion\":1}");
        when(handoffSnapshotMapper.selectOne(any())).thenReturn(row);

        StoryboardHandoffView view = service.requireLatestHandoff(1L, 9L);
        assertThat(view.id()).isEqualTo(501L);
        assertThat(view.reviewedScriptBodyVersionId()).isEqualTo(77L);
        assertThat(view.continuityCheckResult()).isEqualTo("PASS");
        assertThat(view.sceneCount()).isEqualTo(2);
    }

    @Test
    void requireLatestHandoff_missing_throwsNotFound() {
        ContentProject project = new ContentProject();
        project.setId(9L);
        project.setIsDeleted(0);
        when(projectMapper.selectById(9L)).thenReturn(project);
        when(handoffSnapshotMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.requireLatestHandoff(1L, 9L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }
}
