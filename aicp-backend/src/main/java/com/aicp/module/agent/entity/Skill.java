package com.aicp.module.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("skills")
public class Skill {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private String name;
    private String description;
    private String content;
    private String type;
    private String version;
    private String variables;
    private String visibility;
    private Long ownerId;
    private Integer usageCount;
    private Double rating;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
