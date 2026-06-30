package com.aicp.module.storyboard.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.storyboard.entity.Storyboard;
import com.aicp.module.storyboard.entity.StoryboardScene;
import com.aicp.module.storyboard.entity.StoryboardShot;
import com.aicp.module.storyboard.entity.StoryboardVersion;
import com.aicp.module.storyboard.mapper.StoryboardMapper;
import com.aicp.module.storyboard.mapper.StoryboardSceneMapper;
import com.aicp.module.storyboard.mapper.StoryboardVersionShotMapper;
import com.aicp.module.storyboard.mapper.StoryboardVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardAccessService {

    private final StoryboardMapper storyboardMapper;
    private final StoryboardVersionMapper versionMapper;
    private final StoryboardSceneMapper sceneMapper;
    private final StoryboardVersionShotMapper shotMapper;
    private final ProjectAccessService projectAccessService;

    public Storyboard requireStoryboard(Long projectId, Long storyboardId, Long userId, Action action) {
        projectAccessService.require(projectId, userId, action);
        Storyboard storyboard = storyboardMapper.selectById(storyboardId);
        if (storyboard == null || !projectId.equals(storyboard.getProjectId())) {
            throw new BizException(ErrorCode.STORYBOARD_NOT_FOUND);
        }
        return storyboard;
    }

    public StoryboardVersion requireVersion(Long projectId, Long versionId, Long userId, Action action) {
        projectAccessService.require(projectId, userId, action);
        StoryboardVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_NOT_FOUND);
        }
        Storyboard storyboard = storyboardMapper.selectById(version.getStoryboardId());
        if (storyboard == null || !projectId.equals(storyboard.getProjectId())) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_NOT_FOUND);
        }
        return version;
    }

    public StoryboardScene requireScene(Long projectId, Long versionId, Long sceneId, Long userId, Action action) {
        requireVersion(projectId, versionId, userId, action);
        StoryboardScene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !versionId.equals(scene.getVersionId())) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_NOT_FOUND);
        }
        return scene;
    }

    public StoryboardShot requireShot(Long projectId, Long versionId, Long shotId, Long userId, Action action) {
        requireVersion(projectId, versionId, userId, action);
        StoryboardShot shot = shotMapper.selectById(shotId);
        if (shot == null || !versionId.equals(shot.getVersionId())) {
            throw new BizException(ErrorCode.STORYBOARD_VERSION_NOT_FOUND);
        }
        return shot;
    }
}
