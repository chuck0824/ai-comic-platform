package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_emotion_segments")
public class StoryboardEmotionSegment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;
    private String emotionType;
    private String shotRange;
    private Integer intensity;
    private String coreExpression;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
