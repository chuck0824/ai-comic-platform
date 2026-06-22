package com.aicp.module.generation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("credit_transactions")
public class CreditTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long enterpriseId;
    private Long taskId;
    private Integer amount;
    private String type;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
