package com.aicp.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("trade_audit_logs")
public class TradeAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorUserId;
    private String workspaceId;
    private String action;
    private String targetType;
    private String targetId;
    private String beforeSummary;
    private String afterSummary;
    private String correlationId;
    private String clientIp;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
