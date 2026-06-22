package com.aicp.module.canvas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("canvas_edges")
public class CanvasEdge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long projectId;
    private Long sourceNodeId;
    private String sourcePort;
    private Long targetNodeId;
    private String targetPort;
    private String edgeType;
    private String metadata;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
