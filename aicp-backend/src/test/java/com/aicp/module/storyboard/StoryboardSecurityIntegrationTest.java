package com.aicp.module.storyboard;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.storyboard.entity.Storyboard;
import com.aicp.module.storyboard.entity.StoryboardVersion;
import com.aicp.module.storyboard.mapper.StoryboardMapper;
import com.aicp.module.storyboard.mapper.StoryboardVersionMapper;
import com.aicp.module.storyboard.service.StoryboardAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@DisplayName("分镜安全隔离 单元测试")
class StoryboardSecurityIntegrationTest {

    @Mock StoryboardMapper storyboardMapper;
    @Mock StoryboardVersionMapper versionMapper;
    @Mock ProjectAccessService projectAccessService;
    @InjectMocks StoryboardAccessService accessService;

    @Nested
    @DisplayName("跨项目资源隔离")
    class CrossProjectIsolation {

        @Test
        @DisplayName("请求项目A的storyboard但资源属于项目B → 404")
        void rejectsCrossProjectStoryboard() {
            Storyboard sb = new Storyboard();
            sb.setId(1L);
            sb.setProjectId(2L); // belongs to project 2
            when(storyboardMapper.selectById(1L)).thenReturn(sb);

            assertThatThrownBy(() ->
                    accessService.requireStoryboard(1L, 1L, 3L, Action.VIEW))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("分镜不存在");
        }

        @Test
        @DisplayName("请求项目A的version但storyboard属于项目B → 404")
        void rejectsCrossProjectVersion() {
            Storyboard sb = new Storyboard();
            sb.setId(5L);
            sb.setProjectId(2L);
            StoryboardVersion version = new StoryboardVersion();
            version.setId(10L);
            version.setStoryboardId(5L);
            when(versionMapper.selectById(10L)).thenReturn(version);
            when(storyboardMapper.selectById(5L)).thenReturn(sb);

            assertThatThrownBy(() ->
                    accessService.requireVersion(1L, 10L, 3L, Action.VIEW))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("分镜版本不存在");
        }
    }

    @Nested
    @DisplayName("权限阶梯")
    class PermissionTiers {

        @Test
        @DisplayName("VIEW权限不能EDIT_CONTENT")
        void viewCannotEdit() {
            // projectAccessService.require throws before any DB query
            when(projectAccessService.require(1L, 3L, Action.EDIT_CONTENT))
                    .thenThrow(new BizException(ErrorCode.PROJECT_ACCESS_DENIED));

            assertThatThrownBy(() ->
                    accessService.requireStoryboard(1L, 1L, 3L, Action.EDIT_CONTENT))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("无项目访问权限");
        }
    }
}
