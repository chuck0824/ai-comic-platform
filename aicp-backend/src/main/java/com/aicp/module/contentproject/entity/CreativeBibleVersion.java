package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("creative_bible_versions")
public class CreativeBibleVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Integer versionNo;
    private String status;            // draft, reviewable, confirmed, superseded, archived
    private Long sourceVersionId;
    private String summary;
    private String snapshotJson;
    private String snapshotHash;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
