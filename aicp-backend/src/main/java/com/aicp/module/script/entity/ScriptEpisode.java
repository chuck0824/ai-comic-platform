package com.aicp.module.script.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("script_episodes")
public class ScriptEpisode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private Integer episodeNumber;
    private String title;
    private String content;
    private String storyboardTier;
    private Integer wordCount;
    private String status;
    private String openingHook;
    private String closingHook;
    private Double hookScoreAvg;
    private Integer hookCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
