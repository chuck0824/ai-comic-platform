package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_version_scenes")
public class StoryboardScene {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;
    private String sceneKey;
    private Integer sceneNo;
    private String title;
    private String dramaticGoal;
    private String beatDescription;
    private Long locationRefId;
    private Long durationMs;
    private String emotionLabel;
    private Integer emotionIntensity;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
