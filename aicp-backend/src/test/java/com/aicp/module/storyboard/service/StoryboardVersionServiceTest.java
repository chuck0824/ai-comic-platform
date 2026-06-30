package com.aicp.module.storyboard.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.storyboard.entity.Storyboard;
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
@DisplayName("分镜版本生命周期 单元测试")
class StoryboardVersionServiceTest {

    @Mock StoryboardMapper storyboardMapper;
    @Mock StoryboardVersionMapper versionMapper;
    @Mock StoryboardSceneMapper sceneMapper;
    @Mock StoryboardVersionShotMapper shotMapper;
    @Mock StoryboardJobMapper jobMapper;
    @Mock StoryboardAuditLogMapper auditLogMapper;
    @Mock StoryboardAccessService accessService;
    @Mock ProjectAccessService projectAccessService;

    @InjectMocks
    StoryboardVersionService service;

    private StoryboardVersion draftA(int revision) {
        StoryboardVersion v = new StoryboardVersion();
        v.setId(10L);
        v.setUuid("v-uuid");
        v.setStoryboardId(5L);
        v.setTier("A");
        v.setVersionNo(1);
        v.setStatus("draft");
        v.setRevision(revision);
        v.setSourceContentVersionId(100L);
        v.setSchemaVersion(1);
        v.setTotalScenes(1);
        v.setTotalShots(2);
        v.setTotalDurationMs(6000L);
        return v;
    }

    private StoryboardVersion lockedA() {
        StoryboardVersion v = draftA(4);
        v.setStatus("locked");
        v.setLockedBy(7L);
        return v;
    }

    @Nested
    @DisplayName("lockVersion")
    class LockVersion {

        @Test
        @DisplayName("锁定后版本状态变为locked")
        void lockMakesVersionImmutable() {
            StoryboardVersion version = draftA(4);
            Storyboard sb = new Storyboard();
            sb.setId(5L);
            sb.setProjectId(1L);

            when(accessService.requireVersion(1L, 10L, 7L, Action.PRODUCE)).thenReturn(version);
            when(versionMapper.update(isNull(), any())).thenReturn(1);
            when(storyboardMapper.selectById(5L)).thenReturn(sb);

            var result = service.lockVersion(1L, 10L, 7L, 4, "lock-10");
            assertThat(result.status()).isEqualTo("locked");
        }
    }

    @Nested
    @DisplayName("upgradeVersion")
    class UpgradeVersion {

        @Test
        @DisplayName("升档创建子版本并保持父版本锁定")
        void upgradeCreatesChildAndKeepsParentLocked() {
            StoryboardVersion parent = lockedA();
            Storyboard sb = new Storyboard();
            sb.setId(5L);
            sb.setProjectId(1L);

            StoryboardScene scene = new StoryboardScene();
            scene.setId(1L);
            scene.setVersionId(10L);
            scene.setSceneKey("sk-1");
            scene.setSceneNo(1);
            scene.setSortOrder(0);

            StoryboardShot shot = new StoryboardShot();
            shot.setId(1L);
            shot.setVersionId(10L);
            shot.setSceneId(1L);
            shot.setShotKey("shk-1");
            shot.setShotCode("S01-C01");
            shot.setDurationMs(3000L);
            shot.setSortOrder(0);

            when(accessService.requireVersion(1L, 10L, 7L, Action.EDIT_CONTENT)).thenReturn(parent);
            when(storyboardMapper.selectById(5L)).thenReturn(sb);
            when(versionMapper.selectList(any())).thenReturn(List.of());
            when(sceneMapper.selectList(any())).thenReturn(List.of(scene));
            when(shotMapper.selectList(any())).thenReturn(List.of(shot));
            when(versionMapper.insert(any())).thenAnswer(inv -> {
                StoryboardVersion v = inv.getArgument(0);
                v.setId(11L);
                return 1;
            });
            when(sceneMapper.insert(any())).thenReturn(1);
            when(shotMapper.insert(any())).thenReturn(1);

            var result = service.upgradeVersion(1L, 10L, 7L, "B", "up-10-b");
            assertThat(result.tier()).isEqualTo("B");
            assertThat(result.parentVersionId()).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("submitForReview")
    class SubmitReview {

        @Test
        @DisplayName("草稿可以提交审核")
        void draftCanBeSubmitted() {
            StoryboardVersion version = draftA(3);
            when(accessService.requireVersion(1L, 10L, 7L, Action.EDIT_CONTENT)).thenReturn(version);
            when(versionMapper.update(isNull(), any())).thenReturn(1);

            var result = service.submitForReview(1L, 10L, 7L, 3);
            assertThat(result.status()).isEqualTo("reviewing");
        }

        @Test
        @DisplayName("锁定版本不能提交审核")
        void lockedCannotBeSubmitted() {
            StoryboardVersion version = lockedA();
            when(accessService.requireVersion(1L, 10L, 7L, Action.EDIT_CONTENT)).thenReturn(version);

            assertThatThrownBy(() -> service.submitForReview(1L, 10L, 7L, 4))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("锁定");
        }
    }
}
