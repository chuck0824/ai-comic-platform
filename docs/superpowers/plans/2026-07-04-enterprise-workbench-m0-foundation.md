# Enterprise Workbench M0 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build authoritative multi-Workspace discovery, scoped membership, organization management, workspace switching, and a role-aware enterprise shell.

**Architecture:** Extend the 3001 account center as the only Workspace and organization source. The 8080 application verifies every enterprise request through an enriched `WorkspaceContext`, exposes a narrow BFF, and renders role-aware Vue routes without storing enterprise master data locally.

**Tech Stack:** Go, Gin, GORM, SQLite test fixtures, Java 17, Spring Boot 3, MyBatis-Plus, JUnit 5, Mockito, Vue 3, Pinia, Vue Router, Node test runner.

---

## File map

- Modify `new-api/model/aicp_workspace.go`: Workspace, department, role, grant, invitation, and enriched membership models.
- Modify `new-api/model/main.go`: migrate the added account-center tables.
- Modify `new-api/controller/aicp_workspace.go`: list, membership, department, member, invitation, and role handlers.
- Modify `new-api/controller/aicp_workspace_test.go`: API and tenant-isolation tests.
- Modify `new-api/router/api-router.go`: account-center routes.
- Modify `aicp-backend/src/main/java/com/aicp/common/workspace/AccountCenterPermissionClient.java`: parse scoped grants.
- Create `aicp-backend/src/main/java/com/aicp/module/enterprise/service/AccountCenterEnterpriseClient.java`: typed organization-management HTTP client.
- Modify `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceContext.java`: department and scoped authorization.
- Modify `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceContextFilter.java`: protect enterprise routes.
- Create `aicp-backend/src/main/java/com/aicp/module/enterprise/dto/EnterpriseViews.java`: BFF response records.
- Create `aicp-backend/src/main/java/com/aicp/module/enterprise/service/EnterpriseAccountFacade.java`: account-center BFF.
- Replace `aicp-backend/src/main/java/com/aicp/module/enterprise/controller/EnterpriseController.java`: real context and organization endpoints.
- Modify `aicp-backend/src/main/java/com/aicp/common/config/SecurityConfig.java`: remove role-name gate.
- Create `aicp-frontend/src/api/enterprise.js`: enterprise BFF client.
- Create `aicp-frontend/src/stores/workspaceState.js`: framework-free workspace transition rules.
- Create `aicp-frontend/src/stores/workspace.js`: Pinia wrapper and cache boundary.
- Create `aicp-frontend/src/views/enterprise/EnterpriseShell.vue`: enterprise child navigation.
- Create `aicp-frontend/src/views/enterprise/EnterpriseOverview.vue`: role-aware overview.
- Create `aicp-frontend/src/views/enterprise/EnterpriseOrganization.vue`: department/member management.
- Modify `aicp-frontend/src/components/Topbar.vue`, `aicp-frontend/src/router/index.js`, and `aicp-frontend/src/stores/auth.js`: switcher and routes.

### Task 1: Add authoritative organization models to 3001

**Files:**
- Modify: `new-api/model/aicp_workspace.go`
- Modify: `new-api/model/main.go`
- Test: `new-api/model/aicp_workspace_test.go`

- [ ] **Step 1: Write the failing model test**

```go
func openWorkspaceModelTestDB(t *testing.T) *gorm.DB {
    t.Helper()
    db, err := gorm.Open(sqlite.Open("file:"+t.Name()+"?mode=memory&cache=shared"), &gorm.Config{})
    require.NoError(t, err)
    DB = db
    return db
}

func TestWorkspaceOrganizationModelsPersistScopedGrant(t *testing.T) {
    db := openWorkspaceModelTestDB(t)
    require.NoError(t, db.AutoMigrate(
        &AicpWorkspace{}, &AicpDepartment{}, &AicpWorkspaceRole{},
        &AicpRolePermissionGrant{}, &AicpWorkspaceMember{}, &AicpWorkspaceInvitation{},
    ))
    ws := AicpWorkspace{ID: "ent_100", Type: "enterprise", Name: "星辰动漫", Status: "active", OwnerUserID: 9}
    require.NoError(t, db.Create(&ws).Error)
    dept := AicpDepartment{ID: "dept_content", WorkspaceID: ws.ID, Name: "内容一部", Status: "active"}
    require.NoError(t, db.Create(&dept).Error)
    role := AicpWorkspaceRole{ID: "role_head", WorkspaceID: ws.ID, Name: "部门负责人", Status: "active"}
    require.NoError(t, db.Create(&role).Error)
    grant := AicpRolePermissionGrant{RoleID: role.ID, Permission: "trade.purchase.approve", Scope: "DEPARTMENT", ScopeIDs: `["dept_content"]`}
    require.NoError(t, db.Create(&grant).Error)
    assert.Equal(t, "DEPARTMENT", grant.Scope)
}
```

