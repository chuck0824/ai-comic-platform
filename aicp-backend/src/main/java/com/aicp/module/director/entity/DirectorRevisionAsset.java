package com.aicp.module.director.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("director_revision_assets")
public class DirectorRevisionAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long revisionId;
    private Long assetId;
    private Long assetVersionId;
    private String role;
}
