package com.aicp.module.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_executions")
public class AgentExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long sessionId;
    private Long skillId;
    private String toolName;
    private String status;
    private String inputs;
    private String outputs;
    private String logs;
    private Integer durationMs;
    private Integer creditCost;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
