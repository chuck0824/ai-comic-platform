package com.aicp.module.canvas.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("canvas_projects")
public class CanvasProject {
    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank
    private String uuid;

    @NotNull
    private Long userId;

    private Long enterpriseId;

    private String workspaceId;

    @NotBlank
    private String name;

    private Long scriptId;
    private Integer episodeIndex;
    private String styleConfig;
    private String appliedAssetIds;
    private String status;
    private Integer canvasVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
