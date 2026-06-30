package com.aicp.module.storyboard.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.entity.ContentUnit;
import com.aicp.module.contentproject.mapper.ContentUnitMapper;
import com.aicp.module.contentproject.service.ProjectAccessService;
import com.aicp.module.storyboard.dto.StoryboardViews;
import com.aicp.module.storyboard.dto.StoryboardViews.StoryboardDetail;
import com.aicp.module.storyboard.dto.StoryboardViews.StoryboardSummary;
import com.aicp.module.storyboard.entity.Storyboard;
import com.aicp.module.storyboard.entity.StoryboardVersion;
import com.aicp.module.storyboard.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardQueryService {

    private final StoryboardMapper storyboardMapper;
    private final StoryboardVersionMapper versionMapper;
    private final StoryboardSceneMapper sceneMapper;
    private final StoryboardVersionShotMapper shotMapper;
    private final StoryboardReviewIssueMapper reviewIssueMapper;
    private final ContentUnitMapper contentUnitMapper;
    private final ProjectAccessService projectAccessService;

    public StoryboardDetail getStoryboardDetail(Long projectId, Long storyboardId, Long userId) {
        Storyboard sb = storyboardMapper.selectById(storyboardId);
        if (sb == null || !projectId.equals(sb.getProjectId())) {
            throw new BizException(ErrorCode.STORYBOARD_NOT_FOUND);
        }
        projectAccessService.require(projectId, userId, Action.VIEW);
        return toDetail(sb);
    }

    public List<StoryboardSummary> listStoryboards(Long projectId, Long userId) {
        projectAccessService.require(projectId, userId, Action.VIEW);
        List<Storyboard> storyboards = storyboardMapper.selectList(
                new LambdaQueryWrapper<Storyboard>()
                        .eq(Storyboard::getProjectId, projectId)
                        .orderByDesc(Storyboard::getUpdatedAt));
        return storyboards.stream().map(sb -> {
            StoryboardVersion draft = sb.getCurrentDraftVersionId() != null
                    ? versionMapper.selectById(sb.getCurrentDraftVersionId()) : null;
            StoryboardVersion locked = sb.getCurrentLockedVersionId() != null
                    ? versionMapper.selectById(sb.getCurrentLockedVersionId()) : null;
            StoryboardVersion active = draft != null ? draft : locked;
            int openIssues = 0;
            if (active != null) {
                openIssues = reviewIssueMapper.selectCount(
                        new LambdaQueryWrapper<com.aicp.module.storyboard.entity.StoryboardReviewIssue>()
                                .eq(com.aicp.module.storyboard.entity.StoryboardReviewIssue::getVersionId, active.getId())
                                .eq(com.aicp.module.storyboard.entity.StoryboardReviewIssue::getStatus, "open")).intValue();
            }
            return new StoryboardSummary(
                    sb.getId(), sb.getUuid(), sb.getProjectId(), sb.getContentUnitId(),
                    sb.getTitle(), sb.getPurpose(),
                    active != null ? active.getTier() : null,
                    active != null ? active.getVersionNo() : null,
                    sb.getProductionStatus(),
                    active != null ? active.getTotalShots() : 0,
                    active != null ? active.getTotalScenes() : 0,
                    active != null ? active.getTotalDurationMs() : 0L,
                    openIssues,
                    "/content-projects/" + projectId + "/storyboards/" + sb.getId(),
                    sb.getUpdatedAt());
        }).toList();
    }

    public StoryboardDetail createStoryboard(Long projectId, Long userId,
                                              Long contentUnitId, Long sourceContentVersionId,
                                              String title, String purpose) {
        projectAccessService.require(projectId, userId, Action.EDIT_CONTENT);
        ContentUnit unit = contentUnitMapper.selectById(contentUnitId);
        if (unit == null || !projectId.equals(unit.getProjectId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "内容单元不属于该项目");
        }

        Storyboard sb = new Storyboard();
        sb.setUuid(UUID.randomUUID().toString());
        sb.setProjectId(projectId);
        sb.setContentUnitId(contentUnitId);
        sb.setSourceContentVersionId(sourceContentVersionId);
        sb.setTitle(title);
        sb.setPurpose(purpose != null ? purpose : "default");
        sb.setProductionStatus("not_ready");
        sb.setCreatedBy(userId);
        storyboardMapper.insert(sb);

        StoryboardVersion version = new StoryboardVersion();
        version.setUuid(UUID.randomUUID().toString());
        version.setStoryboardId(sb.getId());
        version.setSourceContentVersionId(sourceContentVersionId);
        version.setTier("A");
        version.setVersionNo(1);
        version.setStatus("draft");
        version.setRevision(0);
        version.setSchemaVersion(1);
        version.setCreatedFrom("manual");
        version.setCreatedBy(userId);
        versionMapper.insert(version);

        sb.setCurrentDraftVersionId(version.getId());
        storyboardMapper.updateById(sb);

        return toDetail(sb);
    }

    public List<StoryboardSummary> projectSummaries(Long projectId, Long userId) {
        projectAccessService.require(projectId, userId, Action.VIEW);
        return listStoryboards(projectId, userId);
    }

    private StoryboardDetail toDetail(Storyboard sb) {
        return new StoryboardDetail(
                sb.getId(), sb.getUuid(), sb.getProjectId(), sb.getContentUnitId(),
                sb.getSourceContentVersionId(), sb.getTitle(), sb.getPurpose(),
                sb.getCurrentDraftVersionId(), sb.getCurrentLockedVersionId(),
                sb.getProductionStatus(), sb.getCreatedBy(),
                sb.getCreatedAt(), sb.getUpdatedAt());
    }
}
