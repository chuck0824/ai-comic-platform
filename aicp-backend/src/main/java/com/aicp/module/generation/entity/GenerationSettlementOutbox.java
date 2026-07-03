package com.aicp.module.generation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("generation_settlement_outbox")
public class GenerationSettlementOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String workspaceId;
    private String stage;
    private String payload;
    private String status;       // PENDING / PROCESSING / EXHAUSTED
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
