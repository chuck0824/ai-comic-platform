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

    /** @deprecated legacy: replaced by contentProjectId + productionUnitId */
    @Deprecated
    private Long scriptId;

    /** @deprecated legacy: replaced by productionUnitId */
    @Deprecated
    private Integer episodeIndex;

    private String styleConfig;
    private String appliedAssetIds;
    private String status;
    private Integer canvasVersion;

    // ===== New ownership columns (2026-07-01) =====

    private Long contentProjectId;
    private String productionUnitType;
    private Long productionUnitId;
    private Long sourceContentVersionId;
    private Long sourceStoryboardVersionId;
    private String productionSnapshot;
    private String purpose;
    private Long ownerId;
    private String thumbnailUrl;
    private String idempotencyKey;
    private LocalDateTime archivedAt;

    @Version
    private Integer revision;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
