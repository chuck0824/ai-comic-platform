package com.aicp.module.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_messages")
public class AgentMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String toolCalls;
    private String toolResults;
    private Double confidence;
    private Integer tokensUsed;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
