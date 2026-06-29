package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("volume_outlines")
public class VolumeOutline {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer volumeNo; private String title; private String goal; private String turns;
    private String volumeEndHook; private String characterChanges; private Integer chapterCount;
    private String status; private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
