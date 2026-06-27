package com.aicp.module.script.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("adaptation_versions")
public class AdaptationVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private Long sourceChapterVersionId;
    private Long sourceProjectVersionId;
    private String targetType;
    private String versionNo;
    private String title;
    private String content;
    private String hookStrategyJson;
    private String status;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
