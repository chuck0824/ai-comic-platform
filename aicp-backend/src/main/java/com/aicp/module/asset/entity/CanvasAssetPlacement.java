package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("canvas_asset_placements")
public class CanvasAssetPlacement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private Long assetId;
    private Long assetVersionId;
    private Long canvasProjectId;
    private Long nodeId;
    private Long placedBy;
    private String idempotencyKey;
    private LocalDateTime releasedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
