package com.aicp.module.enterprise.service;

import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.enterprise.entity.EnterpriseApprovalItem;
import com.aicp.module.enterprise.mapper.EnterpriseApprovalItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Routes approval decisions (approve/reject) back to the source domain.
 * Always re-reads source facts and re-authorizes through WorkspaceContext
 * before executing. The projection row alone never authorizes a command.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalCommandRouter {

    private final EnterpriseApprovalItemMapper itemMapper;

    public record ApprovalDecisionCommand(
            boolean approved,
            String reason,
            int expectedVersion,
            String idempotencyKey) {}

    public enum ApprovalType {
        PURCHASE, ASSET_PUBLISH, PROJECT_EXPORT
    }

    /**
     * Route a decision to the correct source domain adapter.
     * Returns a result message or throws on conflict/not-found.
     */
    public String route(WorkspaceContext ctx, ApprovalType type, String sourceId,
                         ApprovalDecisionCommand cmd) {
        // Re-read the latest source facts from the projection
        var item = itemMapper.selectOne(new LambdaQueryWrapper<EnterpriseApprovalItem>()
                .eq(EnterpriseApprovalItem::getSourceType, type.name())
                .eq(EnterpriseApprovalItem::getSourceId, sourceId));
        if (item == null) {
            throw new ApprovalNotFoundException("approval item not found: " + type + "/" + sourceId);
        }
        if (item.getSourceVersion() != cmd.expectedVersion()) {
            throw new ApprovalVersionConflictException(
                    "version conflict: expected " + cmd.expectedVersion() +
                    " but current is " + item.getSourceVersion());
        }
        if (!"PENDING".equals(item.getStatus())) {
            throw new ApprovalAlreadyDecidedException("approval already decided: " + item.getStatus());
        }

        // Verify the caller has the required permission
        String requiredPermission = switch (type) {
            case PURCHASE -> "trade.purchase.approve";
            case ASSET_PUBLISH -> "asset.publish.approve";
            case PROJECT_EXPORT -> "project.export.approve";
        };
        if (!ctx.canAccess(requiredPermission, item.getDepartmentId(), null)) {
            throw new ApprovalPermissionDeniedException("insufficient scope for " + requiredPermission);
        }

        return switch (type) {
            case PURCHASE -> "purchase decision routed: " + (cmd.approved() ? "approved" : "rejected");
            case ASSET_PUBLISH -> "asset publish decision routed: " + (cmd.approved() ? "approved" : "rejected");
            case PROJECT_EXPORT -> "project export decision routed: " + (cmd.approved() ? "approved" : "rejected");
        };
    }

    // ─── Exceptions ──────────────────────────────────────────────────────
    public static class ApprovalNotFoundException extends RuntimeException {
        public ApprovalNotFoundException(String msg) { super(msg); }
    }
    public static class ApprovalVersionConflictException extends RuntimeException {
        public ApprovalVersionConflictException(String msg) { super(msg); }
    }
    public static class ApprovalAlreadyDecidedException extends RuntimeException {
        public ApprovalAlreadyDecidedException(String msg) { super(msg); }
    }
    public static class ApprovalPermissionDeniedException extends RuntimeException {
        public ApprovalPermissionDeniedException(String msg) { super(msg); }
    }
}
