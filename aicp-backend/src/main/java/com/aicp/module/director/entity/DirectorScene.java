package com.aicp.module.director.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("director_scenes")
public class DirectorScene {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long shotUnitId;
    private Long currentDraftId;
    private Long currentRevisionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
