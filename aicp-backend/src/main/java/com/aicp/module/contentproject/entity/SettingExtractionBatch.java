package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("setting_extraction_batches")
public class SettingExtractionBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long sourceVersionId;
    private String chapterVersionIdsJson;
    private String targetSettingTypes;  // JSON array
    private String idempotencyKey;
    private String status;             // queued, running, review_ready, partially_failed, failed, applied, cancelled
    private String modelId;
    private String promptVersion;
    private String extractionConfigJson;
    private String errorMessage;
    private LocalDateTime appliedAt;
    private Long appliedBy;
    private Integer revision;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
