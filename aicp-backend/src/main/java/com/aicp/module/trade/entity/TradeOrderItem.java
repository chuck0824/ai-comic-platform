package com.aicp.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("trade_order_items")
public class TradeOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long listingId;
    private Long scriptId;
    private Long scriptVersionId;
    private String licenseType;
    private Long priceCents;
    private String currency;
    private String titleSnapshot;
    private String authorSnapshot;
    private String tagsSnapshot;
    private String agreementText;
    private String agreementVersion;
    private String agreementHash;
    private Integer historicalNormalCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
