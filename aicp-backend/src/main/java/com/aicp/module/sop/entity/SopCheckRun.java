package com.aicp.module.sop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sop_check_runs")
public class SopCheckRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long contentUnitId;
    private Long canvasProjectId;
    private String gateType;
    private String triggerType;
    private String ruleSetVersion;
    private String scopeHash;
    private String snapshotHash;
    private String sourceRevisionsJson;
    private String status;
    private String overallStatus;
    private Integer passedCount;
    private Integer warningCount;
    private Integer blockedCount;
    private Integer notReadyCount;
    private Integer errorCount;

    @Version
    private Integer rowVersion;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
