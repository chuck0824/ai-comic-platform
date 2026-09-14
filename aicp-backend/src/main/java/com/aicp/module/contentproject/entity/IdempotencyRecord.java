package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("idempotency_records")
public class IdempotencyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String idempotencyKey;
    private String scope;
    private String requestHash;
    private String responsePayload;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
