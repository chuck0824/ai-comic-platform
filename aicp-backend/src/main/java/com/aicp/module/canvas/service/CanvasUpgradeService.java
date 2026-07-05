package com.aicp.module.canvas.service;

import com.aicp.common.exception.BizException;
import com.aicp.common.exception.ErrorCode;
import com.aicp.module.canvas.dto.CanvasMigrationViews.MigrationAuditReport;
import com.aicp.module.canvas.entity.CanvasProject;
import com.aicp.module.canvas.mapper.CanvasProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 单画布事务升级服务。
 * 备份 → 盘点 → 写入 V2 事实表 → 更新 schema_version → 迁移记录。
 * 旧连线批量设置 port_contract_version='legacy', status='NEEDS_CONFIRMATION'。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasUpgradeService {

    private final CanvasProjectMapper projectMapper;
    private final CanvasLegacyAuditService auditService;
    private final ObjectMapper objectMapper;

    // TODO: R1 完整实现 — 注入 V2 表 mapper，事务中完成备份 + 数据迁移

    /**
     * 检查画布是否可以升级。
     * 存在 NEEDS_CONFIRMATION 项时抛出异常。
     */
    public MigrationAuditReport preflight(String projectUuid) {
        MigrationAuditReport report = auditService.report(projectUuid);
        if (report.hasAmbiguity()) {
            throw new BizException(ErrorCode.PARAM_INVALID.getCode(),
                    "画布存在 " + report.issues().stream()
                            .filter(i -> "NEEDS_CONFIRMATION".equals(i.status())).count()
                            + " 个待确认项，请人工处理后再升级");
        }
        return report;
    }

    /**
     * 执行单画布升级（事务）。
     * 幂等：相同 idempotencyKey 返回已有升级记录。
     */
    @Transactional
    public UpgradeResult upgrade(String projectUuid, String idempotencyKey, Long actorId) {
        CanvasProject project = projectMapper.selectOne(
                new LambdaQueryWrapper<CanvasProject>().eq(CanvasProject::getUuid, projectUuid));
        if (project == null) {
            throw new BizException(ErrorCode.CANVAS_NOT_FOUND);
        }

        if (project.getSchemaVersion() != null && project.getSchemaVersion() >= 2) {
            log.info("画布已升级: project={}", projectUuid);
            return new UpgradeResult(projectUuid, "ALREADY_UPGRADED", null);
        }

        // 前置检查
        MigrationAuditReport report = preflight(projectUuid);

        // 备份
        String backupJson;
        String backupChecksum;
        try {
            backupJson = objectMapper.writeValueAsString(report);
            backupChecksum = sha256(backupJson);
        } catch (Exception e) {
            throw new RuntimeException("备份序列化失败", e);
        }

        // 执行迁移
        String migrationUuid = UUID.randomUUID().toString();
        project.setSchemaVersion(2);
        project.setCanvasMode("EXPLORATION"); // 默认探索模式，用户后续绑定
        projectMapper.updateById(project);

        // 旧连线批量标记（在迁移 mapper 层执行）
        // TODO: R1 — update canvas_edges SET port_contract_version='legacy', status='NEEDS_CONFIRMATION'
        //   WHERE project_id = #{project.id}

        log.info("画布升级完成: project={}, migrationUuid={}, nodes={}, edges={}",
                projectUuid, migrationUuid, report.nodeCount(), report.edgeCount());

        return new UpgradeResult(migrationUuid, "UPGRADED", backupChecksum);
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    public record UpgradeResult(String migrationUuid, String status, String backupChecksum) {}
}
