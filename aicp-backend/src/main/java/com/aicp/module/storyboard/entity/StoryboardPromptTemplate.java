package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_prompt_templates")
public class StoryboardPromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;
    private String templateCode;
    private String emotionName;
    private String shotRefsJson;
    private String imagePrompt;
    private String videoMotionPrompt;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
