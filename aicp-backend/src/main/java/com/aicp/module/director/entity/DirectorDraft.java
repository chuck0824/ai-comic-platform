package com.aicp.module.director.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("director_drafts")
public class DirectorDraft {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long sceneId;
    private String documentJson;

    @Version
    private Integer rowVersion;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
