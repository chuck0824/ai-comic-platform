package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.dto.ContentProjectViews.StageView;
import com.aicp.module.contentproject.dto.ContentProjectViews.WorkflowView;
import com.aicp.module.contentproject.entity.StageTruthDiffLog;
import com.aicp.module.contentproject.mapper.StageTruthDiffLogMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StageTruthDiffLogServiceTest {

    @Mock StageTruthDiffLogMapper diffLogMapper;

    StageTruthDiffLogService service;

    @BeforeEach
    void setUp() {
        service = new StageTruthDiffLogService(diffLogMapper, new ObjectMapper());
    }

    @Test
    void skipsWhenViewsAreIdentical() {
        WorkflowView view = view("adaptation", 40, List.of(
                stage("creation_settings", "completed"),
                stage("adaptation", "current")
        ));
        assertThat(service.recordIfDifferent(1L, "workflow", view, view)).isFalse();
        verify(diffLogMapper, never()).insert(any());
    }

    @Test
    void insertsWhenCurrentStageDiffers() {
        when(diffLogMapper.selectOne(any())).thenReturn(null);

        WorkflowView legacy = view("story_seed", 10, List.of(
                stage("story_seed", "current"),
                stage("outline", "pending")
        ));
        WorkflowView truth = view("novel_analysis", 25, List.of(
                stage("creation_settings", "completed"),
                stage("novel_analysis", "current")
        ));

        assertThat(service.recordIfDifferent(9L, "workflow", legacy, truth)).isTrue();

        ArgumentCaptor<StageTruthDiffLog> captor = ArgumentCaptor.forClass(StageTruthDiffLog.class);
        verify(diffLogMapper).insert(captor.capture());
        StageTruthDiffLog row = captor.getValue();
        assertThat(row.getProjectId()).isEqualTo(9L);
        assertThat(row.getLegacyCurrentStage()).isEqualTo("story_seed");
        assertThat(row.getTruthCurrentStage()).isEqualTo("novel_analysis");
        assertThat(row.getFingerprint()).hasSize(64);
        assertThat(row.getDiffSummaryJson()).contains("currentStageMismatch");
    }

    @Test
    void buildDiffReportsStageStatusChanges() {
        Map<String, Object> legacy = service.compact(view("a", 0, List.of(
                stage("a", "current"),
                stage("b", "pending")
        )));
        Map<String, Object> truth = service.compact(view("b", 50, List.of(
                stage("a", "completed"),
                stage("b", "current")
        )));
        Map<String, Object> diff = service.buildDiff(legacy, truth);
        assertThat(diff.get("identical")).isEqualTo(false);
        assertThat(diff.get("currentStageMismatch")).isEqualTo(true);
        assertThat(diff.get("progressDelta")).isEqualTo(50);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stageDiffs = (List<Map<String, Object>>) diff.get("stageStatusDiffs");
        assertThat(stageDiffs).isNotEmpty();
    }

    private static WorkflowView view(String current, int progress, List<StageView> stages) {
        return new WorkflowView(current, "continue", progress, stages);
    }

    private static StageView stage(String key, String status) {
        return new StageView(key, key, status, true, List.of(), null, null);
    }
}
