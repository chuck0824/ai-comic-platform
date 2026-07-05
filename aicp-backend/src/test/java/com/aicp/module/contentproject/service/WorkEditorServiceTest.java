package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.contentproject.domain.ContentProjectEnums.Action;
import com.aicp.module.contentproject.dto.WorkEditorRequests.*;
import com.aicp.module.contentproject.entity.*;
import com.aicp.module.contentproject.mapper.*;
import com.aicp.module.script.entity.Script;
import com.aicp.module.script.mapper.ScriptMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
    @Mock TagDictionaryMapper tagDictionaryMapper;
    @Mock ProjectSettingEntityMapper settingEntityMapper;
    @Mock SettingExtractionBatchMapper extractionBatchMapper;
    @Mock CreativeBibleService creativeBibleService;
    @Mock ProjectAccessService accessService;

    @InjectMocks
    WorkEditorService service;

    private Script existingScript;
    private ContentProject existingProject;
    private ContentProjectProfile existingProfile;

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

        existingProject = new ContentProject();
        existingProject.setId(200L);
        existingProject.setName("测试剧本");
        existingProject.setLegacyScriptId(100L);
        existingProject.setRevision(3);
        existingProject.setIsDeleted(0);

        existingProfile = new ContentProjectProfile();
        existingProfile.setId(10L);
        existingProfile.setProjectId(200L);
        existingProfile.setGenreTag("言情");
        existingProfile.setPlotTags("[\"重生\"]");
        existingProfile.setToneTags("[\"甜宠\"]");
        existingProfile.setSettingTag("现代");
        existingProfile.setSynopsis("简介内容");
        existingProfile.setOutline(null);
        existingProfile.setRevision(3);
    }

    // ==================== resolveLegacy ====================

    @Nested
    @DisplayName("resolveLegacy")
    class ResolveLegacyTests {

        @Test
        @DisplayName("已有映射则直接返回已有项目")
        void returnsExistingProject() {
            when(scriptMapper.selectById(100L)).thenReturn(existingScript);
            when(projectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingProject);
            when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            ContentProject result = service.resolveLegacy(1L, 100L);
            assertThat(result.getId()).isEqualTo(200L);
            verify(projectMapper, never()).insert(any());
        }

        @Test
        @DisplayName("剧本不存在时抛出 NOT_FOUND")
        void throwsWhenScriptNotFound() {
            when(scriptMapper.selectById(999L)).thenReturn(null);
            assertThatThrownBy(() -> service.resolveLegacy(1L, 999L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("剧本不存在");
        }

        @Test
        @DisplayName("拒绝其他用户的剧本")
        void rejectsAnotherUsersScript() {
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
        @DisplayName("无映射时创建新项目、profile、成员和参数版本")
        void createsProjectWhenNoMappingExists() {
            when(scriptMapper.selectById(100L)).thenReturn(existingScript);
            when(projectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            doAnswer(inv -> { ContentProject p = inv.getArgument(0); p.setId(300L); return 1; })
                    .when(projectMapper).insert(any(ContentProject.class));
            when(memberMapper.insert(any())).thenReturn(1);
            when(profileMapper.insert(any())).thenReturn(1);
            doAnswer(inv -> { ProjectParameterVersion pv = inv.getArgument(0); pv.setId(10L); return 1; })
                    .when(parameterVersionMapper).insert(any(ProjectParameterVersion.class));
            when(projectMapper.updateById(any())).thenReturn(1);

            ContentProject result = service.resolveLegacy(1L, 100L);
            assertThat(result.getId()).isEqualTo(300L);

            ArgumentCaptor<ContentProjectProfile> profileCaptor = ArgumentCaptor.forClass(ContentProjectProfile.class);
            verify(profileMapper).insert(profileCaptor.capture());
            assertThat(profileCaptor.getValue().getGenreTag()).isEqualTo("言情");
        }
    }

    // ==================== updateTags ====================

    @Nested
    @DisplayName("updateTags")
    class UpdateTagsTests {

        @BeforeEach
        void setUpTags() {
            // mock dictionary with valid tags (lenient because some tests return early)
            TagDictionary genre = new TagDictionary();
            genre.setAxis("genre"); genre.setTagValue("言情");
            TagDictionary plot1 = new TagDictionary();
            plot1.setAxis("plot"); plot1.setTagValue("重生");
            TagDictionary plot2 = new TagDictionary();
            plot2.setAxis("plot"); plot2.setTagValue("先婚后爱");
            TagDictionary plot3 = new TagDictionary();
            plot3.setAxis("plot"); plot3.setTagValue("逆袭");
            TagDictionary plot4 = new TagDictionary();
            plot4.setAxis("plot"); plot4.setTagValue("复仇");
            TagDictionary tone1 = new TagDictionary();
            tone1.setAxis("tone"); tone1.setTagValue("甜宠");
            TagDictionary tone2 = new TagDictionary();
            tone2.setAxis("tone"); tone2.setTagValue("爽文");
            TagDictionary setting = new TagDictionary();
            setting.setAxis("setting"); setting.setTagValue("现代");
            lenient().when(tagDictionaryMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(genre, plot1, plot2, plot3, plot4, tone1, tone2, setting));
        }

        @Test
        @DisplayName("合法标签更新成功")
        void validTagsUpdateSucceeds() {
            when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingProfile);
            when(profileMapper.updateById(any())).thenReturn(1);

            UpdateTagsRequest req = new UpdateTagsRequest("言情", List.of("重生"), List.of("甜宠", "爽文"), "现代", 3);
            var result = service.updateTags(1L, 200L, req);

            assertThat(result.revision()).isEqualTo(4);
        }

        @Test
        @DisplayName("无效题材抛出 PARAM_INVALID")
        void invalidGenreThrows() {
            when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingProfile);
            UpdateTagsRequest req = new UpdateTagsRequest("科幻", List.of(), List.of(), "现代", 3);

            assertThatThrownBy(() -> service.updateTags(1L, 200L, req))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("无效题材");
        }

        @Test
        @DisplayName("情节超过 3 个抛出 PARAM_INVALID")
        void tooManyPlotsThrows() {
            when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingProfile);
            UpdateTagsRequest req = new UpdateTagsRequest("言情",
                    List.of("重生", "先婚后爱", "逆袭", "复仇"), List.of(), "现代", 3);

            assertThatThrownBy(() -> service.updateTags(1L, 200L, req))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("最多 3 个");
        }

        @Test
        @DisplayName("revision 不匹配抛出 EDIT_CONFLICT")
        void revisionConflictThrows() {
            when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingProfile);
            UpdateTagsRequest req = new UpdateTagsRequest("言情", List.of(), List.of(), "现代", 99);

            assertThatThrownBy(() -> service.updateTags(1L, 200L, req))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("已被他人修改");
        }

        @Test
        @DisplayName("profile 不存在抛出 NOT_FOUND")
        void profileNotFoundThrows() {
            when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            UpdateTagsRequest req = new UpdateTagsRequest("言情", List.of(), List.of(), "现代", 0);

            assertThatThrownBy(() -> service.updateTags(1L, 200L, req))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("项目资料不存在");
        }
    }

    // ==================== updateProfile ====================

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfileTests {

        @Test
        @DisplayName("更新简介和总纲成功")
        void updateSynopsisAndOutlineSucceeds() {
            when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingProfile);
            when(profileMapper.updateById(any())).thenReturn(1);

            UpdateProfileRequest req = new UpdateProfileRequest("新简介", "新总纲", 3);
            var result = service.updateProfile(1L, 200L, req);

            assertThat(result.synopsis()).isEqualTo("新简介");
            assertThat(result.outline()).isEqualTo("新总纲");
            assertThat(result.revision()).isEqualTo(4);
        }

        @Test
        @DisplayName("revision 冲突抛出 EDIT_CONFLICT")
        void revisionConflictThrows() {
            when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingProfile);
            UpdateProfileRequest req = new UpdateProfileRequest("x", null, 5);

            assertThatThrownBy(() -> service.updateProfile(1L, 200L, req))
                    .isInstanceOf(BizException.class);
        }
    }

    // ==================== getEditor ====================

    @Nested
    @DisplayName("getEditor")
    class GetEditorTests {

        @Test
        @DisplayName("返回编辑器聚合视图")
        void returnsEditorView() {
            when(projectMapper.selectById(200L)).thenReturn(existingProject);
            when(profileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingProfile);
            ProjectMember member = new ProjectMember();
            member.setProjectId(200L);
            member.setUserId(1L);
            member.setRole("owner");
            when(memberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member));

            var result = service.getEditor(1L, 200L);

            assertThat(result.projectId()).isEqualTo(200L);
            assertThat(result.title()).isEqualTo("测试剧本");
            assertThat(result.permissions()).isEqualTo("owner");
            assertThat(result.profile()).isNotNull();
            assertThat(result.profile().genreTag()).isEqualTo("言情");
            assertThat(result.settingCounts()).containsKeys("character", "background", "faction", "location", "item");
        }
    }
}
