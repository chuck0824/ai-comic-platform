package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_units")
public class ContentUnit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String stableKey;
    private Long projectId;
    private String unitType;
    private Integer displayNo;
    private String title;
    private String status;
    private Long currentVersionId;
    private Integer revision;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
