package com.aicp.module.generation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("generation_tasks")
public class GenerationTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long projectId;
    private Long nodeId;
    private Long shotId;
    private String type;
    private String subType;
    private String provider;
    private String modelId;
    private String parameters;
    private String status;
    private Integer progress;
    private Integer creditCost;
    private String errorCode;
    private String errorMessage;
    private String outputAssets;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
