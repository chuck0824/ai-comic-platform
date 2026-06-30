package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboards")
public class Storyboard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long projectId;
    private Long contentUnitId;
    private Long sourceContentVersionId;
    private String title;
    private String purpose;
    private Long currentDraftVersionId;
    private Long currentLockedVersionId;
    private String productionStatus;
    private Long createdBy;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
