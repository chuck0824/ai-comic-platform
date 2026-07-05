package com.aicp.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("trade_orders")
public class TradeOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String status;
    private Long buyerUserId;
    private String buyerWorkspaceId;
    private String buyerWorkspaceType;
    private Long sellerUserId;
    private String sellerWorkspaceId;
    private Long totalAmountCents;
    private Long platformFeeCents;
    private Long sellerIncomeCents;
    private String currency;
    private String walletTransferNo;
    private String walletStatus;
    private String createIdempotencyKey;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime fulfilledAt;
    private LocalDateTime completedAt;
    private LocalDateTime refundedAt;
    private Integer rowVersion;
    private String failureReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
