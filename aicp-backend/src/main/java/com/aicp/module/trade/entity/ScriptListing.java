package com.aicp.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("script_listings")
public class ScriptListing {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private Long sellerUserId;
    private Long scriptId;
    private Long scriptVersionId;
    private String title;
    private String synopsis;
    private String coverUrl;
    private String tagsJson;
    private String charactersJson;
    private Integer episodeCount;
    private String authorDisplayName;
    private Integer previewEpisodeCount;
    private String previewEpisodesJson;
    private String reviewStatus;
    private String reviewReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String listingStatus;
    private String exclusiveLicenseType;
    private Integer historicalNormalCount;
    private String reservedOrderNo;
    private LocalDateTime reservationExpiresAt;
    private Integer rowVersion;
    private LocalDateTime listedAt;
    private LocalDateTime delistedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
