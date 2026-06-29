package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tvc_scripts")
public class TvcScript {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long sourceUnitId; private String versionName; private String contentJson; private String plainText;
    private Integer durationSec; private String platforms; private String status; private Long sourceVersionId; private String contentHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
