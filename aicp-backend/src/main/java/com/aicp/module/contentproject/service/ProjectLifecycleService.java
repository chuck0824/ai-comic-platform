package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentVersion;
import com.aicp.module.contentproject.entity.ProjectAuditLog;
import com.aicp.module.contentproject.entity.ProjectMember;
import com.aicp.module.contentproject.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectLifecycleService {

    private final ContentProjectMapper projectMapper;
    private final ContentVersionMapper versionMapper;
    private final ProjectAuditLogMapper auditMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectAccessService accessService;

    // ===== Submit Review =====

    @Transactional
    public ProjectDetail submitReview(Long userId, Long projectId, VersionActionRequest request) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        ContentProject project = requireProject(projectId);

        // Check idempotency
        if (auditKeyExists(projectId, request.idempotencyKey())) {
            return toDetail(project);
        }

        if (!"draft".equals(project.getContentStatus()) && !"needs_revision".equals(project.getContentStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有草稿或待修改状态可以提交审核");
        }

        ContentVersion version = requireVersion(request.versionId(), projectId);
        if (!"draft".equals(version.getStatus()) && !"needs_revision".equals(version.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "版本状态不允许提交审核");
        }

        updateWithOptimisticLock(projectId, project.getRevision(), w -> {
            w.set(ContentProject::getContentStatus, "reviewing");
            w.set(ContentProject::getAdoptedVersionId, request.versionId());
        });

        version.setStatus("reviewing");
        versionMapper.updateById(version);

        insertAudit(projectId, userId, "submit_review", request.versionId(),
                project.getContentStatus(), "reviewing", request.idempotencyKey(), request.comment());

        return toDetail(refresh(projectId));
    }

    // ===== Approve =====

    @Transactional
    public ProjectDetail approve(Long userId, Long projectId, VersionActionRequest request) {
        accessService.require(projectId, userId, Action.REVIEW);
        ContentProject project = requireProject(projectId);

        if (auditKeyExists(projectId, request.idempotencyKey())) {
            return toDetail(project);
        }

        if (!"reviewing".equals(project.getContentStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有审核中的项目可以批准");
        }

        ContentVersion version = requireVersion(request.versionId(), projectId);

        updateWithOptimisticLock(projectId, project.getRevision(), w -> {
            w.set(ContentProject::getContentStatus, "approved");
        });

        version.setStatus("approved");
        versionMapper.updateById(version);

        insertAudit(projectId, userId, "approve", request.versionId(),
                project.getContentStatus(), "approved", request.idempotencyKey(), request.comment());

        return toDetail(refresh(projectId));
    }

    // ===== Request Revision =====

    @Transactional
    public ProjectDetail requestRevision(Long userId, Long projectId, VersionActionRequest request) {
        accessService.require(projectId, userId, Action.REVIEW);
        ContentProject project = requireProject(projectId);

        if (auditKeyExists(projectId, request.idempotencyKey())) {
            return toDetail(project);
        }

        if (!"reviewing".equals(project.getContentStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有审核中的项目可以驳回");
        }

        ContentVersion version = requireVersion(request.versionId(), projectId);

        updateWithOptimisticLock(projectId, project.getRevision(), w -> {
            w.set(ContentProject::getContentStatus, "needs_revision");
        });

        version.setStatus("needs_revision");
        versionMapper.updateById(version);

        insertAudit(projectId, userId, "request_revision", request.versionId(),
                project.getContentStatus(), "needs_revision", request.idempotencyKey(), request.comment());

        return toDetail(refresh(projectId));
    }

    // ===== Lock =====

    @Transactional
    public ProjectDetail lock(Long userId, Long projectId, VersionActionRequest request) {
        accessService.require(projectId, userId, Action.REVIEW);
        ContentProject project = requireProject(projectId);

        if (auditKeyExists(projectId, request.idempotencyKey())) {
            return toDetail(project);
        }

        if (!"approved".equals(project.getContentStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有审核通过的项目可以锁稿");
        }

        ContentVersion version = requireVersion(request.versionId(), projectId);
        if (!"approved".equals(version.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "版本未审核通过，无法锁稿");
        }

        updateWithOptimisticLock(projectId, project.getRevision(), w -> {
            w.set(ContentProject::getContentStatus, "locked");
            w.set(ContentProject::getAdoptedVersionId, request.versionId());
        });

        version.setStatus("locked");
        versionMapper.updateById(version);

        insertAudit(projectId, userId, "lock", request.versionId(),
                project.getContentStatus(), "locked", request.idempotencyKey(), request.comment());

        return toDetail(refresh(projectId));
    }

    // ===== Archive / Restore =====

    @Transactional
    public ProjectDetail archive(Long userId, Long projectId, ProjectActionRequest request) {
        accessService.require(projectId, userId, Action.DELETE_PROJECT);
        ContentProject project = requireProject(projectId);

        if (auditKeyExists(projectId, request.idempotencyKey())) {
            return toDetail(project);
        }

        if ("archived".equals(project.getLifecycleStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "项目已归档");
        }
        if ("listed".equals(project.getMarketStatus()) || "sold".equals(project.getMarketStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已上架项目不得直接归档；请先下架");
        }

        updateWithOptimisticLock(projectId, project.getRevision(), w -> {
            w.set(ContentProject::getLifecycleStatus, "archived");
        });

        insertAudit(projectId, userId, "archive", null,
                project.getContentStatus(), project.getContentStatus(),
                request.idempotencyKey(), request.comment());

        return toDetail(refresh(projectId));
    }

    @Transactional
    public ProjectDetail restore(Long userId, Long projectId, ProjectActionRequest request) {
        accessService.require(projectId, userId, Action.DELETE_PROJECT);
        ContentProject project = requireProject(projectId);

        if (auditKeyExists(projectId, request.idempotencyKey())) {
            return toDetail(project);
        }

        if (!"archived".equals(project.getLifecycleStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有已归档项目可以恢复");
        }

        updateWithOptimisticLock(projectId, project.getRevision(), w -> {
            w.set(ContentProject::getLifecycleStatus, "active");
        });

        insertAudit(projectId, userId, "restore", null,
                project.getContentStatus(), project.getContentStatus(),
                request.idempotencyKey(), request.comment());

        return toDetail(refresh(projectId));
    }

    // ===== Duplicate =====

    @Transactional
    public ProjectDetail duplicate(Long userId, Long projectId, ProjectActionRequest request) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);
        ContentProject project = requireProject(projectId);

        if (auditKeyExists(projectId, request.idempotencyKey())) {
            return toDetail(project);
        }

        ContentProject copy = new ContentProject();
        copy.setUuid("CP_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        copy.setTenantType(project.getTenantType());
        copy.setTenantId(project.getTenantId());
        copy.setOwnerUserId(userId);
        copy.setName(project.getName() + " (副本)");
        copy.setCreationMode(project.getCreationMode());
        copy.setSourceMode(project.getSourceMode());
        copy.setStoryboardIntentStatus("not_decided");
        copy.setContentStatus("draft");
        copy.setProductionStatus("not_started");
        copy.setMarketStatus("private");
        copy.setLifecycleStatus("active");
        copy.setCopiedFromProjectId(projectId);
        copy.setRevision(0);
        copy.setIsDeleted(0);
        projectMapper.insert(copy);

        // Copy owner membership
        ProjectMember member = new ProjectMember();
        member.setProjectId(copy.getId());
        member.setUserId(userId);
        member.setRole("owner");
        memberMapper.insert(member);

        insertAudit(projectId, userId, "duplicate", null,
                project.getContentStatus(), project.getContentStatus(),
                request.idempotencyKey(), request.comment());

        return toDetail(copy);
    }

    // ===== Move to Trash (soft-delete) =====

    @Transactional
    public void moveToTrash(Long userId, Long projectId, ProjectActionRequest request) {
        accessService.require(projectId, userId, Action.DELETE_PROJECT);
        ContentProject project = requireProject(projectId);

        if (auditKeyExists(projectId, request.idempotencyKey())) {
            return;
        }

        if (!"archived".equals(project.getLifecycleStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能将已归档项目移入回收站");
        }
        if ("listed".equals(project.getMarketStatus()) || "sold".equals(project.getMarketStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已上架项目不得直接移入回收站");
        }

        projectMapper.deleteById(projectId); // MyBatis-Plus soft-delete

        insertAudit(projectId, userId, "move_to_trash", null,
                project.getContentStatus(), project.getContentStatus(),
                request.idempotencyKey(), request.comment());
    }

    // ===== Helpers =====

    private ContentProject requireProject(Long projectId) {
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return project;
    }

    private ContentVersion requireVersion(Long versionId, Long projectId) {
        if (versionId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "必须指定版本 ID");
        }
        ContentVersion version = versionMapper.selectById(versionId);
        if (version == null || !version.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "版本不存在或不属于该项目");
        }
        return version;
    }

    private void updateWithOptimisticLock(Long projectId, int expectedRevision,
                                          java.util.function.Consumer<LambdaUpdateWrapper<ContentProject>> setters) {
        LambdaUpdateWrapper<ContentProject> update = new LambdaUpdateWrapper<>();
        update.eq(ContentProject::getId, projectId);
        update.eq(ContentProject::getRevision, expectedRevision);
        setters.accept(update);
        update.set(ContentProject::getRevision, expectedRevision + 1);
        int rows = projectMapper.update(null, update);
        if (rows == 0) {
            throw new BizException(ErrorCode.EDIT_CONFLICT, "并发冲突，请刷新后重试");
        }
    }

    private ContentProject refresh(Long projectId) {
        return projectMapper.selectById(projectId);
    }

    private void insertAudit(Long projectId, Long userId, String actionType,
                             Long versionId, String before, String after,
                             String idempotencyKey, String comment) {
        ProjectAuditLog audit = new ProjectAuditLog();
        audit.setProjectId(projectId);
        audit.setActorUserId(userId);
        audit.setActionType(actionType);
        audit.setTargetVersionId(versionId);
        audit.setBeforeStatus(before);
        audit.setAfterStatus(after);
        audit.setIdempotencyKey(idempotencyKey);
        audit.setComment(comment);
        try {
            auditMapper.insert(audit);
        } catch (Exception e) {
            // Duplicate idempotency key — ignore, caller checks first
            log.warn("Audit insert conflict for key: {}", idempotencyKey);
        }
    }

    private boolean auditKeyExists(Long projectId, String idempotencyKey) {
        return auditMapper.selectCount(
                new LambdaQueryWrapper<ProjectAuditLog>()
                        .eq(ProjectAuditLog::getProjectId, projectId)
                        .eq(ProjectAuditLog::getIdempotencyKey, idempotencyKey)) > 0;
    }

    private ProjectDetail toDetail(ContentProject p) {
        List<ProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, p.getId()));
        List<MemberView> memberViews = members.stream()
                .map(m -> new MemberView(m.getId(), m.getUserId(), m.getRole(), m.getCreatedAt()))
                .toList();
        return new ProjectDetail(
                p.getId(), p.getUuid(), p.getTenantType(), p.getTenantId(),
                p.getOwnerUserId(), p.getName(), p.getCreationMode(), p.getSourceMode(),
                p.getStoryboardIntentStatus(), p.getContentStatus(), p.getProductionStatus(),
                p.getMarketStatus(), p.getLastStageKey(), p.getLastTaskKey(),
                p.getLastContentUnitId(), p.getCurrentParameterVersionId(),
                p.getLegacyScriptId(), p.getRevision(),
                p.getCreatedAt(), p.getUpdatedAt(), memberViews);
    }
}
