package com.aicp.module.storyboard.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.storyboard.entity.Storyboard;
import com.aicp.module.storyboard.entity.StoryboardVersion;
import com.aicp.module.storyboard.mapper.StoryboardMapper;
import com.aicp.module.storyboard.mapper.StoryboardVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@DisplayName("分镜访问控制 单元测试")
class StoryboardAccessServiceTest {

    @Mock
    StoryboardMapper storyboardMapper;
    @Mock
    StoryboardVersionMapper versionMapper;
    @Mock
    ProjectAccessService projectAccessService;

    @InjectMocks
    StoryboardAccessService service;

    @Nested
    @DisplayName("requireVersion")
    class RequireVersion {

        @Test
        @DisplayName("版本不属于项目时抛出版本不存在")
        void rejectsVersionFromAnotherProject() {
            Storyboard sb = new Storyboard();
            sb.setId(9L);
            sb.setProjectId(2L);
            StoryboardVersion version = new StoryboardVersion();
            version.setId(7L);
            version.setStoryboardId(9L);
            when(versionMapper.selectById(7L)).thenReturn(version);
            when(storyboardMapper.selectById(9L)).thenReturn(sb);

            assertThatThrownBy(() -> service.requireVersion(1L, 7L, 3L, Action.VIEW))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("分镜版本不存在");
        }

        @Test
        @DisplayName("版本不存在时抛出异常")
        void rejectsNonExistentVersion() {
            when(versionMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> service.requireVersion(1L, 99L, 3L, Action.VIEW))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("分镜版本不存在");
        }
    }

    @Nested
    @DisplayName("requireStoryboard")
    class RequireStoryboard {

        @Test
        @DisplayName("分镜不属于项目时抛出分镜不存在")
        void rejectsStoryboardFromAnotherProject() {
            Storyboard sb = new Storyboard();
            sb.setId(5L);
            sb.setProjectId(2L);
            when(storyboardMapper.selectById(5L)).thenReturn(sb);

            assertThatThrownBy(() -> service.requireStoryboard(1L, 5L, 3L, Action.VIEW))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("分镜不存在");
        }
    }
}
