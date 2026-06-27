package com.aicp.module.script.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chapter_versions")
public class ChapterVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private Long episodeId;
    private Integer chapterNumber;
    private String title;
    private String content;
    private String contentFormat;
    private String versionNo;
    private String changeSummary;
    private String source;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
