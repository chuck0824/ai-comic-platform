package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Role;
import com.aicp.module.contentproject.entity.ProjectMember;
import com.aicp.module.contentproject.mapper.ProjectMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectAccessService {

    private final ProjectMemberMapper memberMapper;

    public ProjectMember require(Long projectId, Long userId, Action action) {
        List<ProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId));
        if (members.isEmpty()) {
            throw new BizException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        ProjectMember member = members.get(0);
        Role role;
        try {
            role = Role.valueOf(member.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        if (!role.allows(action)) {
            throw new BizException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        return member;
    }

    public boolean isMember(Long projectId, Long userId) {
        return memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)) > 0;
    }
}
