package com.aicp.module.contentproject.service;

import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.contentproject.entity.ProjectExportRequest;
import com.aicp.module.contentproject.mapper.ProjectExportRequestMapper;
import com.aicp.module.enterprise.service.ApprovalProjector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectExportApprovalService {

    private final ProjectExportRequestMapper mapper;
    private final ApprovalProjector projector;

    @Transactional
    public ProjectExportRequest submit(WorkspaceContext ctx, Long projectId,
                                        Long projectVersionId, String exportScopeJson,
                                        String exportFormat, String watermarkPolicy,
                                        String deliveryTarget) {
        var req = new ProjectExportRequest();
        req.setWorkspaceId(ctx.workspaceId());
        req.setDepartmentId(ctx.departmentId());
        req.setProjectId(projectId);
        req.setProjectVersionId(projectVersionId);
        req.setRequesterUserId(ctx.userId());
        req.setExportScopeJson(exportScopeJson);
        req.setExportFormat(exportFormat != null ? exportFormat : "PDF");
        req.setWatermarkPolicy(watermarkPolicy);
        req.setDeliveryTarget(deliveryTarget);
        req.setStatus("PENDING");
        mapper.insert(req);

        // Project to unified approval inbox
        try {
            projector.project("PROJECT_EXPORT", "export-" + req.getId(), 0,
                    ctx.workspaceId(), ctx.departmentId(), ctx.userId(),
                    "项目导出: #" + req.getId(), 0L, "PENDING", "[]");
        } catch (Exception e) {
            log.warn("Failed to project export approval {}: {}", req.getId(), e.getMessage());
        }

        log.info("Export submitted: id={}, project={}", req.getId(), projectId);
        return req;
    }

    @Transactional
    public ProjectExportRequest approve(WorkspaceContext ctx, Long requestId, String comment) {
        var req = mapper.selectById(requestId);
        if (req == null || !"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("export request not in PENDING state");
        }
        req.setStatus("APPROVED");
        req.setApproverUserId(ctx.userId());
        req.setApproverComment(comment);
        req.setApprovedAt(LocalDateTime.now());
        mapper.updateById(req);

        // Create export task (stub — task center integration TBD)
        Long taskId = createExportTask(req);
        if (taskId != null) {
            req.setExportTaskId(taskId);
            mapper.updateById(req);
        }

        try {
            projector.project("PROJECT_EXPORT", "export-" + req.getId(), 1,
                    req.getWorkspaceId(), req.getDepartmentId(), req.getRequesterUserId(),
                    "导出已批准 #" + req.getId() + (taskId != null ? " → 任务#" + taskId : ""), 0L, "APPROVED",
                    "[{\"action\":\"view_task\",\"task_id\":" + (taskId != null ? taskId : "null") + "}]");
        } catch (Exception e) {
            log.warn("Failed to project export approval update {}: {}", req.getId(), e.getMessage());
        }

        return req;
    }

    /**
     * Create an async export task. Stub — replace with task center API call.
     * Returns the task ID, or null if task creation is deferred.
     */
    private Long createExportTask(ProjectExportRequest req) {
        log.info("Export task queued for project={}, version={}, format={}",
                req.getProjectId(), req.getProjectVersionId(), req.getExportFormat());
        // TODO: integrate with task center (GenerationTask or dedicated export job)
        return null; // task center integration deferred
    }

    @Transactional
    public ProjectExportRequest reject(WorkspaceContext ctx, Long requestId, String reason) {
        var req = mapper.selectById(requestId);
        if (req == null || !"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("export request not in PENDING state");
        }
        req.setStatus("REJECTED");
        req.setApproverUserId(ctx.userId());
        req.setApproverComment(reason);
        req.setApprovedAt(LocalDateTime.now());
        mapper.updateById(req);

        try {
            projector.project("PROJECT_EXPORT", "export-" + req.getId(), 1,
                    req.getWorkspaceId(), req.getDepartmentId(), req.getRequesterUserId(),
                    "导出已驳回 #" + req.getId(), 0L, "REJECTED", "[]");
        } catch (Exception e) {
            log.warn("Failed to project export rejection {}: {}", req.getId(), e.getMessage());
        }

        return req;
    }

    @Transactional
    public ProjectExportRequest cancel(WorkspaceContext ctx, Long requestId) {
        var req = mapper.selectById(requestId);
        if (req == null || !"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("only PENDING requests can be cancelled");
        }
        req.setStatus("CANCELLED");
        mapper.updateById(req);

        try {
            projector.project("PROJECT_EXPORT", "export-" + req.getId(), 1,
                    req.getWorkspaceId(), req.getDepartmentId(), req.getRequesterUserId(),
                    "导出已取消 #" + req.getId(), 0L, "CANCELLED", "[]");
        } catch (Exception e) {
            log.warn("Failed to project export cancellation {}: {}", req.getId(), e.getMessage());
        }

        return req;
    }

    public ProjectExportRequest get(Long requestId) {
        return mapper.selectById(requestId);
    }
}
