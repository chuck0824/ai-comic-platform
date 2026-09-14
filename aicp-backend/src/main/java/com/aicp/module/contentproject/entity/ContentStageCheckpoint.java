package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_stage_checkpoints")
public class ContentStageCheckpoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String stageKey;
    private String state;
    private String primaryArtifactType;
    private Long primaryArtifactId;
    private Long adoptedContentVersionId;
    private String inputSnapshotJson;
    private String inputSnapshotHash;
    private String gateResultJson;
    private String staleReasonJson;
    private Integer revision;
    private LocalDateTime completedAt;
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
