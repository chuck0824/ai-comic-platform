package com.aicp.module.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_execution_snapshots")
public class AgentExecutionSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long blueprintId;
    private Integer blueprintVersion;
    private Long userAgentId;
    private Long agentVersionId;
    private String bindingSource;
    private String resolvedParametersJson;
    private String temporaryOverridesJson;
    private String resolvedPrompt;
    private String promptHash;
    private String outputSchemaVersion;
    private Long projectId;
    private String contextHash;
    private String contextRefsJson;
    private String businessTaskType;
    private String businessTaskId;
    private String modelId;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
