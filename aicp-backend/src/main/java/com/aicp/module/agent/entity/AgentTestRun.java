package com.aicp.module.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_test_runs")
public class AgentTestRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long agentVersionId;
    private String inputSnapshotJson;
    private String contextSnapshotJson;
    private String outputJson;
    private Boolean outputSchemaValid;
    private String modelId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Double creditCost;
    private Integer durationMs;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
