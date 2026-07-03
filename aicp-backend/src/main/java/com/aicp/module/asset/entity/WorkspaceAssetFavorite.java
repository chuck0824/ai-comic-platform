package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workspace_asset_favorites")
public class WorkspaceAssetFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String workspaceId;
    private Long assetId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
