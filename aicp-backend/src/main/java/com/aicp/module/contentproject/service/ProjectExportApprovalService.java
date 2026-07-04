package com.aicp.module.contentproject.service;

import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.contentproject.entity.ProjectExportRequest;
import com.aicp.module.contentproject.mapper.ProjectExportRequestMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        return req;
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
        return req;
    }

    public ProjectExportRequest get(Long requestId) {
        return mapper.selectById(requestId);
    }
}