- [ ] **Step 2: Run the model test and verify it fails**

Run: `cd new-api && go test ./model -run TestWorkspaceOrganizationModelsPersistScopedGrant -count=1`

Expected: FAIL because the organization structs are undefined.

- [ ] **Step 3: Add the models and migrations**

Implement these exact fields in `aicp_workspace.go`:

```go
type AicpWorkspace struct { ID string `gorm:"primaryKey;size:64"`; Type string; Name string; Status string; VerifyStatus string; OwnerUserID int64; MemberLimit int }
type AicpDepartment struct { ID string `gorm:"primaryKey;size:64"`; WorkspaceID string `gorm:"index;size:64;not null"`; ParentID string; Name string; ManagerMemberID uint; Status string; SortOrder int }
type AicpWorkspaceRole struct { ID string `gorm:"primaryKey;size:64"`; WorkspaceID string `gorm:"index;size:64;not null"`; Name string; SystemTemplate bool; Status string }
type AicpRolePermissionGrant struct { ID uint `gorm:"primaryKey"`; RoleID string `gorm:"index;size:64;not null"`; Permission string; Scope string; ScopeIDs string `gorm:"type:text"` }
type AicpWorkspaceInvitation struct { ID string `gorm:"primaryKey;size:64"`; WorkspaceID string; Target string; DepartmentID string; RoleID string; TokenDigest string; Status string; ExpiresAt time.Time; InvitedBy int64 }
```

Extend `AicpWorkspaceMember` with `DepartmentID string`, `RoleID string`, and `JoinedAt time.Time`. Register all six models in both migration lists in `model/main.go`.

- [ ] **Step 4: Run the focused and package tests**

Run: `cd new-api && go test ./model -run 'WorkspaceOrganization|AicpWorkspace' -count=1`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add new-api/model/aicp_workspace.go new-api/model/aicp_workspace_test.go new-api/model/main.go
git commit -m "feat: model account-center organizations"
```

### Task 2: Expose workspace discovery and scoped membership

**Files:**
- Modify: `new-api/model/aicp_workspace.go`
- Modify: `new-api/controller/aicp_workspace.go`
- Modify: `new-api/controller/aicp_workspace_test.go`
- Modify: `new-api/router/api-router.go`

- [ ] **Step 1: Add failing controller tests**

Add tests asserting `GET /api/aicp/workspaces` returns only active memberships and membership contains `department_id`, `roles`, `permissions`, and `permission_grants`. Use this response contract:

```json
{"success":true,"data":{"items":[{"workspace_id":"ent_100","workspace_type":"enterprise","name":"星辰动漫","role":"dept_head"}]}}
```

and this grant:

```json
{"permission":"trade.purchase.approve","scope":"DEPARTMENT","scope_ids":["dept_content"]}
```

- [ ] **Step 2: Verify failure**

Run: `cd new-api && go test ./controller -run 'TestListAicpWorkspaces|TestGetAicpWorkspaceMembershipReturnsGrants' -count=1`

Expected: FAIL because the list handler and enriched fields do not exist.

- [ ] **Step 3: Implement queries and handlers**

Add `ListActiveWorkspacesForUser(userID int64)`, enrich `FindActiveWorkspaceMembership`, implement `ListAicpWorkspaces`, and register:

```go
aicpRoute.GET("/workspaces", controller.ListAicpWorkspaces)
aicpRoute.GET("/workspaces/:id/membership", controller.GetAicpWorkspaceMembership)
```

Decode `ScopeIDs` as `[]string`; malformed JSON returns an empty list and logs an error. Never accept a user ID from query or body.

- [ ] **Step 4: Run controller tests**

Run: `cd new-api && go test ./controller -run 'AicpWorkspace' -count=1`

Expected: PASS, including existing tenant-hiding tests.

- [ ] **Step 5: Commit**

```bash
git add new-api/model/aicp_workspace.go new-api/controller/aicp_workspace.go new-api/controller/aicp_workspace_test.go new-api/router/api-router.go
git commit -m "feat: expose workspace discovery and scoped membership"
```

### Task 3: Add department, member, invitation, and role APIs

**Files:**
- Modify: `new-api/controller/aicp_workspace.go`
- Modify: `new-api/controller/aicp_workspace_test.go`
- Modify: `new-api/router/api-router.go`

- [ ] **Step 1: Add failing security and invariant tests**

Cover: cross-Workspace department parent rejected; non-empty department deletion rejected; duplicate active invitation rejected; member limit rejected; last active Workspace administrator cannot be disabled; caller cannot grant a permission they do not hold.

- [ ] **Step 2: Verify failure**

Run: `cd new-api && go test ./controller -run 'AicpDepartment|AicpInvitation|LastWorkspaceAdmin|AicpRole' -count=1`

Expected: FAIL with missing handlers.

- [ ] **Step 3: Implement and register organization routes**

Register the exact routes from design section 10.1. Every handler must obtain `aicp_user_id` from Gin context, call `FindActiveWorkspaceMembership`, require the matching `org.*` permission, and constrain every query by `workspace_id`. Return `409` for invariant conflicts and unified `404` for inaccessible resources.

- [ ] **Step 4: Run the workspace controller suite**

Run: `cd new-api && go test ./controller -run 'Aicp' -count=1`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add new-api/controller/aicp_workspace.go new-api/controller/aicp_workspace_test.go new-api/router/api-router.go
git commit -m "feat: manage workspace organizations"
```

