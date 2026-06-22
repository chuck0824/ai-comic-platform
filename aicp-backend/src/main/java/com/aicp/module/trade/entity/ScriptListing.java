package com.aicp.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("script_listings")
public class ScriptListing {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private Long sellerId;
    private String licenseTypes;
    private String status;
    private LocalDateTime listedAt;
    private LocalDateTime delistedAt;
}
