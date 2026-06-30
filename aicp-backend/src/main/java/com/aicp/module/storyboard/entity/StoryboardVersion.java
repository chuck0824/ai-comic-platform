package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_versions")
public class StoryboardVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long storyboardId;
    private Long parentVersionId;
    private Long sourceContentVersionId;
    private String tier;
    private Integer versionNo;
    private String status;
    private Integer revision;
    private Integer schemaVersion;
    private Integer totalScenes;
    private Integer totalShots;
    private Long totalDurationMs;
    private String createdFrom;
    private Long lockedBy;
    private LocalDateTime lockedAt;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
