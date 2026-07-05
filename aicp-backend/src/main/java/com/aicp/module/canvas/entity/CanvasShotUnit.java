package com.aicp.module.canvas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("canvas_shot_units")
public class CanvasShotUnit {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long projectId;
    private String mode;              // EXPLORATION | PRODUCTION
    private String provisionalShotId; // draft_shot_xxx (EXPLORATION)
    private Long sourceShotId;        // 正式分镜ID (PRODUCTION)
    private Integer sourceShotRevision;
    private Integer targetDurationMs;
    private Integer fps;
    private String aspectRatio;
    private Integer sortOrder;

    @Version
    private Integer rowVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
