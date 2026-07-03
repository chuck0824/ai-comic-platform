package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("asset_activity_logs")
public class AssetActivityLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private Long assetId;
    private Long actorUserId;
    private String action;
    private String beforeData;
    private String afterData;
    private String requestId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
