package com.aicp.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("purchased_script_copies")
public class PurchasedScriptCopy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderItemId;
    private String workspaceId;
    private Long listingId;
    private Long sourceVersionId;
    private String contentJson;
    private String title;
    private Long createdByUserId;
    private Long sourceListingId;
    private String sourceAuthorName;
    private String status;
    private Integer rowVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
