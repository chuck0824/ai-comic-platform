package com.aicp.module.enterprise.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("enterprise_approval_items")
public class EnterpriseApprovalItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private String departmentId;

    private String sourceType;    // PURCHASE, ASSET_PUBLISH, PROJECT_EXPORT
    private String sourceId;
    private Integer sourceVersion;

    private Long requesterUserId;
    private String summary;
    private Long amountCents;
    private String currency;

    private String status;        // PENDING, APPROVED, REJECTED, CANCELLED

    private String allowedActionsJson;

    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private LocalDateTime lastEventAt;

    @Version
    private Integer rowVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
