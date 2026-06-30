package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workspace_assets")
public class WorkspaceAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private String workspaceId;
    private String workspaceType;
    private Long creatorUserId;
    private String assetType;
    private String name;
    private String description;
    private String tags;         // JSON array string
    private String accessScope;  // PRIVATE / WORKSPACE
    private String sourceType;   // CREATED / MARKET_CLAIMED / PROJECT_GENERATED / IMPORTED
    private Long sourceListingId;
    private Long sourceVersionId;
    private Long currentVersionId;
    private String status;       // ACTIVE / ARCHIVED

    @Version
    private Integer rowVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
