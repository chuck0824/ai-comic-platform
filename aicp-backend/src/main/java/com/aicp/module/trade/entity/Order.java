package com.aicp.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long buyerId;
    private Long buyerEnterpriseId;
    private Long sellerId;
    private Long scriptId;
    private String licenseType;
    private BigDecimal amount;
    private BigDecimal platformFee;
    private BigDecimal sellerIncome;
    private String status;
    private String paymentMethod;
    private LocalDateTime paidAt;
    private LocalDateTime expireAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
