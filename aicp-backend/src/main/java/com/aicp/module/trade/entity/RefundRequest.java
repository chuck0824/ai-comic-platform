package com.aicp.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("refund_requests")
public class RefundRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long requesterUserId;
    private String reasonCode;
    private String reasonText;
    private String evidenceJson;
    private String status;
    private Long reviewerUserId;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private Long refundAmountCents;
    private String walletReversalNo;
    private Integer rowVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
