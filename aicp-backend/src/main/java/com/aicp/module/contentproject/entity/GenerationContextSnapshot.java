package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("generation_context_snapshots")
public class GenerationContextSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long generationJobId;
    private Long projectId;
    private Long bibleVersionId;
    private Long projectGuideId;
    private String characterGuideIdsJson;
    private Long unitGuideId;
    private String selectedVersionsJson;
    private String resolvedGuideJson;
    private String payloadJson;
    private String payloadHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
