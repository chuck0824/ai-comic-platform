package com.aicp.module.enterprise.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("enterprise_purchase_budgets")
public class EnterprisePurchaseBudget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private String subjectType;    // WORKSPACE, DEPARTMENT, MEMBER
    private String subjectId;
    private String periodMonth;    // YYYY-MM
    private Long amountCents;
    private Long singleLimitCents;
    private Long reservedCents;
    private Long consumedCents;

    @Version
    private Integer rowVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
