package com.aicp.module.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_sessions")
public class AgentSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long userId;
    private Long projectId;
    private String title;
    private String agentConfig;
    private String status;
    private Integer estimatedSeconds;
    private Integer totalCreditCost;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
