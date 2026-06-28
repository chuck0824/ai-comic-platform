package com.aicp.module.canvas.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("canvas_nodes")
public class CanvasNode {
    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank
    private String uuid;

    @NotNull
    private Long projectId;

    @NotBlank
    private String type;

    private String name;

    @NotNull
    private Integer x;

    @NotNull
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
