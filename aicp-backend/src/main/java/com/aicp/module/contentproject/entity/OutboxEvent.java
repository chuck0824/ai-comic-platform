package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("outbox_events")
public class OutboxEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;
    private String aggregateType;
    private Long aggregateId;
    private Integer aggregateRevision;
    private String eventType;
    private String payloadJson;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime occurredAt;
    private LocalDateTime publishedAt;
}
