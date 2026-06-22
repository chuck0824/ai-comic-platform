package com.aicp.module.script.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("gen_tasks")
public class GenTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String projectId;
    private String genType;
    private String storyboardTier;
    private String inputParams;
    private String outputData;
    private String promptUsed;
    private String modelUsed;
    private String status;
    private Integer progress;
    private Integer tokensUsed;
    private Integer durationMs;
    private String errorMsg;
    private Date completedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
