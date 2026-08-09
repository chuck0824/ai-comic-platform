package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("asset_applications")
public class AssetApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private Long assetId;
    private Long assetVersionId;
    private Long projectId;
    private String targetType;
    private Long targetId;
    private String targetKey;         // Stable textual consumer ID; legacy rows may remain null
    private String changeSummary;
    private String previousState;     // JSON: snapshot before application for undo
    private String undoTokenHash;     // SHA-256 hash of the undo token
    private Long appliedBy;
    private String idempotencyKey;
    private String status;            // APPLIED / UNDONE

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
