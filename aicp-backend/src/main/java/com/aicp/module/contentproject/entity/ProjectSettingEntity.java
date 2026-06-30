package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_setting_entities")
public class ProjectSettingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String settingType;       // character, background, faction, location, item
    private String canonicalName;
    private String aliasesJson;
    private String summary;
    private String detailsJson;
    private String relationshipsJson;
    private String status;            // draft, confirmed, needs_enrichment, archived
    private String sourceType;        // manual, ai_extracted, merged
    private Integer currentVersionNo;
    private Integer revision;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime archivedAt;
    private Long archivedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
