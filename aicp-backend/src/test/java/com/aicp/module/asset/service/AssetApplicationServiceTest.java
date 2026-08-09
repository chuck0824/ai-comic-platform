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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetApplicationServiceTest {

    @Mock AssetLibraryService libraryService;
    @Mock WorkspaceAssetMapper assetMapper;
    @Mock AssetVersionMapper versionMapper;
    @Mock AssetApplicationMapper applicationMapper;
    @Mock CanvasProjectMapper projectMapper;
    @InjectMocks AssetApplicationService service;

    @Test
    void applyPersistsStableTextConsumerKey() {
        WorkspaceContext context = new WorkspaceContext("project_7", "project", 501L, Set.of("asset.use"));
        WorkspaceAsset asset = new WorkspaceAsset();
        asset.setId(3L);
        asset.setCurrentVersionId(8L);
        asset.setAssetType("SCENE");
        asset.setName("出租屋");
        AssetVersion version = new AssetVersion();
        version.setId(8L);
        CanvasProject project = new CanvasProject();
        project.setId(7L);
        project.setWorkspaceId("project_7");

        when(libraryService.requireWorkspaceAsset(context, 3L)).thenReturn(asset);
        when(versionMapper.selectById(8L)).thenReturn(version);
        when(projectMapper.selectById(7L)).thenReturn(project);
        doAnswer(invocation -> {
            invocation.<AssetApplication>getArgument(0).setId(41L);
            return 1;
        }).when(applicationMapper).insert(any(AssetApplication.class));

        service.apply(context, 3L, new AssetRequests.ApplyAssetRequest(
                7L, "SCRIPT_SCENE", 9001L, "EP-007-SCENE-001", "request-1"));

        ArgumentCaptor<AssetApplication> captor = ArgumentCaptor.forClass(AssetApplication.class);
        verify(applicationMapper).insert(captor.capture());
        assertThat(captor.getValue().getTargetId()).isEqualTo(9001L);
        assertThat(captor.getValue().getTargetKey()).isEqualTo("EP-007-SCENE-001");
    }
}
