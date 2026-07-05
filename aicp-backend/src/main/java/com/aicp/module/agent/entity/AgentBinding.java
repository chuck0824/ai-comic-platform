package com.aicp.module.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_bindings")
public class AgentBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private String scopeType;
    private String scopeId;
    private String roleType;
    private Long userAgentId;
    private Long agentVersionId;
    private Long createdBy;
    private Long updatedBy;
    @Version
    private Integer rowVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
