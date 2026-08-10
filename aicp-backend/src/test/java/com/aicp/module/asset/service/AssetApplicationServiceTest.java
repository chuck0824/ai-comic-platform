package com.aicp.module.asset.service;

import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.asset.dto.AssetRequests;
import com.aicp.module.asset.entity.AssetApplication;
import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.mapper.AssetApplicationMapper;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetMapper;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetApplicationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock AssetLibraryService libraryService;
    @Mock WorkspaceAssetMapper assetMapper;
    @Mock AssetVersionMapper versionMapper;
    @Mock AssetApplicationMapper applicationMapper;
    @Mock CanvasProjectMapper projectMapper;
    @Mock ContentProjectMapper contentProjectMapper;
    @Mock ProjectAccessService projectAccessService;
    @InjectMocks AssetApplicationService service;

    @Test
    void applyPersistsStableTextConsumerKey() {
        WorkspaceContext context = configureContentProjectApplicationDependencies();
        service.apply(context, 3L, new AssetRequests.ApplyAssetRequest(
                7L, "SCRIPT_SCENE", 9001L, "EP-007-SCENE-001", "request-1"));

        ArgumentCaptor<AssetApplication> captor = ArgumentCaptor.forClass(AssetApplication.class);
        verify(applicationMapper).insert(captor.capture());
        assertThat(captor.getValue().getTargetId()).isEqualTo(9001L);
        assertThat(captor.getValue().getTargetKey()).isEqualTo("EP-007-SCENE-001");
        verify(projectAccessService).require(7L, 501L, Action.EDIT_CONTENT);
        verify(projectMapper, never()).selectById(7L);
    }

    @Test
    void legacyCanvasTargetStillValidatesAndUpdatesCanvasProject() {
        WorkspaceContext context = configureCanvasApplicationDependencies();
        service.apply(context, 3L, new AssetRequests.ApplyAssetRequest(
                7L, "CANVAS_NODE", 9001L, null, "request-canvas"));
        verify(projectMapper).selectById(7L);
        verify(projectMapper).updateById(any(CanvasProject.class));
        verify(contentProjectMapper, never()).selectById(7L);
    }

    private WorkspaceContext configureCommonApplicationDependencies() {
        WorkspaceContext context = new WorkspaceContext("project_7", "project", 501L, Set.of("asset.use"));
        WorkspaceAsset asset = new WorkspaceAsset();
        asset.setId(3L);
        asset.setCurrentVersionId(8L);
        asset.setAssetType("SCENE");
        asset.setName("出租屋");
        AssetVersion version = new AssetVersion();
        version.setId(8L);
        when(libraryService.requireWorkspaceAsset(context, 3L)).thenReturn(asset);
        when(versionMapper.selectById(8L)).thenReturn(version);
        doAnswer(invocation -> {
            invocation.<AssetApplication>getArgument(0).setId(41L);
            return 1;
        }).when(applicationMapper).insert(any(AssetApplication.class));
        return context;
    }

    private WorkspaceContext configureContentProjectApplicationDependencies() {
        WorkspaceContext context = configureCommonApplicationDependencies();
        ContentProject project = new ContentProject();
        project.setId(7L);
        project.setOwnerUserId(501L);
        project.setTenantType("personal");
        project.setTenantId(501L);
        project.setRevision(2);
        when(contentProjectMapper.selectById(7L)).thenReturn(project);
        return context;
    }

    private WorkspaceContext configureCanvasApplicationDependencies() {
        WorkspaceContext context = configureCommonApplicationDependencies();
        CanvasProject project = new CanvasProject();
        project.setId(7L);
        project.setWorkspaceId("project_7");
        when(projectMapper.selectById(7L)).thenReturn(project);
        return context;
    }

    @Test
    void deserializeApplyRequestFromSnakeCaseWirePayload() throws Exception {
        AssetRequests.ApplyAssetRequest request = objectMapper.readValue("""
                {"project_id":7,"target_type":"SCRIPT_SCENE","target_id":9001,
                 "target_key":"EP-007-SCENE-001","idempotency_key":"request-snake"}
                """, AssetRequests.ApplyAssetRequest.class);

        assertThat(request.projectId()).isEqualTo(7L);
        assertThat(request.targetType()).isEqualTo("SCRIPT_SCENE");
        assertThat(request.targetId()).isEqualTo(9001L);
        assertThat(request.targetKey()).isEqualTo("EP-007-SCENE-001");
        assertThat(request.idempotencyKey()).isEqualTo("request-snake");

        WorkspaceContext context = configureContentProjectApplicationDependencies();
        service.apply(context, 3L, request);
        ArgumentCaptor<AssetApplication> captor = ArgumentCaptor.forClass(AssetApplication.class);
        verify(applicationMapper).insert(captor.capture());
        assertThat(captor.getValue().getTargetKey()).isEqualTo("EP-007-SCENE-001");
    }

    @Test
    void deserializeApplyRequestFromMixedWirePayload() throws Exception {
        AssetRequests.ApplyAssetRequest request = objectMapper.readValue("""
                {"projectId":7,"targetType":"SCRIPT_SCENE","targetId":9001,
                 "target_key":"EP-007-SCENE-001","idempotencyKey":"request-mixed"}
                """, AssetRequests.ApplyAssetRequest.class);

        assertThat(request.projectId()).isEqualTo(7L);
        assertThat(request.targetType()).isEqualTo("SCRIPT_SCENE");
        assertThat(request.targetId()).isEqualTo(9001L);
        assertThat(request.targetKey()).isEqualTo("EP-007-SCENE-001");
        assertThat(request.idempotencyKey()).isEqualTo("request-mixed");
    }

    @Test
    void deserializeApplyRequestFromCamelCaseWirePayload() throws Exception {
        AssetRequests.ApplyAssetRequest request = objectMapper.readValue("""
                {"projectId":7,"targetType":"SCRIPT_SCENE","targetId":9001,
                 "targetKey":"EP-007-SCENE-001","idempotencyKey":"request-camel"}
                """, AssetRequests.ApplyAssetRequest.class);

        assertThat(request.projectId()).isEqualTo(7L);
        assertThat(request.targetType()).isEqualTo("SCRIPT_SCENE");
        assertThat(request.targetId()).isEqualTo(9001L);
        assertThat(request.targetKey()).isEqualTo("EP-007-SCENE-001");
        assertThat(request.idempotencyKey()).isEqualTo("request-camel");
    }
}
