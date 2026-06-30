package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("market_listings")
public class MarketListing {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String publisherWorkspaceId;
    private Long publisherUserId;
    private Long sourceAssetId;
    private Long sourceVersionId;
    private String assetType;          // denormalized for query performance
    private String publicSnapshot;     // JSON: name, description, tags, previews, author_name, recommended_params
    private String licenseType;        // FREE (fixed for this release)
    private BigDecimal price;          // 0 for this release
    private String status;             // LISTED / UNLISTED / REMOVED
    private Integer useCount;
    private BigDecimal rating;

    private Integer rowVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
