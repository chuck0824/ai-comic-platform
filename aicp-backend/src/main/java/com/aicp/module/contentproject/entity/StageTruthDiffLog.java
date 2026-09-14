package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stage_truth_diff_log")
public class StageTruthDiffLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String triggerSource;
    private String legacyCurrentStage;
    private String truthCurrentStage;
    private Integer legacyProgress;
    private Integer truthProgress;
    private String legacySnapshotJson;
    private String truthSnapshotJson;
    private String diffSummaryJson;
    private String fingerprint;
    private LocalDateTime createdAt;
}
