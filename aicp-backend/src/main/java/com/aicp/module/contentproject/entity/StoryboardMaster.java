package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cp_storyboard_masters")
public class StoryboardMaster {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long projectId;
    private Long contentUnitId;
    private String tier;
    private String status;
    private Integer totalShots;
    private Integer estimatedDurationSec;
    private Long sourceVersionId;
    private Long lockedBy;
    private LocalDateTime lockedAt;
    private Integer revision;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
