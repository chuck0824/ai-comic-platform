package com.aicp.module.sop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sop_audits")
public class SopAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectId;
    private Long canvasProjectId;
    private String shotId;
    private String checkItem;
    private String issueType;
    private String severity;
    private String qualityGrade;
    private String description;
    private String fixSuggestion;
    private String responsibleRole;
    private String status;
    private Long fixedBy;
    private Long verifiedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
