package com.aicp.module.sop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sop_work_orders")
public class SopWorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long runId;
    private Long resultId;
    private String ruleCode;
    private String issueFingerprint;
    private String status;
    private String severity;
    private String responsibleRole;
    private Long assigneeId;
    private String resolutionNote;
    private LocalDateTime deadline;
    private Integer activeMarker;

    @Version
    private Integer rowVersion;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
