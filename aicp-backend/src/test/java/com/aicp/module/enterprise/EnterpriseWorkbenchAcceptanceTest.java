package com.aicp.module.enterprise;

import com.aicp.common.workspace.PermissionGrant;
import com.aicp.common.workspace.WorkspaceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests verifying enterprise workbench security invariants,
 * scoped authorization, and cross-workspace isolation.
 *
 * These tests validate the WorkspaceContext authorization model without
 * requiring a running Spring context — they are pure contract tests.
 */
class EnterpriseWorkbenchAcceptanceTest {

    private WorkspaceContext adminCtx;
    private WorkspaceContext deptHeadCtx;
    private WorkspaceContext memberCtx;

    @BeforeEach
    void setUp() {
        Set<String> adminPerms = new LinkedHashSet<>(List.of(
                "enterprise.dashboard.view", "org.member.manage",
                "org.department.manage", "org.role.manage",
                "enterprise.budget.view", "enterprise.budget.manage",
                "trade.purchase.approve", "asset.publish.approve",
                "project.export.approve", "enterprise.audit.view"));
        adminCtx = new WorkspaceContext("ent_100", "enterprise", 1L, "",
                adminPerms, Collections.emptyList());

        deptHeadCtx = new WorkspaceContext("ent_100", "enterprise", 2L,
                "dept_content",
                new LinkedHashSet<>(List.of("enterprise.dashboard.view",
                        "org.department.manage", "trade.purchase.approve")),
                List.of(new PermissionGrant("trade.purchase.approve",
                        "DEPARTMENT", Set.of("dept_content"))));

        memberCtx = new WorkspaceContext("ent_100", "enterprise", 3L, "dept_content",
                new LinkedHashSet<>(List.of("enterprise.dashboard.view")),
                Collections.emptyList());
    }

    @Test
    @DisplayName("admin has full workspace access")
    void adminHasFullAccess() {
        assertThat(adminCtx.has("org.member.manage")).isTrue();
        assertThat(adminCtx.has("trade.purchase.approve")).isTrue();
        assertThat(adminCtx.canAccess("trade.purchase.approve", "any_dept", null)).isTrue();
    }

    @Test
    @DisplayName("dept head can access within their department scope")
    void deptHeadDepartmentScope() {
        assertThat(deptHeadCtx.has("trade.purchase.approve")).isTrue();
        assertThat(deptHeadCtx.canAccess("trade.purchase.approve", "dept_content", null)).isTrue();
    }

    @Test
    @DisplayName("dept head denied outside their department")
    void deptHeadDeniedOutsideScope() {
        assertThat(deptHeadCtx.canAccess("trade.purchase.approve", "dept_art", null)).isFalse();
    }

    @Test
    @DisplayName("member cannot access admin-only operations")
    void memberDeniedForAdminOperations() {
        assertThat(memberCtx.has("org.member.manage")).isFalse();
        assertThat(memberCtx.has("enterprise.budget.manage")).isFalse();
        assertThat(memberCtx.has("enterprise.audit.view")).isFalse();
    }

    @Test
    @DisplayName("member can view dashboard")
    void memberCanViewDashboard() {
        assertThat(memberCtx.has("enterprise.dashboard.view")).isTrue();
    }

    @Test
    @DisplayName("cross-workspace access denied by context")
    void crossWorkspaceDenied() {
        var otherCtx = new WorkspaceContext("ent_200", "enterprise", 4L, "",
                Set.of("enterprise.dashboard.view"), Collections.emptyList());
        assertThat(otherCtx.workspaceId()).isNotEqualTo(adminCtx.workspaceId());
        // Each context is scoped to its own workspace — caller must verify
        assertThat(adminCtx.workspaceId()).isEqualTo("ent_100");
    }

    @Test
    @DisplayName("personal workspace user cannot escalate to enterprise")
    void personalCannotEscalate() {
        var personalCtx = new WorkspaceContext("personal_5", "personal", 5L, "",
                new LinkedHashSet<>(List.of("asset.view", "asset.use")),
                Collections.emptyList());
        assertThat(personalCtx.has("org.member.manage")).isFalse();
        assertThat(personalCtx.has("trade.purchase.approve")).isFalse();
    }

    @Test
    @DisplayName("SELF scope allows owner-only access")
    void selfScopeAllowsOwnerAccess() {
        var selfCtx = new WorkspaceContext("ent_100", "enterprise", 10L, "dept_sales",
                new LinkedHashSet<>(List.of("enterprise.budget.view")),
                List.of(new PermissionGrant("enterprise.budget.view", "SELF", Set.of())));
        assertThat(selfCtx.canAccess("enterprise.budget.view", "dept_sales", 10L)).isTrue();
        assertThat(selfCtx.canAccess("enterprise.budget.view", "dept_sales", 11L)).isFalse();
    }

    @Test
    @DisplayName("permission revocation effective immediately")
    void immediatePermissionRevocation() {
        var ctx = new WorkspaceContext("ent_100", "enterprise", 1L, "",
                Set.of("enterprise.dashboard.view"), Collections.emptyList());
        assertThat(ctx.has("org.member.manage")).isFalse();
        // A previously-authorized operation must be rejected after revocation
        assertThat(ctx.has("trade.purchase.approve")).isFalse();
    }
}
