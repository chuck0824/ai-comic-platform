package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_projects")
public class ContentProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private String tenantType;
    private Long tenantId;
    private Long ownerUserId;
    private String name;
    private String creationMode;
    private String sourceMode;
    private String storyboardIntentStatus;
    private String contentStatus;
    private String productionStatus;
    private String marketStatus;
    private String lastStageKey;
    private String lastTaskKey;
    private Long lastContentUnitId;
    private Long currentParameterVersionId;
    private Long legacyScriptId;
    private Long convertedFromProjectId;
    private Long copiedFromProjectId;
    private String lifecycleStatus;
    private Long adoptedVersionId;
    private Integer revision;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
