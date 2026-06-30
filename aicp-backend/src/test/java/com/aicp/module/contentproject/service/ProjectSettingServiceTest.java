package com.aicp.module.contentproject.service;

import com.aicp.common.exception.BizException;
import com.aicp.module.contentproject.entity.ProjectSettingEntity;
import com.aicp.module.contentproject.entity.ProjectSettingVersion;
import com.aicp.module.contentproject.mapper.ProjectSettingEntityMapper;
import com.aicp.module.contentproject.mapper.ProjectSettingVersionMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectSettingService 单元测试")
class ProjectSettingServiceTest {

    @Mock ProjectSettingEntityMapper entityMapper;
    @Mock ProjectSettingVersionMapper versionMapper;
    @Mock ProjectAccessService accessService;

    @InjectMocks
    ProjectSettingService service;

    private ProjectSettingEntity sampleEntity;

    @BeforeEach
    void setUp() {
        sampleEntity = new ProjectSettingEntity();
        sampleEntity.setId(1L);
        sampleEntity.setProjectId(10L);
        sampleEntity.setSettingType("character");
        sampleEntity.setCanonicalName("林夏");
        sampleEntity.setSummary("主角");
        sampleEntity.setDetailsJson("{\"role\":\"主角\"}");
        sampleEntity.setStatus("draft");
        sampleEntity.setSourceType("manual");
        sampleEntity.setCurrentVersionNo(2);
        sampleEntity.setRevision(2);
    }

    @Nested
    @DisplayName("createSetting")
    class CreateTests {

        @Test
        @DisplayName("无效 setting_type 抛出 PARAM_INVALID")
        void invalidTypeThrows() {
            assertThatThrownBy(() -> service.createSetting(1L, 10L, Map.of("setting_type", "invalid")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("无效设定类型");
        }

        @Test
        @DisplayName("创建成功并生成初始版本")
        void createSucceedsAndCreatesVersion() {
            doAnswer(inv -> { ProjectSettingEntity e = inv.getArgument(0); e.setId(1L); return 1; })
                    .when(entityMapper).insert(any(ProjectSettingEntity.class));
            when(versionMapper.insert(any())).thenReturn(1);
            when(entityMapper.updateById(any())).thenReturn(1);

            Map<String, Object> result = service.createSetting(1L, 10L,
                    Map.of("setting_type", "character", "canonical_name", "林夏"));

            assertThat(result.get("canonical_name")).isEqualTo("林夏");
            assertThat(result.get("status")).isEqualTo("draft");
            verify(versionMapper).insert(any(ProjectSettingVersion.class));
        }
    }

    @Nested
    @DisplayName("updateSetting")
    class UpdateTests {

        @Test
        @DisplayName("revision 冲突抛出 EDIT_CONFLICT")
        void revisionConflictThrows() {
            when(entityMapper.selectById(1L)).thenReturn(sampleEntity);
            assertThatThrownBy(() -> service.updateSetting(1L, 10L, 1L,
                    Map.of("canonical_name", "新名", "revision", 99)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("已被他人修改");
        }

        @Test
        @DisplayName("更新成功并创建新版本")
        void updateSucceeds() {
            when(entityMapper.selectById(1L)).thenReturn(sampleEntity);
            when(entityMapper.updateById(any())).thenReturn(1);
            when(versionMapper.insert(any())).thenReturn(1);

            Map<String, Object> result = service.updateSetting(1L, 10L, 1L,
                    Map.of("canonical_name", "林夏V2", "revision", 2));

            assertThat(result.get("canonical_name")).isEqualTo("林夏V2");
            verify(versionMapper).insert(any(ProjectSettingVersion.class));
        }
    }

    @Nested
    @DisplayName("archiveSetting")
    class ArchiveTests {

        @Test
        @DisplayName("归档设定成功")
        void archiveSucceeds() {
            when(entityMapper.selectById(1L)).thenReturn(sampleEntity);
            when(entityMapper.updateById(any())).thenReturn(1);

            service.archiveSetting(1L, 10L, 1L);

            ArgumentCaptor<ProjectSettingEntity> captor = ArgumentCaptor.forClass(ProjectSettingEntity.class);
            verify(entityMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("archived");
        }
    }

    @Nested
    @DisplayName("restoreSetting")
    class RestoreTests {

        @Test
        @DisplayName("恢复归档设定")
        void restoreSucceeds() {
            sampleEntity.setStatus("archived");
            when(entityMapper.selectById(1L)).thenReturn(sampleEntity);
            when(entityMapper.updateById(any())).thenReturn(1);

            Map<String, Object> result = service.restoreSetting(1L, 10L, 1L);

            assertThat(result.get("status")).isEqualTo("draft");
        }
    }

    @Nested
    @DisplayName("copySetting")
    class CopyTests {

        @Test
        @DisplayName("复制创建副本")
        void copySucceeds() {
            when(entityMapper.selectById(1L)).thenReturn(sampleEntity);
            doAnswer(inv -> { ProjectSettingEntity e = inv.getArgument(0); e.setId(2L); return 1; })
                    .when(entityMapper).insert(any(ProjectSettingEntity.class));
            when(versionMapper.insert(any())).thenReturn(1);
            when(entityMapper.updateById(any())).thenReturn(1);

            Map<String, Object> result = service.copySetting(1L, 10L, 1L);

            assertThat(result.get("canonical_name")).isEqualTo("林夏（副本）");
            assertThat(result.get("status")).isEqualTo("draft");
            assertThat(result.get("source_type")).isEqualTo("manual");
        }
    }

    @Nested
    @DisplayName("listVersions")
    class VersionTests {

        @Test
        @DisplayName("列出所有版本")
        void listVersionsSucceeds() {
            when(entityMapper.selectById(1L)).thenReturn(sampleEntity);
            ProjectSettingVersion v1 = new ProjectSettingVersion();
            v1.setId(10L); v1.setEntityId(1L); v1.setVersionNo(1);
            ProjectSettingVersion v2 = new ProjectSettingVersion();
            v2.setId(11L); v2.setEntityId(1L); v2.setVersionNo(2);
            when(versionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(v2, v1));

            var versions = service.listVersions(1L, 10L, 1L);
            assertThat(versions).hasSize(2);
        }
    }
}
