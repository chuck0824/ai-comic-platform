package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("asset_entitlements")
public class AssetEntitlement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String beneficiaryWorkspaceId;
    private Long listingId;
    private Long sourceVersionId;
    private String grantType;      // FREE_CLAIM
    private Long claimedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime claimedAt;
}
