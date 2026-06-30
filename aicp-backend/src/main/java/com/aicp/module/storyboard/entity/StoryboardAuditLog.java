package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_audit_logs")
public class StoryboardAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;
    private Long actorUserId;
    private String actionType;
    private String targetType;
    private Long targetId;
    private String operationId;
    private String beforeJson;
    private String afterJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
