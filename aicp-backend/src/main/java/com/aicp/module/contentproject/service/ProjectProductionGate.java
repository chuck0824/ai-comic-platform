package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates that a locked version exists and belongs to the project
 * before allowing storyboard, canvas, or listing entry.
 */
@Component
@RequiredArgsConstructor
public class ProjectProductionGate {

    private final ContentProjectMapper projectMapper;
    private final ContentVersionMapper versionMapper;

    /**
     * Check that the project has a locked version ready for production.
     * Returns null on success, or a user-facing blocked reason string.
     */
    public String checkBlocked(Long projectId) {
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            return "项目不存在";
        }
        if (!"locked".equals(project.getContentStatus())) {
            return "请先锁定一个审核通过的内容版本";
        }
        Long adoptedVersionId = project.getAdoptedVersionId();
        if (adoptedVersionId == null) {
            return "请先锁定一个审核通过的内容版本";
        }
        ContentVersion version = versionMapper.selectById(adoptedVersionId);
        if (version == null || !"locked".equals(version.getStatus())) {
            return "锁稿版本不存在或状态异常";
        }
        if (!version.getProjectId().equals(projectId)) {
            return "锁稿版本不属于该项目";
        }
        return null; // gate passed
    }

    /**
     * Hard-check: throws BizException if the gate is not satisfied.
     */
    public void requireLockedVersion(Long projectId, Long versionId) {
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (!"locked".equals(project.getContentStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先锁定一个审核通过的内容版本");
        }
        ContentVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "版本不存在");
        }
        if (!version.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "版本不属于该项目");
        }
        if (!"locked".equals(version.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "版本未锁定，无法进入生产");
        }
    }
}