### Task 4: Enrich the trusted 8080 WorkspaceContext

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/common/workspace/AccountCenterPermissionClient.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceContext.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceAccessService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceContextFilter.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/config/SecurityConfig.java`
- Test: `aicp-backend/src/test/java/com/aicp/common/workspace/WorkspaceAccessServiceTest.java`

- [ ] **Step 1: Add failing scope tests**

```java
var ctx = new WorkspaceContext("ent_100", "enterprise", 9L, "dept_content",
        Set.of("trade.purchase.approve"),
        List.of(new PermissionGrant("trade.purchase.approve", "DEPARTMENT", Set.of("dept_content"))));
assertThat(ctx.canAccess("trade.purchase.approve", "dept_content", 10L)).isTrue();
assertThat(ctx.canAccess("trade.purchase.approve", "dept_art", 10L)).isFalse();
```

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=WorkspaceAccessServiceTest test`

Expected: compilation failure because `PermissionGrant` and scoped context fields are absent.

- [ ] **Step 3: Implement scoped context**

Create `PermissionGrant` as a record in `WorkspaceContext.java`, add `departmentId` and `grants`, and implement `canAccess(permission, targetDepartmentId, targetUserId)` for `WORKSPACE`, `DEPARTMENT`, and `SELF`. Parse the enriched 3001 response. Remove the permissive dev membership fallback（参见设计文档第 12 节：开发环境不得使用宽松 Membership Mock）；tests and dev seed data must use explicit memberships. Add `/api/v1/enterprise/**` to `WorkspaceContextFilter` and replace the broad `ent_admin/dept_head` matcher with authenticated access plus service-level permission checks.

- [ ] **Step 4: Run workspace and security tests**

Run: `cd aicp-backend && mvn -Dtest='WorkspaceAccessServiceTest,*Security*Test' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/common/workspace aicp-backend/src/main/java/com/aicp/common/config/SecurityConfig.java aicp-backend/src/test/java/com/aicp/common/workspace/WorkspaceAccessServiceTest.java
git commit -m "feat: enforce scoped workspace authorization"
```

### Task 5: Replace the enterprise stub with a 3001 BFF

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/dto/EnterpriseViews.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/service/AccountCenterEnterpriseClient.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/service/EnterpriseAccountFacade.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/enterprise/controller/EnterpriseController.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/enterprise/service/EnterpriseAccountFacadeTest.java`

- [ ] **Step 1: Write failing facade tests**

Test that context returns workspace identity, current member, visible menu keys, and `allowedActions`; organization mutations forward to 3001 and invalidate cached reads; upstream `404`, `409`, and `503` remain distinguishable.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=EnterpriseAccountFacadeTest test`

