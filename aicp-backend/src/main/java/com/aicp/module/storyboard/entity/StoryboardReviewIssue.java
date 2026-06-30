package com.aicp.module.storyboard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storyboard_review_issues")
public class StoryboardReviewIssue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;
    private String fingerprint;
    private String issueType;
    private String severity;
    private Long shotId;
    private String message;
    private String evidence;
    private String suggestion;
    private String status;
    private String resolutionNote;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
