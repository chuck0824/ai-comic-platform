package com.aicp.module.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_agent_definitions")
public class UserAgentDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long blueprintId;
    private Long ownerUserId;
    private Long currentPublishedVersionId;
    private String name;
    private String description;
    private String icon;
    private String applicableGenresJson;
    private String platformsJson;
    private String visibility;
    private String lifecycleStatus;
    @Version
    private Integer rowVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
