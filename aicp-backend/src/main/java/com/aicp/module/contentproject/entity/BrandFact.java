package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("brand_facts")
public class BrandFact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String factType; private String content; private String evidenceStatus; private String evidenceUrl; private String isMustExpress; private String isMustNotExpress;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
