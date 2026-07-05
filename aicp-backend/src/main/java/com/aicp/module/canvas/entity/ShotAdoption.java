package com.aicp.module.canvas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("shot_adoptions")
public class ShotAdoption {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long shotUnitId;
    private Integer revision;
    private Long candidateId;
    private Long adoptedBy;
    private String reason;
    private String overrideReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
