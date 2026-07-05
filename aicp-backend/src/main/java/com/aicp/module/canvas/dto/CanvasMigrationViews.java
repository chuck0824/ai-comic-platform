package com.aicp.module.canvas.dto;

import java.util.List;

/**
 * R0 只读审计视图。
 * 盘点旧画布节点和连线形状，不执行任何写操作。
 * R1 持久化实体为 {@code CanvasMigrationRecord}，与此视图不同。
 */
public final class CanvasMigrationViews {

    private CanvasMigrationViews() {}

    /**
     * 盘点发现的单个问题或分类结果。
     *
     * @param objectId      节点/连线的 uuid 或 id
     * @param objectType    对象类型：NODE | EDGE
     * @param currentType   当前记录的 type 字段值
     * @param suggestedType 建议的新类型，无建议则为 null
     * @param status        分类状态：AUTO_CLASSIFIED | NEEDS_CONFIRMATION | LEGACY_UNMODIFIED | UNKNOWN
     * @param reason        可读的原因说明
     */
    public record MigrationAuditIssue(
            String objectId,
            String objectType,
            String currentType,
            String suggestedType,
            String status,
            String reason
    ) {}

    /**
     * 单个画布项目的完整审计报告。
     */
    public record MigrationAuditReport(
            String projectUuid,
            int nodeCount,
            int edgeCount,
            List<MigrationAuditIssue> issues
    ) {
        /** 是否存在需要人工确认的歧义项 */
        public boolean hasAmbiguity() {
            return issues != null && issues.stream()
                    .anyMatch(i -> "NEEDS_CONFIRMATION".equals(i.status()));
        }

        /** 是否存在可自动分类的项 */
        public long autoClassifiedCount() {
            return issues == null ? 0
                    : issues.stream().filter(i -> "AUTO_CLASSIFIED".equals(i.status())).count();
        }
    }
}
