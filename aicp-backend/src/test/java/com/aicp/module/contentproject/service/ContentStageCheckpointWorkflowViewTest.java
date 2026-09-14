package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.config.ScriptStageTruthProperties;
import com.aicp.module.contentproject.dto.ContentProjectViews.WorkflowView;
import com.aicp.module.contentproject.dto.StageCheckpointViews.StageCheckpointView;
import com.aicp.module.contentproject.dto.StageCheckpointViews.StageProjection;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentStageCheckpointMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.aicp.module.contentproject.service.stage.StageGateRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ContentStageCheckpointWorkflowViewTest {

    @Mock ContentStageCheckpointMapper checkpointMapper;
    @Mock ContentProjectMapper projectMapper;
    @Mock ContentUnitMapper contentUnitMapper;
    @Mock ContentVersionMapper contentVersionMapper;
    @Mock ProjectAccessService accessService;
    @Mock StageGateRegistry gateRegistry;
    @Mock ScriptStageTruthProperties truthProperties;

    ContentStageCheckpointService service;

    @BeforeEach
    void setUp() {
        service = new ContentStageCheckpointService(
                checkpointMapper, projectMapper, contentUnitMapper, contentVersionMapper,
                accessService, gateRegistry, truthProperties, new ObjectMapper());
    }

    @Test
    void mapsCheckpointProjectionToWorkflowView() {
        StageProjection projection = new StageProjection(
                12L,
                3,
                List.of(
                        row("creation_settings", "COMPLETED"),
                        row("novel_upload", "COMPLETED"),
                        row("novel_analysis", "IN_PROGRESS"),
                        row("adaptation", "NOT_STARTED"),
                        row("structured_script", "NOT_STARTED"),
                        row("script_body", "NOT_STARTED"),
                        row("review_revision", "NOT_STARTED"),
                        row("text_storyboard", "NOT_STARTED")
                ),
                "novel_analysis",
                true
        );

        WorkflowView view = service.toWorkflowView(projection);
        assertThat(view.currentStageKey()).isEqualTo("novel_analysis");
        assertThat(view.progress()).isEqualTo(25);
        assertThat(view.stages()).filteredOn(s -> "completed".equals(s.status())).hasSize(2);
        assertThat(view.stages()).filteredOn(s -> "current".equals(s.status())).hasSize(1);
        assertThat(view.stages().get(2).label()).isEqualTo("小说分析");
    }

    private StageCheckpointView row(String key, String state) {
        return new StageCheckpointView(
                key, state, 0, null, null, null,
                Map.of("blockers", List.of(), "warnings", List.of()),
                List.of(),
                List.of());
    }
}
