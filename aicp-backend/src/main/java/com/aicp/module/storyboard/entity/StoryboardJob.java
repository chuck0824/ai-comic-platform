package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_jobs")
public class StoryboardJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long projectId;
    private Long storyboardId;
    private Long versionId;
    private String jobType;
    private String status;
    private String idempotencyKey;
    private Integer progressPercent;
    private String currentStage;
    private String requestJson;
    private String resultJson;
    private String errorCode;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
