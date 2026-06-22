package com.aicp.module.notify.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notifications")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private Integer isRead;
    private String actionUrl;
    private String sourceType;
    private String sourceId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
