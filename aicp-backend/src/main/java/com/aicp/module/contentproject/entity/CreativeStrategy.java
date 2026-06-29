package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("creative_strategies")
public class CreativeStrategy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer angleNo; private String angleName; private String openingHook; private String valueProposition; private String brandMemoryPoint; private String platform; private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
