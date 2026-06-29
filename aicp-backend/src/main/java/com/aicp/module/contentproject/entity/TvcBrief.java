package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tvc_briefs")
public class TvcBrief {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String brandName; private String productName; private String targetAudience;
    private String budget; private String platforms; private String duration; private String additionalNotes; private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
