package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_canvas_snapshots")
public class StoryboardCanvasSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long projectId;
    private Long storyboardId;
    private Long versionId;
    private String snapshotType;
    private String idempotencyKey;
    private Long parameterVersionId;
    private Long sourceContentVersionId;
    private String snapshotJson;
    private String snapshotHash;
    private String gateReportJson;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
