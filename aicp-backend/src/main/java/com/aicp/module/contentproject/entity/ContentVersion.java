package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_versions")
public class ContentVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long contentUnitId;
    private Integer versionNo;
    private String status;
    private String contentJson;
    private String plainText;
    private String source;
    private Long generationJobId;
    private String contentHash;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
