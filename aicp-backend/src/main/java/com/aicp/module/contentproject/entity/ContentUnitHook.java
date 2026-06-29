package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("content_unit_hooks")
public class ContentUnitHook {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long contentUnitId;
    private Long contentVersionId;
    private String previousPromise;
    private String promisePayoff;
    private String openingHook;
    private String midEscalation;
    private String payoffOrReversal;
    private String closingHook;
    private String nextPromise;
    private Double hookScore;
    private String lockedFields;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
