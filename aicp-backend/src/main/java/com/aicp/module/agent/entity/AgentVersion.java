package com.aicp.module.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_versions")
public class AgentVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long userAgentId;
    private Long blueprintId;
    private Integer versionNo;
    private String parametersJson;
    private String editablePrompt;
    private String examplesJson;
    private String modelPolicyJson;
    private String status;
    private String changeSummary;
    private String contentHash;
    private Long createdBy;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    @Version
    private Integer rowVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
