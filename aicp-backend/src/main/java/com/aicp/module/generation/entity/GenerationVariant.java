package com.aicp.module.generation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("generation_variants")
public class GenerationVariant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentTaskId;
    private Integer variantIndex;
    private String parameters;
    private String outputData;
    private Double qualityScore;
    private Integer selected;
    private String taskUuid;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
