package com.aicp.module.contentproject;

import com.aicp.module.asset.entity.AssetVersion;
import com.aicp.module.asset.entity.WorkspaceAsset;
import com.aicp.module.asset.mapper.AssetApplicationMapper;
import com.aicp.module.asset.mapper.AssetVersionMapper;
import com.aicp.module.asset.mapper.CanvasAssetPlacementMapper;
import com.aicp.module.asset.mapper.WorkspaceAssetMapper;
import com.aicp.module.canvas.mapper.CanvasNodeMapper;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.contentproject.service.ProjectSceneAssetService;
import com.aicp.module.contentproject.service.SceneAssetMarkdownProjector;
import com.aicp.module.storyboard.mapper.StoryboardMapper;
import com.aicp.module.storyboard.mapper.StoryboardVersionMapper;
import com.aicp.module.storyboard.mapper.StoryboardVersionShotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Guards the list endpoint against asset-count and reference-count N+1 queries. */
@ExtendWith(MockitoExtension.class)
class ProjectSceneAssetBatchQueryTest {

    @Mock WorkspaceAssetMapper assetMapper;
    @Mock AssetVersionMapper versionMapper;
    @Mock AssetApplicationMapper applicationMapper;
    @Mock CanvasAssetPlacementMapper placementMapper;
    @Mock ContentUnitMapper contentUnitMapper;
    @Mock StoryboardMapper storyboardMapper;
    @Mock StoryboardVersionMapper storyboardVersionMapper;
    @Mock StoryboardVersionShotMapper storyboardShotMapper;
    @Mock CanvasNodeMapper canvasNodeMapper;
    @Mock CanvasProjectMapper canvasProjectMapper;
    @Mock ProjectAccessService projectAccess;
    @Mock SceneAssetMarkdownProjector markdownProjector;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks ProjectSceneAssetService service;

    @Test
    void listUsesBoundedBatchReadsWhenAssetCountGrows() {
        List<WorkspaceAsset> assets = new ArrayList<>();
        List<AssetVersion> versions = new ArrayList<>();
        for (long id = 1; id <= 25; id++) {
            WorkspaceAsset asset = new WorkspaceAsset();
            asset.setId(id);
            asset.setUuid("asset-" + id);
            asset.setContentProjectId(9L);
            asset.setAssetType("SCENE");
            asset.setSourceType("PROJECT_GENERATED");
            asset.setName("scene-" + id);
            asset.setStatus("ACTIVE");
            asset.setCurrentVersionId(100L + id);
            assets.add(asset);

            AssetVersion version = new AssetVersion();
            version.setId(100L + id);
            version.setAssetId(id);
            version.setVersionNumber(1);
            version.setMetadata("{\"schema_version\":1,\"master\":{\"space_type\":\"INTERIOR\",\"reusability\":\"PRIMARY\",\"reality_type\":\"REALISTIC\"},\"variants\":[]}");
            versions.add(version);
        }
        when(assetMapper.selectList(any())).thenReturn(assets);
        when(versionMapper.selectList(any())).thenReturn(versions);
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(placementMapper.selectList(any())).thenReturn(List.of());
        when(storyboardMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.list(7L, 9L, null, null, null, null, null)).hasSize(25);

        verify(versionMapper, atMostOnce()).selectList(any());
        verify(versionMapper, never()).selectBatchIds(any());
        verify(versionMapper, never()).selectById(any());
        verify(assetMapper, never()).selectById(any());
        verify(applicationMapper, atMostOnce()).selectList(any());
        verify(placementMapper, atMostOnce()).selectList(any());
        verify(storyboardMapper, atMostOnce()).selectList(any());
        verify(storyboardVersionMapper, atMostOnce()).selectBatchIds(any());
        verify(storyboardShotMapper, atMostOnce()).selectList(any());
        verify(contentUnitMapper, atMostOnce()).selectBatchIds(any());
    }
}
