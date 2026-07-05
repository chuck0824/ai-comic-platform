package com.aicp.module.sop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sop_rule_set_versions")
public class SopRuleSetVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String version;
    private String name;
    private String description;
    private Integer ruleCount;
    private Integer enabledCount;
    private Integer isActive;
    private LocalDateTime publishedAt;
    private Long publishedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
