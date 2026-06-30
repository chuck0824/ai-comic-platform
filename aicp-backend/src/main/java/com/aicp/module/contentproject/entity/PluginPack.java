package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plugin_packs")
public class PluginPack {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long projectId;
    private Long storyboardMasterId;
    private Integer versionNo;
    private String name;
    private String manifestJson;   // JSON: {assets:[{type,url,assetId}],checksums:{},metadata:{}}
    private Integer assetCount;
    private String status;         // draft | exported | archived
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
