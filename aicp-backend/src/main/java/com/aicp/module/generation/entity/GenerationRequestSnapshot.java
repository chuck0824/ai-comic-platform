package com.aicp.module.generation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("generation_request_snapshots")
public class GenerationRequestSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long nodeId;
    private Long shotUnitId;
    private String payloadJson;
    private String payloadHash;
    private String resolvedModelId;
    private String resolvedModelVersion;
    private String adapterVersion;
    private Integer estimatedCredits;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
