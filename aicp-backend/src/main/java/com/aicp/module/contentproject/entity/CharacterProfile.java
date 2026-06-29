package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("character_profiles")
public class CharacterProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name; private String role; private String archetype;
    private String appearance; private String personality; private String motivation;
    private String longTermGoal; private String knowledgeBoundary; private String dialogueStyle;
    private String backstory; private String relationshipsJson; private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
