package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("asset_favorites")
public class AssetFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String workspaceId;
    private Long listingId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
