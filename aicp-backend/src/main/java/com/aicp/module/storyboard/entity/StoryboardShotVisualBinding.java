package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_shot_visual_bindings")
public class StoryboardShotVisualBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;
    private Long shotId;
    private Long characterVisualId;
    private String applicationNote;
    private String antiDriftRequirement;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
