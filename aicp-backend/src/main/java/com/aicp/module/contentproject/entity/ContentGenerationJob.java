package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_generation_jobs")
public class ContentGenerationJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long projectId;
    private String jobType;
    private String targetType;
    private Long targetId;
    private String status;
    private String inputSnapshotJson;
    private String inputSnapshotHash;
    private String schemaVersion;
    private String model;
    private String promptVersion;
    private Integer estimatedCredits;
    private Integer actualCredits;
    private String errorCode;
    private Long retryOfJobId;
    private String idempotencyKey;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
