package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_creative_rules")
public class StoryboardCreativeRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;
    private String ruleType;
    private String dimensionName;
    private String principle;
    private String implementationText;
    private String targetRefsJson;
    private String effectText;
    private String status;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
