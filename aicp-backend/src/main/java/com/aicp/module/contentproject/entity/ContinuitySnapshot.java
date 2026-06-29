package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("continuity_snapshots")
public class ContinuitySnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long contentUnitId;
    private String snapshotJson;
    private String contentHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
