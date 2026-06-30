package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tag_dictionary")
public class TagDictionary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String axis;
    private String tagValue;
    private String tagLabel;
    private Integer sortOrder;
    private Integer isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
