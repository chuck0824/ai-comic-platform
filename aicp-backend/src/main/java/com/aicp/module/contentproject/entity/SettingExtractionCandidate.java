package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("setting_extraction_candidates")
public class SettingExtractionCandidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String settingType;
    private String canonicalName;
    private String aliasesJson;
    private String fieldValuesJson;
    private String evidenceText;
    private String evidencePositionJson;
    private BigDecimal confidence;
    private Long matchedEntityId;
    private String matchReason;
    private String matchStatus;        // new, duplicate, conflict
    private String fieldDecisionsJson; // per-field: merge/keep/replace
    private String reviewStatus;       // pending, accepted, rejected

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
