package com.aicp.module.enterprise.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("enterprise_audit_index")
public class EnterpriseAuditIndex {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private String departmentId;
    private Long actorUserId;
    private String action;
    private String objectType;
    private String objectId;
    private String result;
    private String sourceDomain;
    private String sourceRecordId;
    private String requestId;
    private String redactedSummary;
    private String eventId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
