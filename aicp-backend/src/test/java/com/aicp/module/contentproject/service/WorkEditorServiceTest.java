package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.module.contentproject.entity.ContentProject;
import com.aicp.module.contentproject.entity.ContentProjectProfile;
import com.aicp.module.contentproject.entity.ProjectMember;
import com.aicp.module.contentproject.entity.ProjectParameterVersion;
import com.aicp.module.contentproject.mapper.ContentProjectMapper;
import com.aicp.module.contentproject.mapper.ContentProjectProfileMapper;
import com.aicp.module.contentproject.mapper.ProjectMemberMapper;
import com.aicp.module.contentproject.mapper.ProjectParameterVersionMapper;
import com.aicp.module.script.entity.Script;
import com.aicp.module.script.mapper.ScriptMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkEditorService 单元测试")
class WorkEditorServiceTest {

    @Mock ScriptMapper scriptMapper;
    @Mock ContentProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock ContentProjectProfileMapper profileMapper;
    @Mock ProjectParameterVersionMapper parameterVersionMapper;

    @InjectMocks
    WorkEditorService service;

    private Script existingScript;

    @BeforeEach
    void setUp() {
        existingScript = new Script();
        existingScript.setId(100L);
        existingScript.setTitle("测试剧本");
        existingScript.setAuthorUserId(1L);
        existingScript.setOwnerUserId(1L);
        existingScript.setGenreTag("言情");
        existingScript.setPlotTags("[\"重生\"]");
        existingScript.setToneTags("[\"甜宠\"]");
        existingScript.setSettingTag("现代");
        existingScript.setSynopsis("这是一部测试剧本的简介。");
    }

    // ===== resolveLegacy =====

    @Test
    @DisplayName("resolveLegacy → 已有映射则直接返回已有项目")
    void resolveLegacyReturnsExistingProject() {
        when(scriptMapper.selectById(100L)).thenReturn(existingScript);

        ContentProject existing = new ContentProject();
        existing.setId(200L);
        existing.setLegacyScriptId(100L);
        when(projectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        ContentProject result = service.resolveLegacy(1L, 100L);

        assertThat(result.getId()).isEqualTo(200L);
        verify(projectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("resolveLegacy → 剧本不存在时抛出 NOT_FOUND")
    void resolveLegacyThrowsWhenScriptNotFound() {
        when(scriptMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.resolveLegacy(1L, 999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("剧本不存在");
    }

    @Test
    @DisplayName("resolveLegacy → 拒绝其他用户的剧本")
    void resolveLegacyRejectsAnotherUsersScript() {
        Script othersScript = new Script();
        othersScript.setId(100L);
        othersScript.setAuthorUserId(2L);
        othersScript.setOwnerUserId(2L);
        when(scriptMapper.selectById(100L)).thenReturn(othersScript);

        assertThatThrownBy(() -> service.resolveLegacy(1L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    @DisplayName("resolveLegacy → 无映射时创建新项目、profile、成员和参数版本")
    void resolveLegacyCreatesProjectWhenNoMappingExists() {
        when(scriptMapper.selectById(100L)).thenReturn(existingScript);
        when(projectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // MyBatis-Plus insert 后自动回填 ID
        doAnswer(inv -> {
            ContentProject p = inv.getArgument(0);
            p.setId(300L);
            return 1;
        }).when(projectMapper).insert(any(ContentProject.class));
        when(memberMapper.insert(any())).thenReturn(1);
        when(profileMapper.insert(any())).thenReturn(1);
        doAnswer(inv -> {
            ProjectParameterVersion pv = inv.getArgument(0);
            pv.setId(10L);
            return 1;
        }).when(parameterVersionMapper).insert(any(ProjectParameterVersion.class));
        when(projectMapper.updateById(any())).thenReturn(1);

        ContentProject result = service.resolveLegacy(1L, 100L);

        assertThat(result.getId()).isEqualTo(300L);
        assertThat(result.getLegacyScriptId()).isEqualTo(100L);

        // 验证项目创建
        ArgumentCaptor<ContentProject> projectCaptor = ArgumentCaptor.forClass(ContentProject.class);
        verify(projectMapper).insert(projectCaptor.capture());
        assertThat(projectCaptor.getValue().getName()).isEqualTo("测试剧本");
        assertThat(projectCaptor.getValue().getLegacyScriptId()).isEqualTo(100L);

        // 验证 profile 创建并迁移标签
        ArgumentCaptor<ContentProjectProfile> profileCaptor = ArgumentCaptor.forClass(ContentProjectProfile.class);
        verify(profileMapper).insert(profileCaptor.capture());
        ContentProjectProfile profile = profileCaptor.getValue();
        assertThat(profile.getProjectId()).isEqualTo(300L);
        assertThat(profile.getGenreTag()).isEqualTo("言情");
        assertThat(profile.getSynopsis()).isEqualTo("这是一部测试剧本的简介。");

        // 验证成员创建
        ArgumentCaptor<ProjectMember> memberCaptor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(memberMapper).insert(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo("owner");

        // 验证参数版本创建
        verify(parameterVersionMapper).insert(any(ProjectParameterVersion.class));
    }

    @Test
    @DisplayName("resolveLegacy → 已有映射但用户非成员时自动添加为 viewer")
    void resolveLegacyAddsViewerWhenNotMember() {
        when(scriptMapper.selectById(100L)).thenReturn(existingScript);

        ContentProject existing = new ContentProject();
        existing.setId(200L);
        existing.setLegacyScriptId(100L);
        when(projectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(memberMapper.insert(any())).thenReturn(1);

        ContentProject result = service.resolveLegacy(1L, 100L);

        assertThat(result.getId()).isEqualTo(200L);
        ArgumentCaptor<ProjectMember> memberCaptor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(memberMapper).insert(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo("viewer");
    }
}
