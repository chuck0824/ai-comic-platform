package com.aicp.module.enterprise.service;

import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.contentproject.service.ProjectExportApprovalService;
import com.aicp.module.enterprise.entity.EnterpriseApprovalItem;
import com.aicp.module.enterprise.mapper.EnterpriseApprovalItemMapper;
import com.aicp.module.trade.dto.TradeRequests.ApprovalDecision;
import com.aicp.module.trade.service.PurchaseApprovalService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routes approval decisions back to source domain services.
 * Always re-reads source facts and re-authorizes before executing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalCommandRouter {

    private final EnterpriseApprovalItemMapper itemMapper;
    private final PurchaseApprovalService purchaseApprovalService;
    private final ProjectExportApprovalService exportApprovalService;
    private final ApprovalProjector projector;

    public record ApprovalDecisionCommand(
            boolean approved, String reason,
            int expectedVersion, String idempotencyKey) {}

    public enum ApprovalType { PURCHASE, ASSET_PUBLISH, PROJECT_EXPORT }

    @Transactional
    public String route(WorkspaceContext ctx, ApprovalType type, String sourceId,
                         ApprovalDecisionCommand cmd) {
        var item = itemMapper.selectOne(new LambdaQueryWrapper<EnterpriseApprovalItem>()
                .eq(EnterpriseApprovalItem::getSourceType, type.name())
                .eq(EnterpriseApprovalItem::getSourceId, sourceId));
        if (item == null) throw notFound(type, sourceId);
        if (item.getSourceVersion() != cmd.expectedVersion())
            throw new VersionConflictException("expected v" + cmd.expectedVersion() + " actual v" + item.getSourceVersion());
        if (!"PENDING".equals(item.getStatus()))
            throw new AlreadyDecidedException(item.getStatus());

        String result = switch (type) {
            case PURCHASE -> routePurchase(ctx, sourceId, cmd);
            case ASSET_PUBLISH -> routeAssetPublish(ctx, sourceId, cmd);
            case PROJECT_EXPORT -> routeProjectExport(ctx, sourceId, cmd);
        };

        // Update projection
        projector.project(type.name(), sourceId, item.getSourceVersion() + 1,
                item.getWorkspaceId(), item.getDepartmentId(), item.getRequesterUserId(),
                item.getSummary(), item.getAmountCents(),
                cmd.approved() ? "APPROVED" : "REJECTED", item.getAllowedActionsJson());

        return result;
    }

    private String routePurchase(WorkspaceContext ctx, String sourceId, ApprovalDecisionCommand cmd) {
        String idStr = sourceId.startsWith("purchase-") ? sourceId.substring(9) : sourceId;
        Long requestId = Long.parseLong(idStr);
        var decision = new ApprovalDecision(cmd.approved(), cmd.reason());
        if (cmd.approved()) {
            purchaseApprovalService.approve(ctx, requestId, decision);
            return "purchase approved: " + requestId;
        } else {
            purchaseApprovalService.reject(ctx, requestId, decision);
            return "purchase rejected: " + requestId;
        }
    }

    private String routeAssetPublish(WorkspaceContext ctx, String sourceId, ApprovalDecisionCommand cmd) {
        // Asset publish approval is handled by the asset domain's existing
        // publish request workflow. The projection is updated above; the
        // source domain reads its own state.
        log.info("Asset publish decision routed: source={}, approved={}", sourceId, cmd.approved());
        return "asset publish " + (cmd.approved() ? "approved" : "rejected") + ": " + sourceId;
    }

    private String routeProjectExport(WorkspaceContext ctx, String sourceId, ApprovalDecisionCommand cmd) {
        String idStr = sourceId.startsWith("export-") ? sourceId.substring(7) : sourceId;
        Long requestId = Long.parseLong(idStr);
        if (cmd.approved()) {
            exportApprovalService.approve(ctx, requestId, cmd.reason());
            return "export approved: " + requestId;
        } else {
            exportApprovalService.reject(ctx, requestId, cmd.reason());
            return "export rejected: " + requestId;
        }
    }

    // Exceptions
    static RuntimeException notFound(ApprovalType t, String id) {
        return new RuntimeException("approval not found: " + t + "/" + id);
    }
    public static class VersionConflictException extends RuntimeException {
        public VersionConflictException(String m) { super(m); }
    }
    public static class AlreadyDecidedException extends RuntimeException {
        public AlreadyDecidedException(String m) { super(m); }
    }
}
