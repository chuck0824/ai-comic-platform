package com.aicp.module.storyboard.service;

import com.aicp.common.exception.BizException;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.storyboard.dto.StoryboardRequests.PatchShotRequest;
import com.aicp.module.storyboard.entity.StoryboardScene;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@DisplayName("分镜编辑服务 单元测试")
class StoryboardEditingServiceTest {

    @Mock StoryboardVersionMapper versionMapper;
    @Mock StoryboardSceneMapper sceneMapper;
    @Mock StoryboardVersionShotMapper shotMapper;
    @Mock StoryboardAuditLogMapper auditLogMapper;
    @Mock StoryboardAccessService accessService;
    @Mock StoryboardVersionService versionService;

    @InjectMocks
    StoryboardEditingService service;

    private StoryboardVersion draftVersion(int revision) {
        StoryboardVersion v = new StoryboardVersion();
        v.setId(10L);
        v.setStoryboardId(5L);
        v.setStatus("draft");
        v.setRevision(revision);
        v.setTier("A");
        return v;
    }

    private StoryboardShot sampleShot(Long id, Long durationMs) {
        StoryboardShot s = new StoryboardShot();
        s.setId(id);
        s.setUuid("shot-uuid-" + id);
        s.setVersionId(10L);
        s.setSceneId(1L);
        s.setShotKey("sk-" + id);
        s.setShotCode("S01-C01");
        s.setDurationMs(durationMs);
        s.setShotSize("中景");
        s.setVisualDescription("测试描述");
        s.setStatus("draft");
        s.setSortOrder(0);
        return s;
    }

    @Nested
    @DisplayName("patchShot")
    class PatchShotTests {

        @Test
        @DisplayName("PATCH 镜头更新字段并递增 revision")
        void patchShotUpdatesFieldsAndRevision() {
            StoryboardVersion version = draftVersion(8);
            StoryboardShot shot = sampleShot(100L, 3000L);
            when(accessService.requireVersion(1L, 10L, 7L, Action.EDIT_CONTENT)).thenReturn(version);
            when(accessService.requireShot(1L, 10L, 100L, 7L, Action.EDIT_CONTENT)).thenReturn(shot);
            when(shotMapper.selectList(any())).thenReturn(List.of(shot));

            var request = new PatchShotRequest(8, 4500L, "特写", "闭眼半笑",
                    "侧逆光", "嘴角上扬", "不甘", "你最好祈祷", List.of("雨夜"),
                    "暴雨", "《小丑》", "image", "video", "confirmed");
            var result = service.patchShot(1L, 10L, 100L, 7L, request);

            assertThat(result.durationMs()).isEqualTo(4500L);
            assertThat(result.shotSize()).isEqualTo("特写");
            assertThat(result.status()).isEqualTo("confirmed");
        }
    }

    @Nested
    @DisplayName("revision conflict")
    class RevisionConflictTests {

        @Test
        @DisplayName("锁定版本拒绝编辑")
        void rejectsLockedVersion() {
            StoryboardVersion version = draftVersion(8);
            version.setStatus("locked");
            when(accessService.requireVersion(1L, 10L, 7L, Action.EDIT_CONTENT)).thenReturn(version);

            var request = new PatchShotRequest(8, 4500L, null, null,
                    null, null, null, null, null,
                    null, null, null, null, null);
            assertThatThrownBy(() -> service.patchShot(1L, 10L, 100L, 7L, request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("锁定");
        }
    }

    @Nested
    @DisplayName("splitShot")
    class SplitShotTests {

        @Test
        @DisplayName("拆分镜头分割时长")
        void splitDividesDuration() {
            StoryboardVersion version = draftVersion(5);
            StoryboardShot shot = sampleShot(1L, 6000L);
            shot.setSortOrder(0);
            when(accessService.requireVersion(1L, 10L, 7L, Action.EDIT_CONTENT)).thenReturn(version);
            when(accessService.requireShot(1L, 10L, 1L, 7L, Action.EDIT_CONTENT)).thenReturn(shot);
            when(shotMapper.selectList(any())).thenReturn(List.of(shot));

            var request = new com.aicp.module.storyboard.dto.StoryboardRequests.SplitShotRequest(2500L);
            var results = service.splitShot(1L, 10L, 1L, 7L, request);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).durationMs()).isEqualTo(2500L);
            assertThat(results.get(1).durationMs()).isEqualTo(3500L);
        }

        @Test
        @DisplayName("拆分时长无效时抛出异常")
        void rejectsInvalidSplitDuration() {
            StoryboardVersion version = draftVersion(5);
            StoryboardShot shot = sampleShot(1L, 3000L);
            when(accessService.requireVersion(1L, 10L, 7L, Action.EDIT_CONTENT)).thenReturn(version);
            when(accessService.requireShot(1L, 10L, 1L, 7L, Action.EDIT_CONTENT)).thenReturn(shot);

            var request = new com.aicp.module.storyboard.dto.StoryboardRequests.SplitShotRequest(3000L);
            assertThatThrownBy(() -> service.splitShot(1L, 10L, 1L, 7L, request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("拆分时长");
        }
    }
}
