package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("plot_tasks")
public class PlotTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String taskType; private String title; private String description;
    private String stageGoals; private String obstacles; private String cost;
    private String characterIds; private Long parentTaskId; private String status; private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
