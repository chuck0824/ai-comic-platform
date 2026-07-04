package com.aicp.module.enterprise.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("enterprise_purchase_budget_entries")
public class EnterprisePurchaseBudgetEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long budgetId;
    private String workspaceId;
    private String entryType;     // RESERVE, RELEASE, CONSUME, REVERSE
    private Long amountCents;
    private String sourceType;
    private String sourceId;
    private String walletTransferNo;
    private String idempotencyKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
