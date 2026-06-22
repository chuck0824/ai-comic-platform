package com.aicp.module.script.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("prompt_templates")
public class PromptTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private String name;
    private String category;      // script_generate, character_extract, shot_split, frame_generate, video_generate
    private String content;       // Prompt 模板内容，支持 {{变量}} 语法
    private String description;
    private String visibility;    // private, team, public
    private Long ownerId;
    private Integer version;
    private String status;        // draft, published
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
