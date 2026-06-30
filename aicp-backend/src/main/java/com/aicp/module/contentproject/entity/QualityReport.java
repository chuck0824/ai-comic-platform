package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quality_reports")
public class QualityReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long projectId;
    private String canvasProjectId;
    private String nodeUuid;
    private Long assetVersionId;

    // 5-dimension quality scores (0-100)
    private Integer correctnessScore;
    private Integer securityScore;
    private Integer performanceScore;
    private Integer costScore;
    private Integer consistencyScore;

    private String issuesJson;     // JSON array of found issues
    private String summary;         // AI-generated quality summary
    private String status;          // open | resolved | wont_fix

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
