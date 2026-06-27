package com.aicp.module.script.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("episode_review_reports")
public class EpisodeReviewReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private Long episodeId;
    private Integer episodeNumber;
    private String overallStatus; // pass, needs_revision
    private Double overallScore;
    private Double hookScore;
    private Double showrunnerScore;
    private Double directorScore;
    private String reportJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
