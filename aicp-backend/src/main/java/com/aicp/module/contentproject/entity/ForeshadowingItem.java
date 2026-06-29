package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("foreshadowing_items")
public class ForeshadowingItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String description; private Long plantedInUnitId; private Long payoffInUnitId;
    private String status; private String category; private String characterIds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
