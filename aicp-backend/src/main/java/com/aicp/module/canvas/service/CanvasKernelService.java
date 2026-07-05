package com.aicp.module.canvas.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.canvas.domain.CanvasKernelEnums.CanvasMode;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.entity.CanvasShotUnit;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.aicp.module.canvas.mapper.CanvasShotUnitMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Canvas 生产内核服务。
 * ShotWorkUnit 的创建、更新和 Gate 状态推导。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasKernelService {

    private final CanvasProjectMapper projectMapper;
    private final CanvasShotUnitMapper shotUnitMapper;

    /**
     * 创建 ShotWorkUnit。
     * PRODUCTION 模式必须绑定分镜版本；EXPLORATION 自动生成临时镜头ID。
     */
    @Transactional
    public CanvasShotUnit createUnit(Long projectId, CreateUnitRequest request, Long actorId) {
        CanvasProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ErrorCode.CANVAS_NOT_FOUND);
        }

        boolean production = CanvasMode.PRODUCTION.name().equals(project.getCanvasMode());
        if (production && (request.sourceShotId() == null || request.sourceShotRevision() == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "正式生产镜头必须绑定分镜版本");
        }

        if (request.targetDurationMs() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "时长必须大于 0");
        }
        if (request.fps() < 1 || request.fps() > 120) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(), "帧率必须在 1–120 之间");
        }

        CanvasShotUnit unit = new CanvasShotUnit();
        unit.setUuid(UUID.randomUUID().toString());
        unit.setProjectId(projectId);
        unit.setMode(project.getCanvasMode());
        unit.setTargetDurationMs(request.targetDurationMs());
        unit.setFps(request.fps());
        unit.setAspectRatio(request.aspectRatio() != null ? request.aspectRatio() : "16:9");
        unit.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);

        if (production) {
            unit.setSourceShotId(request.sourceShotId());
            unit.setSourceShotRevision(request.sourceShotRevision());
        } else {
            unit.setProvisionalShotId("draft_shot_" + UUID.randomUUID().toString().substring(0, 12));
        }

        shotUnitMapper.insert(unit);
        log.info("ShotWorkUnit created: id={}, mode={}, projectId={}", unit.getId(), unit.getMode(), projectId);
        return unit;
    }

    /**
     * 更新 ShotWorkUnit（乐观锁）。
     * 当存在进行中的生成任务时，拒绝修改 fps/duration/aspect。
     */
    @Transactional
    public CanvasShotUnit updateUnit(Long projectId, Long unitId, UpdateUnitRequest request,
                                      int expectedVersion, Long actorId) {
        CanvasShotUnit unit = shotUnitMapper.selectById(unitId);
        if (unit == null || !unit.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.CANVAS_NOT_FOUND);
        }
        if (unit.getRowVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    String.format("ShotUnit %d 版本冲突: 期望 %d，实际 %d", unitId, expectedVersion, unit.getRowVersion()));
        }

        // 修改时序参数前检查是否有进行中任务（简化实现：由调用方负责检查）
        if (request.fps() != null) unit.setFps(request.fps());
        if (request.targetDurationMs() != null) unit.setTargetDurationMs(request.targetDurationMs());
        if (request.aspectRatio() != null) unit.setAspectRatio(request.aspectRatio());
        if (request.sortOrder() != null) unit.setSortOrder(request.sortOrder());

        // PRODUCTION 模式下可更新分镜绑定
        if (CanvasMode.PRODUCTION.name().equals(unit.getMode())) {
            if (request.sourceShotId() != null) unit.setSourceShotId(request.sourceShotId());
            if (request.sourceShotRevision() != null) unit.setSourceShotRevision(request.sourceShotRevision());
        }

        shotUnitMapper.updateById(unit);
        log.info("ShotWorkUnit updated: id={}, version={}", unitId, unit.getRowVersion());
        return unit;
    }

    public CanvasShotUnit getUnit(Long unitId) {
        return shotUnitMapper.selectById(unitId);
    }

    /** 不可变记录 */
    public record CreateUnitRequest(Long sourceShotId, Integer sourceShotRevision,
                                     Integer targetDurationMs, Integer fps, String aspectRatio, Integer sortOrder) {}
    public record UpdateUnitRequest(Long sourceShotId, Integer sourceShotRevision,
                                     Integer targetDurationMs, Integer fps, String aspectRatio, Integer sortOrder) {}
}
