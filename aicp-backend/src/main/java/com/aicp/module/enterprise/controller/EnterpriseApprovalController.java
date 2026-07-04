package com.aicp.module.enterprise.controller;

import com.aicp.common.dto.ApiResponse;
import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.enterprise.dto.ApprovalViews.ApprovalDecisionRequest;
import com.aicp.module.enterprise.entity.EnterpriseApprovalItem;
import com.aicp.module.enterprise.mapper.EnterpriseApprovalItemMapper;
import com.aicp.module.enterprise.service.ApprovalCommandRouter;
import com.aicp.module.enterprise.service.ApprovalCommandRouter.ApprovalDecisionCommand;
import com.aicp.module.enterprise.service.ApprovalCommandRouter.ApprovalType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
public class EnterpriseApprovalController {

    private final EnterpriseApprovalItemMapper itemMapper;
    private final ApprovalCommandRouter router;

    @GetMapping("/approvals")
    public ApiResponse<?> listApprovals(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String bucket,
                                         @RequestParam(required = false) String sourceType,
                                         @RequestParam(required = false) String departmentId,
                                         HttpServletRequest request) {
        var ctx = WorkspaceContext.get(request);
        var qw = new LambdaQueryWrapper<EnterpriseApprovalItem>()
                .eq(EnterpriseApprovalItem::getWorkspaceId, ctx.workspaceId());

        if ("mine".equals(bucket)) {
            qw.eq(EnterpriseApprovalItem::getRequesterUserId, ctx.userId());
        } else if ("processed".equals(bucket)) {
            qw.in(EnterpriseApprovalItem::getStatus, "APPROVED", "REJECTED");
        } else {
            // default: pending (待我处理)
            qw.eq(EnterpriseApprovalItem::getStatus, "PENDING");
        }
        if (sourceType != null) {
            qw.eq(EnterpriseApprovalItem::getSourceType, sourceType);
        }
        if (departmentId != null) {
            qw.eq(EnterpriseApprovalItem::getDepartmentId, departmentId);
        }
        qw.orderByDesc(EnterpriseApprovalItem::getLastEventAt);

        var result = itemMapper.selectPage(new Page<>(page, size), qw);
        return ApiResponse.success(result);
    }

    @GetMapping("/approvals/{type}/{id}")
    public ApiResponse<?> getApprovalDetail(@PathVariable String type,
                                             @PathVariable String id,
                                             HttpServletRequest request) {
        var ctx = WorkspaceContext.get(request);
        var item = itemMapper.selectOne(new LambdaQueryWrapper<EnterpriseApprovalItem>()
                .eq(EnterpriseApprovalItem::getSourceType, type)
                .eq(EnterpriseApprovalItem::getSourceId, id)
                .eq(EnterpriseApprovalItem::getWorkspaceId, ctx.workspaceId()));
        if (item == null) return ApiResponse.error(404, "approval item not found");
        return ApiResponse.success(item);
    }

    @PostMapping("/approvals/{type}/{id}/decisions")
    public ApiResponse<?> submitDecision(@PathVariable String type,
                                          @PathVariable String id,
                                          @RequestBody ApprovalDecisionRequest body,
                                          HttpServletRequest request) {
        var ctx = WorkspaceContext.get(request);
        try {
            ApprovalType approvalType = ApprovalType.valueOf(type.toUpperCase());
            var cmd = new ApprovalDecisionCommand(
                    body.approved(), body.reason(),
                    body.expectedVersion(), body.idempotencyKey());
            String result = router.route(ctx, approvalType, id, cmd);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, "unknown approval type: " + type);
        } catch (ApprovalCommandRouter.VersionConflictException e) {
            return ApiResponse.error(409, e.getMessage());
        } catch (ApprovalCommandRouter.AlreadyDecidedException e) {
            return ApiResponse.error(409, e.getMessage());
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
    }
}
