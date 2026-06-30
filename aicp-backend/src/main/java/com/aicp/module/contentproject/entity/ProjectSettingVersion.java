package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_setting_versions")
public class ProjectSettingVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long entityId;
    private Integer versionNo;
    private String snapshotJson;       // 完整快照
    private String fieldChangesJson;   // 字段级变更
    private String sourceType;         // manual, ai_extracted, merged
    private Long operatedBy;
    private String evidenceJson;       // AI 证据

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
