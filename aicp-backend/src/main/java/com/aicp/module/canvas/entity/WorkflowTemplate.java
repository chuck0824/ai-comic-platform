package com.aicp.module.canvas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workflow_templates")
public class WorkflowTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long ownerId;
    private String name;
    private String description;
    private String category;
    private String config;
    private String variables;
    private String thumbnailUrl;
    private Integer usageCount;
    private Double rating;
    private String visibility;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
