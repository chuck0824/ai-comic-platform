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

    // ── Workbench extension ──
    private Long sourceTaskId;
    private String storageProvider;
    private String storageBucket;
    private String storageKey;
    private String mimeType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private Integer durationMs;

    private String metadata;    // JSON: structured asset data
    private String previewUrl;
    private String contentRef;
    private String checksum;
    private String generationSnapshot;  // JSON: provider/model/prompt snapshot

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
