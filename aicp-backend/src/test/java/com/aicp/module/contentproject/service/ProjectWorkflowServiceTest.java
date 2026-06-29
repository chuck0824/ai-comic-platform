package com.aicp.module.contentproject.service;

import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ProjectParameterVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectWorkflowService 单元测试")
class ProjectWorkflowServiceTest {

    @Mock ContentProjectMapper projectMapper;
    @Mock ProjectParameterVersionMapper parameterVersionMapper;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    ProjectWorkflowService service;

    private ContentProject project(String sourceMode, String storyboardIntent) {
        ContentProject p = new ContentProject();
        p.setId(1L);
        p.setName("测试");
        p.setCreationMode("short_drama");
        p.setSourceMode(sourceMode);
        p.setStoryboardIntentStatus(storyboardIntent);
        p.setLastStageKey("story_seed");
        p.setIsDeleted(0);
        return p;
    }

    @Test
    @DisplayName("上传项目跳过 story_seed，起始阶段为 import_review")
    void uploadedProjectSkipsSatisfiedSeedAndStartsAtImportReview() {
        ContentProject p = project("uploaded", "not_decided");

        WorkflowView view = service.calculate(p, Map.of("story_seed", true));

        assertThat(view.currentStageKey()).isEqualTo("import_review");
        assertThat(view.stages().stream()
                .filter(s -> "story_seed".equals(s.key()))
                .findFirst().orElseThrow().status()).isEqualTo("completed");
    }

    @Test
    @DisplayName("跳过 storyboard 不降低完成度")
    void skippedStoryboardDoesNotReduceCompletion() {
        ContentProject p = project("ai_manual", "skipped");
        p.setLastStageKey("destination");

        WorkflowView view = service.calculate(p, Map.of());

        assertThat(view.progress()).isEqualTo(100);
        assertThat(view.stages().stream()
                .filter(s -> "storyboard".equals(s.key()))
                .findFirst().orElseThrow().status()).isEqualTo("skipped");
    }

    @Test
    @DisplayName("AI手动项目从 story_seed 开始")
    void aiManualStartsAtStorySeed() {
        ContentProject p = project("ai_manual", "not_decided");

        WorkflowView view = service.calculate(p, Map.of());

        assertThat(view.currentStageKey()).isEqualTo("story_seed");
    }

    @Test
    @DisplayName("请求分镜后 storyboard 状态为 pending")
    void requestedStoryboardIsPending() {
        ContentProject p = project("ai_manual", "requested");
        p.setLastStageKey("destination");

        WorkflowView view = service.calculate(p, Map.of());

        assertThat(view.stages().stream()
                .filter(s -> "storyboard".equals(s.key()))
                .findFirst().orElseThrow().status()).isEqualTo("pending");
    }
}
