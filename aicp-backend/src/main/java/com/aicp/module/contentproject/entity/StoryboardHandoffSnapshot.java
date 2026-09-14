package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_handoff_snapshots")
public class StoryboardHandoffSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long checkpointId;
    private Long reviewedScriptBodyVersionId;
    private String continuityCheckResult;
    private Integer sceneCount;
    private String payloadJson;
    private String contentHash;
    private Long createdBy;
    private LocalDateTime capturedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
