package com.aicp.module.canvas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("canvas_projects")
public class CanvasProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long userId;
    private Long enterpriseId;
    private String name;
    private Long scriptId;
    private Integer episodeIndex;
    private String styleConfig;
    private String status;
    private Integer canvasVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
