package com.aicp.module.sop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sop_check_results")
public class SopCheckResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;
    private String ruleCode;
    private String result;
    private String severity;
    private Integer critical;
    private String targetType;
    private String targetId;
    private String issueFingerprint;
    private String evidenceJson;
    private String suggestion;
    private String fixPolicy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
