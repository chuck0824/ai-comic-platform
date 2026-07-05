package com.aicp.module.canvas.service;

import com.aicp.common.exception.BizException;
import com.aicp.module.canvas.dto.CanvasProjectRequests.CreateCanvasProjectRequest;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CanvasProjectManagementServiceTest {

    @Mock
    CanvasProjectMapper projectMapper;

    CanvasProjectManagementService service;

    @BeforeEach
    void setUp() {
        service = new CanvasProjectManagementService(projectMapper, new ObjectMapper());
        lenient().when(projectMapper.selectOne(any())).thenReturn(null);
        lenient().doAnswer(invocation -> {
            CanvasProject project = invocation.getArgument(0);
            project.setId(99L);
            return 1;
        }).when(projectMapper).insert(any(CanvasProject.class));
    }

    @Test
    void createsStandaloneCanvasWithoutUpstreamIds() {
        var request = new CreateCanvasProjectRequest(
                "空白画布", null, null, null, null, null,
                "experiment", 7L, "canvas-create:7:blank:abc");

        var result = service.create(7L, request);

        assertThat(result.uuid()).startsWith("canvas_");
        assertThat(result.purpose()).isEqualTo("experiment");
        assertThat(result.contentProjectId()).isNull();
        assertThat(result.productionUnitId()).isNull();
        assertThat(result.productionSnapshot().storyboardLocked()).isFalse();
        assertThat(result.productionSnapshot().metadata().get("standalone")).isEqualTo(true);
        verify(projectMapper).insert(argThat(project ->
                "draft".equals(project.getStatus())
                        && "personal_7".equals(project.getWorkspaceId())));
    }

    @Test
    void rejectsPartialUpstreamBinding() {
        var request = new CreateCanvasProjectRequest(
                "不完整绑定", 3L, null, null, null, null,
                "official", 7L, "canvas-create:7:partial");

        assertThatThrownBy(() -> service.create(7L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("关联内容项目时必须同时提供生产单元和来源版本");
        verify(projectMapper, never()).insert(any());
    }

    @Test
    void preservesCompleteUpstreamBinding() {
        var request = new CreateCanvasProjectRequest(
                "绑定画布", 3L, "episode", 4L, 5L, 6L,
                "official", 7L, "canvas-create:7:bound");

        service.create(7L, request);

        verify(projectMapper).insert(argThat(project ->
                Long.valueOf(3L).equals(project.getContentProjectId())
                        && Long.valueOf(4L).equals(project.getProductionUnitId())
                        && Long.valueOf(5L).equals(project.getSourceContentVersionId())
                        && Long.valueOf(6L).equals(project.getSourceStoryboardVersionId())));
    }

    @Test
    void returnsExistingCanvasForRepeatedIdempotencyKey() {
        CanvasProject existing = new CanvasProject();
        existing.setId(12L);
        existing.setUuid("canvas_existing");
        existing.setUserId(7L);
        existing.setOwnerId(7L);
        existing.setName("已存在画布");
        existing.setPurpose("experiment");
        existing.setStatus("draft");
        existing.setCanvasVersion(1);
        existing.setRevision(0);
        existing.setIsDeleted(0);
        existing.setProductionSnapshot("{\"storyboardLocked\":false,\"shotCount\":0,\"fps\":25,\"metadata\":{\"standalone\":true}}");
        when(projectMapper.selectOne(any())).thenReturn(existing);

        var request = new CreateCanvasProjectRequest(
                "空白画布", null, null, null, null, null,
                "experiment", 7L, "canvas-create:7:blank:abc");

        var result = service.create(7L, request);

        assertThat(result.uuid()).isEqualTo("canvas_existing");
        verify(projectMapper, never()).insert(any());
    }
}
