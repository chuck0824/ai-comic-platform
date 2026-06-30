package com.aicp.module.storyboard.service;

import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.storyboard.domain.ProductionGate;
import com.aicp.module.storyboard.entity.StoryboardShot;
import com.aicp.module.storyboard.entity.StoryboardVersion;
import com.aicp.module.storyboard.mapper.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@DisplayName("分镜审核服务 单元测试")
class StoryboardReviewServiceTest {

    @Mock StoryboardReviewIssueMapper issueMapper;
    @Mock StoryboardVersionShotMapper shotMapper;
    @Mock StoryboardCharacterVisualMapper characterVisualMapper;
    @Mock StoryboardShotVisualBindingMapper visualBindingMapper;
    @Mock StoryboardAccessService accessService;

    @InjectMocks
    StoryboardReviewService service;

    @Nested
    @DisplayName("ProductionGate")
    class GateTests {

        @Test
        @DisplayName("C档缺少生产字段时准入失败")
        void cTierFailsWhenProductionFieldsAreMissing() {
            StoryboardVersion version = new StoryboardVersion();
            version.setTier("C");
            version.setStatus("locked");
            StoryboardShot shot = new StoryboardShot();
            shot.setId(1L);
            shot.setShotCode("S01-C01");
            shot.setDurationMs(3000L);
            shot.setImagePrompt("image prompt");
            shot.setVideoMotionPrompt(null);

            var result = ProductionGate.evaluate(version, List.of(shot), List.of(), List.of());
            assertThat(result.allowed()).isFalse();
            assertThat(result.violations().stream().anyMatch(v -> v.contains("视频动作提示词"))).isTrue();
        }

        @Test
        @DisplayName("A档基本字段完整时准入通过")
        void aTierPassesWithBasicFields() {
            StoryboardVersion version = new StoryboardVersion();
            version.setTier("A");
            StoryboardShot shot = new StoryboardShot();
            shot.setShotCode("S01-C01");
            shot.setDurationMs(3000L);

            var result = ProductionGate.evaluate(version, List.of(shot), List.of("char1"), List.of());
            assertThat(result.allowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("runChecks")
    class RunChecksTests {

        @Test
        @DisplayName("检测到缺失时长时创建问题")
        void detectsMissingDuration() {
            StoryboardVersion version = new StoryboardVersion();
            version.setId(10L);
            version.setTier("A");
            StoryboardShot shot = new StoryboardShot();
            shot.setId(1L);
            shot.setShotCode("S01-C01");
            shot.setDurationMs(0L);
            shot.setVersionId(10L);
            shot.setSceneId(1L);

            when(accessService.requireVersion(1L, 10L, 7L, Action.REVIEW)).thenReturn(version);
            when(shotMapper.selectList(any())).thenReturn(List.of(shot));
            when(issueMapper.selectOne(any())).thenReturn(null);

            var issues = service.runChecks(1L, 10L, 7L);
            assertThat(issues).isNotEmpty();
            assertThat(issues.stream().anyMatch(i -> i.issueType().equals("duration_zero"))).isTrue();
        }
    }
}
