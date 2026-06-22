package com.aicp.module.script.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("episode_hooks")
public class EpisodeHook {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long episodeId;
    private Long scriptId;
    private String hookType;      // opening, closing, reversal, plant
    private String content;
    private Double strengthScore; // 0.0 - 1.0
    private String strengthReason;
    private Integer position;     // word offset / beat index
    private String status;        // draft, reviewed, approved
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
