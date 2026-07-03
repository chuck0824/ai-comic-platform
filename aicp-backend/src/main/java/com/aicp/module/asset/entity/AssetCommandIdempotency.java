package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("asset_command_idempotencies")
public class AssetCommandIdempotency {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private Long userId;
    private String idempotencyKey;
    private String commandType;
    private String requestHash;
    private Integer responseCode;
    private String responseBody;
    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
