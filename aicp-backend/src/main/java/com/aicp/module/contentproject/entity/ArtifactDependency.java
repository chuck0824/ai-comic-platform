package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("artifact_dependencies")
public class ArtifactDependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String sourceType;
    private Long sourceVersionId;
    private String targetType;
    private Long targetVersionId;
    private String dependencyType;
    private String sourceHash;
    private String syncStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
