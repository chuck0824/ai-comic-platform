package com.aicp.module.canvas.domain;

/**
 * Canvas 生产内核枚举。
 */
public final class CanvasKernelEnums {

    private CanvasKernelEnums() {}

    /** 画布模式 */
    public enum CanvasMode {
        EXPLORATION,
        PRODUCTION
    }

    /** 迁移状态 */
    public enum MigrationStatus {
        NOT_AUDITED,
        AUTO_READY,
        NEEDS_CONFIRMATION,
        UPGRADED,
        FAILED,
        ROLLED_BACK
    }

    /** 连线状态 */
    public enum EdgeStatus {
        ACTIVE,
        NEEDS_CONFIRMATION,
        LEGACY
    }

    /** 端口方向 */
    public enum PortDirection {
        INPUT,
        OUTPUT
    }

    /** 生成优先级 */
    public enum GenerationPriority {
        P0_HIGH,
        P1_NORMAL,
        P2_LOW,
        P3_BATCH
    }

    /** 安全审核状态 */
    public enum SafetyStatus {
        PENDING,
        PASS,
        FLAGGED,
        REJECTED
    }

    /** 候选 Gate 状态 */
    public enum ShotGate {
        INPUT_READY,
        COST_CONFIRMED,
        GENERATED,
        QUALITY_COMPLETE,
        ADOPTED
    }
}
