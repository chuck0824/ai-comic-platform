package com.aicp.module.canvas.kernel;

import com.aicp.common.exception.BizException;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.entity.CanvasShotUnit;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.aicp.module.canvas.mapper.CanvasShotUnitMapper;
import com.aicp.module.canvas.service.CanvasKernelService;
import com.aicp.module.canvas.service.CanvasKernelService.CreateUnitRequest;
import com.aicp.module.canvas.service.CanvasKernelService.UpdateUnitRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CanvasKernelService 单元测试")
class CanvasKernelServiceTest {

    @Mock
    private CanvasProjectMapper projectMapper;
    @Mock
    private CanvasShotUnitMapper shotUnitMapper;

    private CanvasKernelService service;

    @BeforeEach
    void setUp() {
        service = new CanvasKernelService(projectMapper, shotUnitMapper);
    }

    @Nested
    @DisplayName("ShotWorkUnit 创建")
    class CreateUnit {

        @Test
        @DisplayName("正式生产镜头必须绑定分镜版本")
        void productionUnitRequiresSourceRevision() {
            when(projectMapper.selectById(anyLong())).thenReturn(productionProject());

            assertThatThrownBy(() -> service.createUnit(1L,
                    new CreateUnitRequest(null, null, 5000, 24, "16:9", 0), 7L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("正式生产镜头必须绑定分镜版本");
        }

        @Test
        @DisplayName("探索模式自动生成临时镜头ID")
        void explorationUnitGetsProvisionalShotId() {
            when(projectMapper.selectById(anyLong())).thenReturn(explorationProject());
            when(shotUnitMapper.insert(any())).thenReturn(1);

            CanvasShotUnit unit = service.createUnit(1L,
                    new CreateUnitRequest(null, null, 5000, 24, "16:9", 0), 7L);

            assertThat(unit.getMode()).isEqualTo("EXPLORATION");
            assertThat(unit.getProvisionalShotId()).startsWith("draft_shot_");
        }

        @Test
        @DisplayName("时长 ≤ 0 时拒绝创建")
        void rejectsZeroDuration() {
            when(projectMapper.selectById(anyLong())).thenReturn(explorationProject());

            assertThatThrownBy(() -> service.createUnit(1L,
                    new CreateUnitRequest(null, null, 0, 24, "16:9", 0), 7L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("时长必须大于 0");
        }

        @Test
        @DisplayName("帧率越界时拒绝创建")
        void rejectsInvalidFps() {
            when(projectMapper.selectById(anyLong())).thenReturn(explorationProject());

            assertThatThrownBy(() -> service.createUnit(1L,
                    new CreateUnitRequest(null, null, 5000, 200, "16:9", 0), 7L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("帧率必须在 1–120");
        }
    }

    @Nested
    @DisplayName("ShotWorkUnit 更新")
    class UpdateUnit {

        @Test
        @DisplayName("乐观锁版本不一致时拒绝更新")
        void staleRowVersionIsRejected() {
            CanvasShotUnit unit = existingUnit("EXPLORATION", 3);

            when(shotUnitMapper.selectById(anyLong())).thenReturn(unit);

            assertThatThrownBy(() -> service.updateUnit(1L, unit.getId(),
                    new UpdateUnitRequest(null, null, null, 30, null, null), 99, 7L))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("有效更新成功并递增版本")
        void validUpdateSucceeds() {
            CanvasShotUnit unit = existingUnit("PRODUCTION", 0);
            unit.setSourceShotId(1L);
            unit.setSourceShotRevision(3);

            when(shotUnitMapper.selectById(anyLong())).thenReturn(unit);
            when(shotUnitMapper.updateById(any())).thenReturn(1);

            CanvasShotUnit result = service.updateUnit(1L, unit.getId(),
                    new UpdateUnitRequest(null, null, null, 30, "21:9", null), 0, 7L);

            assertThat(result.getFps()).isEqualTo(30);
            assertThat(result.getAspectRatio()).isEqualTo("21:9");
        }
    }

    private static CanvasProject explorationProject() {
        CanvasProject p = new CanvasProject();
        p.setId(1L);
        p.setUuid("proj-1");
        p.setName("test");
        p.setCanvasMode("EXPLORATION");
        p.setUserId(7L);
        return p;
    }

    private static CanvasProject productionProject() {
        CanvasProject p = explorationProject();
        p.setCanvasMode("PRODUCTION");
        return p;
    }

    private static CanvasShotUnit existingUnit(String mode, int rowVersion) {
        CanvasShotUnit u = new CanvasShotUnit();
        u.setId(100L);
        u.setUuid("unit-1");
        u.setProjectId(1L);
        u.setMode(mode);
        u.setFps(24);
        u.setTargetDurationMs(5000);
        u.setAspectRatio("16:9");
        u.setRowVersion(rowVersion);
        return u;
    }
}
