package com.aicp.module.canvas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("canvas_nodes")
public class CanvasNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long projectId;
    private String type;
    private String name;
    private Integer x;
    private Integer y;
    private Integer width;
    private Integer height;
    private String inputData;
    private String outputData;
    private String status;
    private Long groupId;
    private Long lockedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
