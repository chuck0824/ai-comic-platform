package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.common.util.SecurityUtil;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.domain.ContentProjectEnums.CreationMode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Role;
import com.aicp.module.contentproject.domain.ContentProjectEnums.SourceMode;
import com.aicp.module.contentproject.dto.ContentProjectRequests.*;
import com.aicp.module.contentproject.dto.ContentProjectViews.*;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ProjectMember;
import com.aicp.module.contentproject.entity.ProjectParameterVersion;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ProjectMemberMapper;
import com.aicp.module.contentproject.mapper.ProjectParameterVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentProjectService {

    private final ContentProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectParameterVersionMapper parameterVersionMapper;
    private final ProjectAccessService accessService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    // ===== Create =====

    @Transactional
    public ProjectDetail create(Long userId, CreateProjectRequest request) {
        CreationMode mode = CreationMode.parse(request.creationMode());
        SourceMode source = SourceMode.parse(request.sourceMode());

        if (source == SourceMode.AI_MANUAL && (request.startContent() == null || request.startContent().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "AI手动模式下起始内容不能为空");
        }

        Long tenantId = resolveTenantId(userId, request.tenantType(), request.tenantId());

        ContentProject project = new ContentProject();
        project.setUuid("CP_" + UUID.randomUUID().toString().replace("-", ""));
        project.setTenantType(request.tenantType());
        project.setTenantId(tenantId);
        project.setOwnerUserId(userId);
        project.setName(request.name());
        project.setCreationMode(mode.value());
        project.setSourceMode(source.value());
        project.setStoryboardIntentStatus("not_decided");
        project.setContentStatus("draft");
        project.setProductionStatus("not_started");
        project.setMarketStatus("private");
        project.setLastStageKey("story_seed");
        project.setRevision(0);
        project.setIsDeleted(0);
        projectMapper.insert(project);

        ProjectMember owner = new ProjectMember();
        owner.setProjectId(project.getId());
        owner.setUserId(userId);
        owner.setRole(Role.OWNER.name().toLowerCase());
        memberMapper.insert(owner);

        Map<String, Object> initialParams = new LinkedHashMap<>();
        initialParams.put("start_content", request.startContent());
        initialParams.put("content_goal", request.contentGoal());
        String payloadJson = toJson(initialParams);
        String hash = sha256(payloadJson);

        ProjectParameterVersion paramV1 = new ProjectParameterVersion();
        paramV1.setProjectId(project.getId());
        paramV1.setVersionNo(1);
        paramV1.setPayloadJson(payloadJson);
        paramV1.setContentHash(hash);
        paramV1.setCreatedBy(userId);
        parameterVersionMapper.insert(paramV1);

        project.setCurrentParameterVersionId(paramV1.getId());
        projectMapper.updateById(project);

        outboxService.append("content_project.created", project.getId(), 0,
                Map.of("project_id", project.getId(), "name", project.getName()));

        return toDetail(project, List.of(owner));
    }

    // ===== Get =====

    public ProjectDetail get(Long userId, Long projectId) {
        accessService.require(projectId, userId, Action.VIEW);
        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }
        List<ProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId));
        return toDetail(project, members);
    }

    // ===== List =====

    public ProjectListResult list(Long userId, int page, int pageSize) {
        List<ProjectMember> memberships = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getUserId, userId));

        if (memberships.isEmpty()) {
            return new ProjectListResult(List.of(), page, pageSize, 0);
        }

        List<Long> projectIds = memberships.stream()
                .map(ProjectMember::getProjectId)
                .distinct()
                .toList();

        LambdaQueryWrapper<ContentProject> query = new LambdaQueryWrapper<>();
        query.in(ContentProject::getId, projectIds);
        query.eq(ContentProject::getIsDeleted, 0);
        query.orderByDesc(ContentProject::getUpdatedAt);

        Page<ContentProject> result = projectMapper.selectPage(new Page<>(page, pageSize), query);
        List<ProjectSummary> items = result.getRecords().stream()
                .map(p -> new ProjectSummary(
                        p.getId(), p.getUuid(), p.getName(),
                        p.getCreationMode(), p.getSourceMode(),
                        p.getContentStatus(), p.getProductionStatus(),
                        p.getStoryboardIntentStatus(), p.getLastStageKey(),
                        p.getRevision(), p.getUpdatedAt()))
                .toList();

        return new ProjectListResult(items, page, pageSize, result.getTotal());
    }

    // ===== Update =====

    @Transactional
    public ProjectDetail update(Long userId, Long projectId, UpdateProjectRequest request) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);

        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }

        int currentRevision = project.getRevision();
        if (request.revision() != null && !request.revision().equals(currentRevision)) {
            throw new BizException(ErrorCode.EDIT_CONFLICT);
        }

        LambdaUpdateWrapper<ContentProject> update = new LambdaUpdateWrapper<>();
        update.eq(ContentProject::getId, projectId);
        update.eq(ContentProject::getRevision, currentRevision);
        if (request.name() != null) {
            update.set(ContentProject::getName, request.name());
        }
        update.set(ContentProject::getRevision, currentRevision + 1);

        int rows = projectMapper.update(null, update);
        if (rows == 0) {
            throw new BizException(ErrorCode.EDIT_CONFLICT);
        }

        project = projectMapper.selectById(projectId);
        List<ProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId));
        return toDetail(project, members);
    }

    // ===== Resume Position =====

    @Transactional
    public ProjectDetail saveResumePosition(Long userId, Long projectId, ResumePositionRequest request) {
        accessService.require(projectId, userId, Action.EDIT_CONTENT);

        ContentProject project = projectMapper.selectById(projectId);
        if (project == null || project.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
        }

        int currentRevision = project.getRevision();
        if (request.revision() != null && !request.revision().equals(currentRevision)) {
            throw new BizException(ErrorCode.EDIT_CONFLICT);
        }

        LambdaUpdateWrapper<ContentProject> update = new LambdaUpdateWrapper<>();
        update.eq(ContentProject::getId, projectId);
        update.eq(ContentProject::getRevision, currentRevision);
        if (request.stageKey() != null) {
            update.set(ContentProject::getLastStageKey, request.stageKey());
        }
        if (request.taskKey() != null) {
            update.set(ContentProject::getLastTaskKey, request.taskKey());
        }
        if (request.contentUnitId() != null) {
            update.set(ContentProject::getLastContentUnitId, request.contentUnitId());
        }
        update.set(ContentProject::getRevision, currentRevision + 1);

        int rows = projectMapper.update(null, update);
        if (rows == 0) {
            throw new BizException(ErrorCode.EDIT_CONFLICT);
        }

        project = projectMapper.selectById(projectId);
        List<ProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId));
        return toDetail(project, members);
    }

    // ===== Members =====

    public List<MemberView> listMembers(Long userId, Long projectId) {
        accessService.require(projectId, userId, Action.VIEW);
        return memberMapper.selectList(
                        new LambdaQueryWrapper<ProjectMember>()
                                .eq(ProjectMember::getProjectId, projectId))
                .stream()
                .map(m -> new MemberView(m.getId(), m.getUserId(), m.getRole(), m.getCreatedAt()))
                .toList();
    }

    @Transactional
    public MemberView addMember(Long userId, Long projectId, CreateMemberRequest request) {
        accessService.require(projectId, userId, Action.MANAGE_MEMBERS);

        long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, request.userId()));
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该用户已是项目成员");
        }

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(request.userId());
        member.setRole(request.role());
        memberMapper.insert(member);

        return new MemberView(member.getId(), member.getUserId(), member.getRole(), member.getCreatedAt());
    }

    @Transactional
    public MemberView updateMember(Long userId, Long projectId, Long memberId, UpdateMemberRequest request) {
        accessService.require(projectId, userId, Action.MANAGE_MEMBERS);

        ProjectMember member = memberMapper.selectById(memberId);
        if (member == null || !member.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        if ("owner".equalsIgnoreCase(member.getRole()) && !"owner".equalsIgnoreCase(request.role())) {
            long ownerCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getProjectId, projectId)
                            .eq(ProjectMember::getRole, "owner"));
            if (ownerCount <= 1) {
                throw new BizException(ErrorCode.PARAM_INVALID, "不能移除最后一位项目Owner");
            }
        }

        member.setRole(request.role());
        memberMapper.updateById(member);

        return new MemberView(member.getId(), member.getUserId(), member.getRole(), member.getCreatedAt());
    }

    @Transactional
    public void removeMember(Long userId, Long projectId, Long memberId) {
        accessService.require(projectId, userId, Action.MANAGE_MEMBERS);

        ProjectMember member = memberMapper.selectById(memberId);
        if (member == null || !member.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        if ("owner".equalsIgnoreCase(member.getRole())) {
            long ownerCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getProjectId, projectId)
                            .eq(ProjectMember::getRole, "owner"));
            if (ownerCount <= 1) {
                throw new BizException(ErrorCode.PARAM_INVALID, "不能移除最后一位项目Owner");
            }
        }

        memberMapper.deleteById(memberId);
    }

    // ===== Helpers =====

    private Long resolveTenantId(Long userId, String tenantType, Long requestTenantId) {
        if ("personal".equals(tenantType)) {
            return userId;
        }
        if ("enterprise".equals(tenantType)) {
            if (requestTenantId == null || requestTenantId <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "企业项目必须提供 tenantId");
            }
            List<String> permissions = SecurityUtil.getCurrentUserPermissions();
            if (!permissions.contains("ent_admin") && !permissions.contains("dept_head")) {
                throw new BizException(ErrorCode.FORBIDDEN, "仅企业管理员或部门主管可创建企业项目");
            }
            return requestTenantId;
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "不支持的租户类型");
    }

    private ProjectDetail toDetail(ContentProject p, List<ProjectMember> members) {
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "{}";
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record ProjectListResult(List<ProjectSummary> items, int page, int pageSize, long total) {}
}
