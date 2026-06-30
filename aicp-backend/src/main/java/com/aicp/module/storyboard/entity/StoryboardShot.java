package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_version_shots")
public class StoryboardShot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long versionId;
    private Long sceneId;
    private String shotKey;
    private String shotCode;
    private Long durationMs;
    private String shotSize;
    private String visualDescription;
    private String lightingAtmosphere;
    private String characterAction;
    private String emotionDescription;
    private String dialogueText;
    private String sceneTagsJson;
    private String soundEffect;
    private String referenceText;
    private String imagePrompt;
    private String videoMotionPrompt;
    private String directorIntention;
    private String actionMotivation;
    private String relationshipBlocking;
    private String informationGap;
    private String audioVisualRelation;
    private String editPoint;
    private String dubText;
    private String subtitleText;
    private String failureStrategy;
    private String status;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
