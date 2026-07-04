package com.aicp.module.contentproject.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("project_export_requests")
public class ProjectExportRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceId;
    private String departmentId;
    private Long projectId;
    private Long projectVersionId;
    private Long requesterUserId;
    private String exportScopeJson;
    private String exportFormat;
    private String watermarkPolicy;
    private String deliveryTarget;
    private String complianceEvidenceRef;
    private String contentSnapshotSummary;
    private String status;          // PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED
    private Long approverUserId;
    private String approverComment;
    private LocalDateTime approvedAt;
    private Long exportTaskId;

    @Version
    private Integer rowVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
