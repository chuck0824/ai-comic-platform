package com.aicp.module.script.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("scripts")
public class Script {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uuid;
    private String projectId;
    private String title;
    private Long authorUserId;
    private Long ownerUserId;
    private String ownerType;
    private Long enterpriseId;
    private Integer episodeCount;
    private Integer completedEpisodes;
    private Integer totalWords;
    private String coverImageUrl;
    private String synopsis;
    private String genreTag;
    private String plotTags;
    private String toneTags;
    private String settingTag;
    private String source;
    private String status;
    private String currentVersion;
    private String maturityLevel;
    private String pluginPack;
    private Double rating;
    private Integer reviewCount;
    private Integer salesCount;
    @TableLogic private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
