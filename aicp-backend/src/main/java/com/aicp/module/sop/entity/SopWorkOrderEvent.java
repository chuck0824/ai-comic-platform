package com.aicp.module.sop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sop_work_order_events")
public class SopWorkOrderEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String note;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
