package com.aicp.module.director.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("director_revisions")
public class DirectorRevision {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long sceneId;
    private Integer revision;
    private String documentJson;
    private String documentHash;
    private String idempotencyKey;
    private Long frozenBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
