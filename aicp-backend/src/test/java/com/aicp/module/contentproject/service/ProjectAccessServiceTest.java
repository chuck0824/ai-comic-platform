package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.entity.ProjectMember;
import com.aicp.module.contentproject.mapper.ProjectMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectAccessService 单元测试")
class ProjectAccessServiceTest {

    @Mock ProjectMemberMapper memberMapper;
    @InjectMocks ProjectAccessService service;

    private ProjectMember owner(Long userId) {
        ProjectMember m = new ProjectMember();
        m.setProjectId(1L);
        m.setUserId(userId);
        m.setRole("owner");
        return m;
    }

    private ProjectMember viewer(Long userId) {
        ProjectMember m = new ProjectMember();
        m.setProjectId(1L);
        m.setUserId(userId);
        m.setRole("viewer");
        return m;
    }

    @Test
    @DisplayName("Owner 可执行所有操作")
    void ownerCanPerformEveryAction() {
        when(memberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(owner(7L)));

        assertThatCode(() -> service.require(1L, 7L, Action.DELETE_PROJECT)).doesNotThrowAnyException();
        assertThatCode(() -> service.require(1L, 7L, Action.EDIT_CONTENT)).doesNotThrowAnyException();
        assertThatCode(() -> service.require(1L, 7L, Action.MANAGE_MEMBERS)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Viewer 仅可查看，编辑内容被拒绝")
    void viewerCannotEdit() {
        when(memberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(viewer(8L)));

        assertThatCode(() -> service.require(1L, 8L, Action.VIEW)).doesNotThrowAnyException();

        assertThatThrownBy(() -> service.require(1L, 8L, Action.EDIT_CONTENT))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED.getCode());
    }

    @Test
    @DisplayName("非成员被拒绝访问")
    void nonMemberDenied() {
        when(memberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.require(1L, 99L, Action.VIEW))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED.getCode());
    }
}
