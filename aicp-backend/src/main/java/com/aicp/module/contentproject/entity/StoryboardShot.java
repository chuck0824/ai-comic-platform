package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cp_storyboard_shots")
public class StoryboardShot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long sceneId;
    private Long masterId;
    private Integer shotNo;
    private String shotType;
    private Integer durationSec;
    private String description;
    private String cameraAction;
    private String dialogueRef;
    private String visualRefUrl;
    private String status;
    private Integer sortOrder;

    // B-tier: director-level intent and blocking
    private String directorIntention;
    private String actionMotivation;
    private String relationshipBlocking;
    private String informationGap;
    private String editPoint;

    // C-tier: production card (抽卡表) — image/video/dub/subtitle generation prompts
    private String imagePrompt;
    private String videoPrompt;
    private String dubText;
    private String subtitle;
    private String failureStrategy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
