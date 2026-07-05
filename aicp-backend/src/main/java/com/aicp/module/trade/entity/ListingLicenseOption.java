package com.aicp.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("listing_license_options")
public class ListingLicenseOption {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long listingId;
    private String licenseType;
    private Long priceCents;
    private String currency;
    private String termJson;
    private String agreementText;
    private String agreementVersion;
    private String agreementHash;
    private Integer enabled;
    private Integer rowVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
