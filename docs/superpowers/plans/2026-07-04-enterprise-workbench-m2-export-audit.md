# Enterprise Workbench M2 Export and Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete project export approval, cross-domain enterprise audit search, failure degradation, migration cleanup, and final acceptance.

**Architecture:** The project domain owns export approval and asynchronous export creation. Enterprise projections index approvals and audit references only; task center owns execution progress. Migration freezes duplicate 8080 enterprise master data after verified conversion to 3001 Workspace IDs.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, H2/MySQL, JUnit 5, Vue 3, Node tests, Markdown documentation.

---

### Task 1: Add the project export request state machine

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V8__project_export_approval.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ProjectExportRequest.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/ProjectExportRequestMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectExportApprovalService.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ProjectExportApprovalServiceTest.java`

- [ ] **Step 1: Add failing state tests**

Test submit fixes project version and export scope; approve creates exactly one export job; reject requires reason; requester may cancel only `PENDING`; expired requests cannot be approved; `APPROVED` is distinct from export task success.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=ProjectExportApprovalServiceTest test`

Expected: FAIL because export approval is absent.

- [ ] **Step 3: Implement the state machine**

Use statuses `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, and `EXPIRED`; store Workspace, department, project/version, export scope JSON, format, watermark policy, delivery target, compliance evidence reference, reviewer, reason, row version, and timestamps. Require `project.export.request` or `project.export.approve` with scoped object access.

- [ ] **Step 4: Run project export tests**

Run: `cd aicp-backend && mvn -Dtest='ProjectExportApprovalServiceTest,ContentProject*Test' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/resources/db aicp-backend/src/main/java/com/aicp/module/contentproject aicp-backend/src/test/java/com/aicp/module/contentproject/service/ProjectExportApprovalServiceTest.java
git commit -m "feat: add project export approval"
```

### Task 2: Connect export requests to unified approvals and task center

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/enterprise/service/ApprovalProjector.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/enterprise/service/ApprovalCommandRouter.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectExportApprovalService.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/enterprise/ProjectExportApprovalAdapterTest.java`

- [ ] **Step 1: Add failing adapter tests**

Assert export events create `PROJECT_EXPORT` inbox rows; detail includes compliance evidence and watermark policy; approval routes to project service; the resulting export job is linked to task center while the approval item stays terminal.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=ProjectExportApprovalAdapterTest test`

Expected: FAIL because the adapter is absent.

- [ ] **Step 3: Implement the adapter**

Emit versioned Outbox events on every export request transition. Project only summary fields into the inbox. Return a task link after approval; never project task `RUNNING/SUCCEEDED/FAILED` into approval status.

- [ ] **Step 4: Run approval regression**

Run: `cd aicp-backend && mvn -Dtest='ProjectExportApprovalAdapterTest,EnterpriseApprovalE2ETest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/enterprise/service aicp-backend/src/main/java/com/aicp/module/contentproject/service aicp-backend/src/test/java/com/aicp/module/enterprise/ProjectExportApprovalAdapterTest.java
git commit -m "feat: surface export approvals in enterprise inbox"
```

### Task 3: Add the rebuildable enterprise audit index

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V9__enterprise_audit_index.sql`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/entity/EnterpriseAuditIndex.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/mapper/EnterpriseAuditIndexMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/service/EnterpriseAuditProjector.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/controller/EnterpriseAuditController.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/enterprise/service/EnterpriseAuditProjectorTest.java`

- [ ] **Step 1: Add failing audit tests**

Cover duplicate event idempotency, Workspace and department filtering, sensitive summary redaction, source reference retention, and source-record `404` handling.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=EnterpriseAuditProjectorTest test`

Expected: FAIL because the index is absent.

- [ ] **Step 3: Implement audit projection and API**

Store Workspace, department, actor, action, object type/ID, result, source domain/record ID, request ID, redacted summary, event ID, and timestamp. Expose server pagination and filters at `/api/v1/enterprise/audit-events`; require `enterprise.audit.view` and apply grant scope.

- [ ] **Step 4: Run audit tests**

Run: `cd aicp-backend && mvn -Dtest='EnterpriseAuditProjectorTest,Enterprise*Test' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/resources/db/migration/V9__enterprise_audit_index.sql aicp-backend/src/main/java/com/aicp/module/enterprise aicp-backend/src/test/java/com/aicp/module/enterprise/service/EnterpriseAuditProjectorTest.java
git commit -m "feat: index enterprise audit events"
```

### Task 4: Finish export and audit UI with degradation states

**Files:**
- Create: `aicp-frontend/src/views/enterprise/EnterpriseAudit.vue`
- Modify: `aicp-frontend/src/views/enterprise/EnterpriseApprovals.vue`
- Modify: `aicp-frontend/src/views/enterprise/components/ApprovalDetailDrawer.vue`
- Modify: `aicp-frontend/src/views/enterprise/EnterpriseOverview.vue`
- Modify: `aicp-frontend/src/api/enterprise.js`
- Test: `aicp-frontend/tests/enterprise-export-audit-state.test.js`

