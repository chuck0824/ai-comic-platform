package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_writing_guides")
public class ProjectWritingGuide {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long bibleVersionId;
    private String scopeType;         // project, character, content_unit
    private Long scopeId;             // 0 for project, character_id for character, content_unit_id for unit
    private Integer versionNo;
    private String status;            // draft, confirmed, superseded
    private String guideJson;
    private Long parentGuideId;
    private String sourceType;        // manual, ai_extracted, imported
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
