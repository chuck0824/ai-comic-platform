package com.aicp.module.enterprise.dto;

import java.util.List;
import java.util.Map;

/**
 * Response DTOs for the enterprise BFF.
 */
public final class EnterpriseViews {

    private EnterpriseViews() {}

    public record EnterpriseContextView(
            String workspaceId,
            String workspaceType,
            String name,
            Long userId,
            String departmentId,
            List<String> roles,
            List<String> permissions,
            List<String> visibleMenuKeys,
            Map<String, Boolean> allowedActions) {}

    public record DepartmentView(
            String id,
            String workspaceId,
            String parentId,
            String name,
            Long managerMemberId,
            String status,
            Integer sortOrder) {}

    public record MemberView(
            Long id,
            String workspaceId,
            Long userId,
            String departmentId,
            String roleId,
            String status,
            String permissions) {}

    public record RoleView(
            String id,
            String workspaceId,
            String name,
            boolean systemTemplate,
            String status) {}

    public record DashboardView(
            Integer memberCount,
            Integer memberLimit,
            Long availableCents,
            Long frozenCents,
            Long budgetAvailableCents,
            Long budgetReservedCents,
            Long budgetConsumedCents,
            Integer pendingApprovals,
            Integer activeProjects,
            String balanceUpdatedAt,
            String budgetUpdatedAt) {}
}