- [ ] **Step 1: Add failing UI state tests**

Test export-specific evidence, approval-to-task link, audit filter serialization, partial dashboard card failure, projection-syncing label, membership-revoked fallback, and separate budget versus wallet errors.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-frontend && node --test tests/enterprise-export-audit-state.test.js`

Expected: FAIL because the state helpers and audit page are absent.

- [ ] **Step 3: Implement UI states**

Add the audit route and export drawer fields. A 3001 membership error blocks the full enterprise page; independent metric failures remain local to their cards with source update time and retry. A `409` decision reloads source detail before enabling actions again.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/enterprise-*.test.js tests/navigation-contract.test.js && npm run build`

Expected: PASS and build exits 0.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/enterprise aicp-frontend/src/api/enterprise.js aicp-frontend/tests
git commit -m "feat: complete enterprise export and audit UI"
```

### Task 5: Migrate and freeze duplicate enterprise master data

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/service/EnterpriseMasterDataMigrationService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/enterprise/service/EnterpriseMasterDataMigrationServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/enterprise/controller/EnterpriseController.java`
- Modify: `aicp-backend/src/main/resources/db/schema.sql`

- [ ] **Step 1: Add failing migration tests**

Cover stable legacy-enterprise-to-Workspace mapping, business reference conversion, count/status reconciliation, rerun idempotency, unresolved ownership quarantine, and rejection of all local enterprise writes after cutover.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=EnterpriseMasterDataMigrationServiceTest test`

Expected: FAIL because migration and freeze logic are absent.

- [ ] **Step 3: Implement migration and freeze**

Produce a report containing migrated, matched, quarantined, and failed counts. Never map unresolved data to user `1` or a default Workspace. Remove `/enterprise/register`, local profile writes, and local member writes after the reconciliation gate passes; keep legacy tables read-only for the stabilization window.

- [ ] **Step 4: Run migration tests**

Run: `cd aicp-backend && mvn -Dtest='EnterpriseMasterDataMigrationServiceTest,Enterprise*Test' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/enterprise aicp-backend/src/test/java/com/aicp/module/enterprise/service/EnterpriseMasterDataMigrationServiceTest.java aicp-backend/src/main/resources/db/schema.sql
git commit -m "refactor: retire local enterprise master writes"
```

### Task 6: Synchronize architecture documentation

**Files:**
- Modify: `docs/01-core/用户端产品功能设计.md`
- Modify: `docs/01-core/后端产品功能设计_V1.5.md`
- Modify: `docs/01-core/API接口文档_V1.5.md`
- Modify: `docs/01-core/new-api对接技术规划_V1.5.md`
- Modify: `docs/02-derived/流程图文档.md`

- [ ] **Step 1: Update the five documents**

Use the approved design as the canonical wording for 3001 ownership, 8080 BFF responsibilities, Workspace switching, scoped permissions, procurement budgets, three approval types, and explicit payment after purchase approval.

- [ ] **Step 2: Run the contradiction scan**

Run:

```bash
rg -n "user-svc 是账号事实源|new-api 影子用户|8080 企业成员主表|ent_admin/dept_head 统一门禁|审批通过自动扣款" docs/01-core docs/02-derived
```

Expected: no unqualified active-architecture statements; historical mentions explicitly point to the superseding design.

- [ ] **Step 3: Commit**

```bash
git add docs/01-core docs/02-derived/流程图文档.md
git commit -m "docs: align enterprise workspace architecture"
```

### Task 7: Run final acceptance

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/enterprise/EnterpriseWorkbenchAcceptanceTest.java`
- Modify: `aicp-frontend/tests/enterprise-export-audit-state.test.js`

- [ ] **Step 1: Add acceptance scenarios**

Cover multi-Workspace isolation; immediate permission revocation; administrator, department-head, and member views; purchase budget concurrency; purchase/asset/export approvals; explicit payment; audit linkage; account-center outage; projection lag; optimistic conflict; and unknown wallet result.

- [ ] **Step 2: Run all verification commands**

Run: `cd new-api && go test ./model ./controller -run 'Aicp|Workspace' -count=1`

Run: `cd aicp-backend && mvn -Dtest='WorkspaceAccessServiceTest,Enterprise*Test,ProjectExport*Test,AssetMarketLifecycleE2ETest,*Purchase*Test' test`

Run: `cd aicp-frontend && node --test tests/enterprise-*.test.js tests/navigation-contract.test.js && npm run build`

Expected: every command exits 0; no cross-Workspace or approval-bypass assertion is accepted.

- [ ] **Step 3: Commit acceptance coverage**

```bash
git add aicp-backend/src/test/java/com/aicp/module/enterprise/EnterpriseWorkbenchAcceptanceTest.java aicp-frontend/tests/enterprise-export-audit-state.test.js
git commit -m "test: verify enterprise workbench acceptance"
```
