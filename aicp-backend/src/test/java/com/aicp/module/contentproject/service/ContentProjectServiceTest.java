package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Role;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ProjectMemberMapper;
import com.aicp.module.contentproject.mapper.ProjectParameterVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentProjectService 单元测试")
class ContentProjectServiceTest {

    @Mock ContentProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock ProjectParameterVersionMapper parameterVersionMapper;
    @Mock ProjectAccessService accessService;
    @Mock OutboxService outboxService;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    ContentProjectService service;

    private CreateProjectRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreateProjectRequest("测试短剧", "short_drama", "ai_manual",
                "人物林夏发现账本被篡改", "追更", "personal", null);
    }

    @Test
    @DisplayName("创建项目 → 添加 Owner 成员并设置起始阶段")
    void createAddsOwnerMembershipAndResumeDefaults() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"start_content\":\"...\"}");
        // Simulate MyBatis-Plus auto ID assignment
        doAnswer(inv -> {
            ContentProject p = inv.getArgument(0);
            p.setId(1L);
            return 1;
        }).when(projectMapper).insert(any(ContentProject.class));
        doAnswer(inv -> {
            com.aicp.module.contentproject.entity.ProjectParameterVersion pv = inv.getArgument(0);
            pv.setId(10L);
            return 1;
        }).when(parameterVersionMapper).insert(any(com.aicp.module.contentproject.entity.ProjectParameterVersion.class));

        ProjectDetail result = service.create(7L, validRequest);

        assertThat(result.lastStageKey()).isEqualTo("story_seed");
        verify(memberMapper).insert(argThat(m -> m.getUserId().equals(7L)
                && m.getRole().equals(Role.OWNER.name().toLowerCase())));
        verify(outboxService).append(eq("content_project.created"), eq(1L), eq(0), any());
    }

    @Test
    @DisplayName("获取项目 → 已删除项目抛出 NOT_FOUND")
    void getDeletedProjectThrowsNotFound() {
        ContentProject deleted = new ContentProject();
        deleted.setId(99L);
        deleted.setIsDeleted(1);
        // accessService.require is a void mock → does nothing
        when(projectMapper.selectById(99L)).thenReturn(deleted);

        assertThatThrownBy(() -> service.get(7L, 99L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PROJECT_NOT_FOUND.getCode());
    }
}
