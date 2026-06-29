package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cp_storyboard_scenes")
public class StoryboardScene {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long masterId;
    private Integer sceneNo;
    private String dramaticGoal;
    private String beatDescription;
    private Long locationId;
    private String characterIds;
    private Integer durationSec;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
