package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("LStory_LTimelines")
public class StoryTimeline {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String eventName; private String description; private String relativeTime;
    private String involvedCharacters; private Long locationId; private String foreshadowingIds; private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
