package com.aicp.module.storyboard.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.storyboard.entity.Storyboard;
import com.aicp.module.storyboard.mapper.StoryboardMapper;
import com.aicp.module.storyboard.mapper.StoryboardReviewIssueMapper;
import com.aicp.module.storyboard.mapper.StoryboardVersionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@DisplayName("分镜查询服务 单元测试")
class StoryboardQueryServiceTest {

    @Mock
    StoryboardMapper storyboardMapper;
    @Mock
    StoryboardVersionMapper versionMapper;
    @Mock
    StoryboardReviewIssueMapper reviewIssueMapper;
    @Mock
    ContentUnitMapper contentUnitMapper;
    @Mock
    ProjectAccessService projectAccessService;

    @InjectMocks
    StoryboardQueryService service;

    @Nested
    @DisplayName("getStoryboardDetail")
    class GetDetail {

        @Test
        @DisplayName("分镜不属于项目时抛出分镜不存在")
        void rejectsMismatchedProject() {
            Storyboard sb = new Storyboard();
            sb.setId(1L);
            sb.setProjectId(99L);
            when(storyboardMapper.selectById(1L)).thenReturn(sb);

            assertThatThrownBy(() -> service.getStoryboardDetail(1L, 1L, 3L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("分镜不存在");
        }

        @Test
        @DisplayName("分镜不存在时抛出异常")
        void rejectsNonExistent() {
            when(storyboardMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> service.getStoryboardDetail(1L, 99L, 3L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("分镜不存在");
        }
    }

    @Nested
    @DisplayName("createStoryboard")
    class Create {

        @Test
        @DisplayName("内容单元不属于项目时抛出参数异常")
        void rejectsUnitFromOtherProject() {
            ContentUnit unit = new ContentUnit();
            unit.setId(5L);
            unit.setProjectId(99L);
            when(contentUnitMapper.selectById(5L)).thenReturn(unit);

            assertThatThrownBy(() -> service.createStoryboard(
                    1L, 3L, 5L, 10L, "测试", "default"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("内容单元不属于该项目");
        }
    }
}
