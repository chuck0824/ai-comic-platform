package com.aicp.module.script.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("repo_assets")
public class RepoAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String assetId;
    private String assetType;
    private String name;
    private Long scriptId;
    private String projectId;
    private Long ownerUserId;
    private Long enterpriseId;
    private String description;
    private String faceId;
    private String costumeId;
    private String voiceId;
    private String locationId;
    private String maturityLevel;
    private Integer isLocked;
    private String shortAnchor;
    private String longAnchor;
    private String referenceImageUrls;
    private String consistencyPrompt;
    private Long seedValue;
    private String metadata;
    private Integer isPublic;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
