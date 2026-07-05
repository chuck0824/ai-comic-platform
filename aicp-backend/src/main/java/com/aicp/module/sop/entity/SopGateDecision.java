package com.aicp.module.sop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sop_gate_decisions")
public class SopGateDecision {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long runId;
    private String gateType;
    private Integer allowed;
    private Integer blockerCount;
    private String idempotencyKey;

    @Version
    private Integer rowVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
