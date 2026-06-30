package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("asset_publish_requests")
public class AssetPublishRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private Long assetId;
    private Long versionId;
    private Long requesterId;
    private Long reviewerId;
    private String status;          // PENDING / APPROVED / REJECTED / CANCELLED
    private String reason;          // publish reason (submit) or reject reason (reject)
    private String reviewComment;

    private Integer rowVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
