package com.aicp.module.generation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("platform_assets")
public class PlatformAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private Long projectId;
    private Long sourceNodeId;
    private Long sourceTaskId;
    private String type;
    private String name;
    private String fileUrl;
    private String thumbnailUrl;
    private String prompt;
    private String modelId;
    private String parameters;
    private Long fileSize;
    private Integer durationMs;
    private Integer width;
    private Integer height;
    private String metadata;
    private String tags;
    private Integer favorite;
    private Long ownerUserId;
    private Long enterpriseId;
    private String visibility;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
