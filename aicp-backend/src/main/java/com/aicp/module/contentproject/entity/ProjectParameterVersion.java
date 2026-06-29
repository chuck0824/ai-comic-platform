package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_parameter_versions")
public class ProjectParameterVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Integer versionNo;
    private String payloadJson;
    private String contentHash;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
