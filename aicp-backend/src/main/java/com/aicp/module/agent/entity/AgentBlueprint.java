package com.aicp.module.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_blueprints")
public class AgentBlueprint {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private String roleType;
    private String name;
    private String description;
    private String parameterSchemaJson;
    private String defaultParametersJson;
    private String lockedSystemPrompt;
    private String editablePromptTemplate;
    private String inputSchemaJson;
    private String outputSchemaJson;
    private String allowedToolsJson;
    private String contextPolicyJson;
    private String modelPolicyJson;
    private Integer blueprintVersion;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
