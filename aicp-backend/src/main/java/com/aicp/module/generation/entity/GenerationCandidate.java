package com.aicp.module.generation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("generation_candidates")
public class GenerationCandidate {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;
    private Long requestSnapshotId;
    private Long taskId;
    private Integer attemptNo;
    private Long assetVersionId;
    private String modelId;
    private Long seed;
    private Integer actualCredits;
    private String safetyStatus;  // PENDING | PASS | FLAGGED | REJECTED
    private Boolean isSelected;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
