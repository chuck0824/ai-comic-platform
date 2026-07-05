package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_audit_logs")
public class ProjectAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long actorUserId;
    private String actionType;
    private Long targetVersionId;
    private String beforeStatus;
    private String afterStatus;
    private String comment;
    private String idempotencyKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
