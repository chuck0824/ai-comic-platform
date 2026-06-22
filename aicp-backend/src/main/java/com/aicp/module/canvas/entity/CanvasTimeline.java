package com.aicp.module.canvas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("canvas_timelines")
public class CanvasTimeline {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String data;
    private Integer durationMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
