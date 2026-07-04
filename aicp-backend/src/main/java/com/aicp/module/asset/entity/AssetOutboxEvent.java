package com.aicp.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_outbox_events")
public class AssetOutboxEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private String status;       // PENDING, PROCESSED, FAILED
    private Integer attempts;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
