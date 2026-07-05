package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ecosystem_rules")
public class EcosystemRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long bibleVersionId;
    private String ruleType;          // era_world, world_rule, social_structure, institution_taboo,
                                      // faction_organization, resource_system, ability_system,
                                      // location_system, key_history
    private String name;
    private String summary;
    private String detailsJson;
    private String scopeJson;
    private String exceptionsJson;
    private String status;            // draft, confirmed, archived
    private String sourceType;        // manual, ai_extracted, merged, imported
    private String evidenceJson;
    private Integer revision;
    private Long createdBy;
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
