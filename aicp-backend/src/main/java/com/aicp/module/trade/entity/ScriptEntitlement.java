package com.aicp.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("script_entitlements")
public class ScriptEntitlement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderItemId;
    private String beneficiaryWorkspaceId;
    private Long listingId;
    private Long scriptVersionId;
    private String licenseType;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
    private Integer maxAccounts;
    private Integer allowCommercial;
    private Integer allowAdaptation;
    private Integer allowSublicense;
    private String territoryRestriction;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private Integer rowVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
