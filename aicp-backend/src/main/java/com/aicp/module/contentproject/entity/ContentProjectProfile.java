package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_project_profiles")
public class ContentProjectProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String genreTag;
    private String plotTags;
    private String toneTags;
    private String settingTag;
    private String synopsis;
    private String outline;
    private Integer revision;
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