Expected: FAIL because the facade does not exist.

- [ ] **Step 3: Implement the BFF**

Define response records `EnterpriseContextView`, `DepartmentView`, `MemberView`, and `RoleView`. `AccountCenterEnterpriseClient` exposes typed `listWorkspaces`, `listDepartments`, `createDepartment`, `listMembers`, `inviteMember`, `updateMember`, `listRoles`, and `updateRole` methods and forwards the original bearer token. Implement context plus the department/member/invitation/role routes from design section 10.2. The controller obtains `WorkspaceContext` from the request attribute and never accepts a trusted Workspace ID in the body. Delete all hard-coded maps from `EnterpriseController`.

- [ ] **Step 4: Run enterprise backend tests**

Run: `cd aicp-backend && mvn -Dtest='EnterpriseAccountFacadeTest,WorkspaceAccessServiceTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/enterprise aicp-backend/src/test/java/com/aicp/module/enterprise
git commit -m "feat: proxy enterprise organization management"
```

### Task 6: Add Workspace switching and the enterprise shell

**Files:**
- Create: `aicp-frontend/src/api/enterprise.js`
- Create: `aicp-frontend/src/stores/workspaceState.js`
- Create: `aicp-frontend/src/stores/workspace.js`
- Create: `aicp-frontend/src/views/enterprise/EnterpriseShell.vue`
- Create: `aicp-frontend/src/views/enterprise/EnterpriseOverview.vue`
- Create: `aicp-frontend/src/views/enterprise/EnterpriseOrganization.vue`
- Modify: `aicp-frontend/src/components/Topbar.vue`
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/src/stores/auth.js`
- Test: `aicp-frontend/tests/enterprise-workspace-state.test.js`
- Test: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Add failing state tests**

Test `selectWorkspace` preserves the old workspace when membership loading fails, clears Workspace-scoped cache keys after success, and falls back to `personal_{uid}` when the active membership disappears.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-frontend && node --test tests/enterprise-workspace-state.test.js tests/navigation-contract.test.js`

Expected: FAIL because the workspace store and nested routes do not exist.

- [ ] **Step 3: Implement the frontend state and routes**

`workspaceState.js` exports pure functions `commitWorkspaceSelection(current, candidate, membership)` and `personalFallback(userId)`. `workspace.js` exports state `items`, `activeId`, `activeType`, `membership`, `loading`, plus actions `loadWorkspaces()`, `selectWorkspace(id)`, and `fallbackToPersonal()`. `selectWorkspace` must fetch membership before changing local storage. Add nested enterprise routes for `overview` and `organization`; menu visibility comes from `/enterprise/context`. Replace `deriveAndStoreWorkspace` with initialization through the workspace store after login.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/enterprise-workspace-state.test.js tests/navigation-contract.test.js && npm run build`

Expected: all tests PASS and Vite build exits 0.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/api/enterprise.js aicp-frontend/src/stores aicp-frontend/src/views/enterprise aicp-frontend/src/components/Topbar.vue aicp-frontend/src/router/index.js aicp-frontend/tests
git commit -m "feat: add enterprise workspace shell"
```

### Task 7: Verify M0 end to end

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/enterprise/EnterpriseFoundationE2ETest.java`
- Modify: `aicp-frontend/tests/enterprise-workspace-state.test.js`

- [ ] **Step 1: Add the E2E contract**

Cover personal plus two enterprise workspaces, administrator and department-head contexts, cross-Workspace member access denial, last-admin protection, and stale-membership fallback.

- [ ] **Step 2: Run all focused suites**

Run: `cd new-api && go test ./model ./controller -run 'Aicp|Workspace' -count=1`

Run: `cd aicp-backend && mvn -Dtest='WorkspaceAccessServiceTest,Enterprise*Test' test`

Run: `cd aicp-frontend && node --test tests/enterprise-workspace-state.test.js tests/navigation-contract.test.js && npm run build`

Expected: every command exits 0.

- [ ] **Step 3: Commit the E2E coverage**

```bash
git add aicp-backend/src/test/java/com/aicp/module/enterprise/EnterpriseFoundationE2ETest.java aicp-frontend/tests/enterprise-workspace-state.test.js
git commit -m "test: verify enterprise workspace foundation"
```
