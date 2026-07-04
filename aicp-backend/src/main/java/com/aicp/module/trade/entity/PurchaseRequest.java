package com.aicp.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("purchase_requests")
public class PurchaseRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private Long requesterUserId;
    private Long listingId;
    private String licenseType;
    private Long amountCents;
    private String currency;
    private String reason;
    private Long approverUserId;
    private String approvalComment;
    private String status;
    private String orderNo;
    private String budgetSubjectType;
    private String budgetSubjectId;
    private String budgetReservationEntryId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
