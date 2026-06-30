package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("asset_versions")
public class AssetVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetId;
    private Integer versionNumber;
    private String metadata;    // JSON: structured asset data
    private String previewUrl;
    private String contentRef;
    private String checksum;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
