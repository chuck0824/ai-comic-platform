package com.aicp.module.script.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("script_versions")
public class ScriptVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private String version;
    private String content;
    private String changeSummary;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
