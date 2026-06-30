package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_character_visuals")
public class StoryboardCharacterVisual {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;
    private Long characterRefId;
    private String characterName;
    private String coreIdentity;
    private String dailyLook;
    private String taskLook;
    private String performanceAnchor;
    private String promptLock;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
