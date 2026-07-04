package com.aicp.module.enterprise.service;

import com.aicp.common.workspace.WorkspaceContext;
import com.aicp.module.enterprise.dto.EnterpriseViews.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Enterprise BFF that aggregates 3001 account-center data and enriches it
 * with 8080 business facts. Never stores enterprise master data locally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseAccountFacade {

    private final AccountCenterEnterpriseClient client;

    /**
     * Build the enterprise context: workspace identity, visible menus, allowed actions.
     */
    public EnterpriseContextView getContext(WorkspaceContext ctx, String bearerToken) {
        boolean isAdmin = ctx.has("org.member.manage");
        boolean isDeptHead = ctx.has("org.department.manage");
        boolean canViewBudget = ctx.has("enterprise.budget.view");
        boolean canAudit = ctx.has("enterprise.audit.view");

        List<String> menus = new ArrayList<>();
        menus.add("overview");
        if (isAdmin || isDeptHead) {
            menus.add("organization");
        }
        if (canViewBudget || isAdmin) {
            menus.add("budgets");
        }
        if (ctx.has("trade.purchase.approve") || ctx.has("asset.publish.approve")
                || ctx.has("project.export.approve")
                || ctx.has("trade.purchase.request") || ctx.has("asset.publish.request")
                || ctx.has("project.export.request")) {
            menus.add("approvals");
        }
        if (canAudit) {
            menus.add("audit");
        }

        Map<String, Boolean> actions = new LinkedHashMap<>();
        actions.put("canManageOrg", isAdmin);
        actions.put("canManageBudget", ctx.has("enterprise.budget.manage"));
        actions.put("canViewBudget", canViewBudget);
        actions.put("canAudit", canAudit);
        actions.put("canRequestPurchase", ctx.has("trade.purchase.request"));
        actions.put("canApprovePurchase", ctx.has("trade.purchase.approve"));
        actions.put("canPayPurchase", ctx.has("trade.purchase.pay"));
        actions.put("canRequestAssetPublish", ctx.has("asset.publish.request"));
        actions.put("canApproveAssetPublish", ctx.has("asset.publish.approve"));
        actions.put("canRequestExport", ctx.has("project.export.request"));
        actions.put("canApproveExport", ctx.has("project.export.approve"));

        return new EnterpriseContextView(
                ctx.workspaceId(), ctx.workspaceType(), "",
                ctx.userId(), ctx.departmentId(),
                Collections.emptyList(), // roles from context
                new ArrayList<>(ctx.permissions()),
                menus, actions);
    }

    // ─── Departments (proxy to 3001) ────────────────────────────────────────

    public JsonNode listDepartments(WorkspaceContext ctx, String bearerToken) {
        return client.listDepartments(ctx.workspaceId(), bearerToken);
    }

    public JsonNode createDepartment(WorkspaceContext ctx, Map<String, Object> body, String bearerToken) {
        return client.createDepartment(ctx.workspaceId(), body, bearerToken);
    }

    public JsonNode updateDepartment(WorkspaceContext ctx, String departmentId,
                                      Map<String, Object> body, String bearerToken) {
        return client.updateDepartment(ctx.workspaceId(), departmentId, body, bearerToken);
    }

    public JsonNode deleteDepartment(WorkspaceContext ctx, String departmentId, String bearerToken) {
        return client.deleteDepartment(ctx.workspaceId(), departmentId, bearerToken);
    }

    // ─── Members ────────────────────────────────────────────────────────────

    public JsonNode listMembers(WorkspaceContext ctx, int page, int size, String bearerToken) {
        return client.listMembers(ctx.workspaceId(), page, size, bearerToken);
    }

    public JsonNode updateMember(WorkspaceContext ctx, Long memberId,
                                  Map<String, Object> body, String bearerToken) {
        return client.updateMember(ctx.workspaceId(), memberId, body, bearerToken);
    }

    // ─── Invitations ────────────────────────────────────────────────────────

    public JsonNode createInvitation(WorkspaceContext ctx, Map<String, Object> body, String bearerToken) {
        return client.createInvitation(ctx.workspaceId(), body, bearerToken);
    }

    // ─── Roles ──────────────────────────────────────────────────────────────

    public JsonNode listRoles(WorkspaceContext ctx, String bearerToken) {
        return client.listRoles(ctx.workspaceId(), bearerToken);
    }

    public JsonNode createRole(WorkspaceContext ctx, Map<String, Object> body, String bearerToken) {
        return client.createRole(ctx.workspaceId(), body, bearerToken);
    }

    public JsonNode updateRole(WorkspaceContext ctx, String roleId,
                                Object body, String bearerToken) {
        return client.updateRole(ctx.workspaceId(), roleId, body, bearerToken);
    }

    // ─── Billing ────────────────────────────────────────────────────────────

    public JsonNode getBillingSummary(WorkspaceContext ctx, String bearerToken) {
        return client.getBillingSummary(ctx.workspaceId(), bearerToken);
    }
}
