# Enterprise Workbench M1 Budget and Approvals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add concurrency-safe procurement budgets and a unified purchase/asset approval inbox while preserving domain ownership.

**Architecture:** The enterprise module owns procurement budget policy and rebuildable approval projections only. Purchase and asset services keep their state machines; approval commands re-read source facts, authorize through `WorkspaceContext`, and execute in the source domain.

**Tech Stack:** Java 17, Spring Boot transactions, MyBatis-Plus, H2/MySQL migrations, JUnit 5, Vue 3, Element Plus, Node tests.

---

## File map

- Create `V7__enterprise_budget_and_approval_projection.sql` plus H2/MySQL schema equivalents.
- Create enterprise budget entities, mappers, DTOs, service, and controller.
- Modify trade purchase request/service to reserve, release, consume, and reverse budget.
- Create approval projection entity, projector, adapters, command router, and controller.
- Modify trade services to emit `TradeOutboxEvent`; add an asset outbox and emit asset approval events.
- Create budget and approval Vue pages and state tests.

### Task 1: Add procurement budget schema and contract

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V7__enterprise_budget_and_approval_projection.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Test: `aicp-backend/src/test/java/com/aicp/module/enterprise/schema/EnterpriseGovernanceSchemaTest.java`

- [ ] **Step 1: Add a failing schema test**

Assert the tables `enterprise_purchase_budgets`, `enterprise_purchase_budget_entries`, and `enterprise_approval_items` exist; monetary columns are `BIGINT`; budget scope has a unique key on Workspace, subject, and month; approval source has a unique key on type and source ID.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=EnterpriseGovernanceSchemaTest test`

Expected: FAIL because the tables are absent.

- [ ] **Step 3: Add exact schema**

Create budgets with `amount_cents`, `single_limit_cents`, `reserved_cents`, `consumed_cents`, and `row_version`; immutable entries with `entry_type`, `amount_cents`, `source_type`, `source_id`, and unique `idempotency_key`; approval items with source type/ID/version, Workspace, department, requester, status, amount, summary, timestamps, and row version.

- [ ] **Step 4: Run schema tests**

Run: `cd aicp-backend && mvn -Dtest=EnterpriseGovernanceSchemaTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/resources/db aicp-backend/src/test/java/com/aicp/module/enterprise/schema
git commit -m "feat: add enterprise governance schema"
```

### Task 2: Implement atomic procurement budget accounting

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/entity/EnterprisePurchaseBudget.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/entity/EnterprisePurchaseBudgetEntry.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/mapper/EnterprisePurchaseBudgetMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/mapper/EnterprisePurchaseBudgetEntryMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/service/PurchaseBudgetService.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/enterprise/service/PurchaseBudgetServiceTest.java`

- [ ] **Step 1: Write failing budget tests**

Test `reserve`, `release`, `consume`, and `reverse`; reject single-limit and monthly-limit breaches; replaying the same idempotency key returns the original result; two concurrent reservations cannot exceed available budget.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=PurchaseBudgetServiceTest test`

Expected: FAIL because the service is absent.

- [ ] **Step 3: Implement the service contract**

```java
public record BudgetSubject(String type, String id) {}
public record BudgetSnapshot(long amountCents, long reservedCents, long consumedCents, long availableCents, int rowVersion) {}

public interface PurchaseBudgetService {
    BudgetSnapshot reserve(WorkspaceContext ctx, BudgetSubject subject, YearMonth month, long amountCents, String sourceId, String idempotencyKey);
    BudgetSnapshot release(WorkspaceContext ctx, String sourceId, long amountCents, String idempotencyKey);
    BudgetSnapshot consume(WorkspaceContext ctx, String sourceId, long amountCents, String walletTransferNo, String idempotencyKey);
    BudgetSnapshot reverse(WorkspaceContext ctx, String sourceId, long amountCents, String walletReversalNo, String idempotencyKey);
}
```

Use a conditional SQL update with `reserved_cents + consumed_cents + amount <= amount_cents` and `row_version`; insert the immutable entry in the same transaction.

- [ ] **Step 4: Run budget tests**

Run: `cd aicp-backend && mvn -Dtest=PurchaseBudgetServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/enterprise/entity aicp-backend/src/main/java/com/aicp/module/enterprise/mapper aicp-backend/src/main/java/com/aicp/module/enterprise/service/PurchaseBudgetService.java aicp-backend/src/test/java/com/aicp/module/enterprise/service/PurchaseBudgetServiceTest.java
git commit -m "feat: account for procurement budgets"
```

### Task 3: Integrate budgets with enterprise purchase lifecycle

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/entity/PurchaseRequest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/PurchaseApprovalService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/OrderService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/RefundService.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/trade/service/EnterprisePurchaseBudgetIntegrationTest.java`

- [ ] **Step 1: Add failing lifecycle tests**

Assert submit reserves; reject/cancel/expire release; approve only changes the order to `PENDING_PAYMENT`; confirmed wallet success consumes; confirmed refund reverses; unknown wallet state performs no new budget entry.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=EnterprisePurchaseBudgetIntegrationTest test`

Expected: FAIL because purchase services do not call the budget service.

- [ ] **Step 3: Implement lifecycle integration**

Add `budgetSubjectType`, `budgetSubjectId`, and `budgetReservationEntryId` to `PurchaseRequest`. Derive Workspace and requester from `WorkspaceContext`, not `req.workspaceId()`. Use stable keys `purchase:{requestId}:reserve`, `:release`, `:consume:{transferNo}`, and `:reverse:{reversalNo}`.

- [ ] **Step 4: Run trade tests**

Run: `cd aicp-backend && mvn -Dtest='EnterprisePurchaseBudgetIntegrationTest,*Purchase*Test,FreeOrderDeliveryTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/trade aicp-backend/src/test/java/com/aicp/module/trade
git commit -m "feat: enforce enterprise purchase budgets"
```

### Task 4: Build the unified approval projection and command router

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/entity/EnterpriseApprovalItem.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/mapper/EnterpriseApprovalItemMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/service/ApprovalProjector.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/service/ApprovalCommandRouter.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/dto/ApprovalViews.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/entity/AssetOutboxEvent.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/mapper/AssetOutboxEventMapper.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetPublicationService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/PurchaseApprovalService.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/enterprise/service/ApprovalProjectionTest.java`

- [ ] **Step 1: Add failing projection tests**

Cover duplicate and out-of-order events, current status replacement only when source version increases, department-scope filtering, and command routing for `PURCHASE` and `ASSET_PUBLISH`.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=ApprovalProjectionTest test`

Expected: FAIL because projection classes are absent.

- [ ] **Step 3: Implement projection and router**

Define `ApprovalType { PURCHASE, ASSET_PUBLISH, PROJECT_EXPORT }` and `ApprovalDecisionCommand(boolean approved, String reason, int expectedVersion, String idempotencyKey)`. Add `asset_outbox_events` to the V7 migration with unique `event_id`, aggregate ID/type, event type, payload, status, attempts, and timestamps. Purchase transitions write `TradeOutboxEvent`; asset transitions write `AssetOutboxEvent` in the same source transaction. Projectors may update only `enterprise_approval_items`; router adapters call `PurchaseApprovalService` or `AssetPublicationService` after source re-read and scoped authorization.

- [ ] **Step 4: Run projection tests**

Run: `cd aicp-backend && mvn -Dtest='ApprovalProjectionTest,AssetMarketLifecycleE2ETest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/enterprise aicp-backend/src/test/java/com/aicp/module/enterprise/service/ApprovalProjectionTest.java
git commit -m "feat: project unified enterprise approvals"
```

### Task 5: Expose budget and approval APIs

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/controller/EnterpriseBudgetController.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/enterprise/controller/EnterpriseApprovalController.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/enterprise/EnterpriseGovernanceApiTest.java`

- [ ] **Step 1: Add failing API tests**

Test server-side pagination, `mine/submitted/processed` buckets, department filtering, source detail hydration, required reject reason, `Idempotency-Key`, expected-version conflict, and no-permission response.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=EnterpriseGovernanceApiTest test`

Expected: FAIL because endpoints are absent.

- [ ] **Step 3: Implement controllers**

Expose `/api/v1/enterprise/budgets`, `/budget-entries`, `/approvals`, `/approvals/{type}/{id}`, and `/approvals/{type}/{id}/decisions`. Return `allowed_actions` from backend facts. A projection row never authorizes a command by itself.

- [ ] **Step 4: Run enterprise API tests**

Run: `cd aicp-backend && mvn -Dtest='EnterpriseGovernanceApiTest,Enterprise*Test' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/enterprise/controller aicp-backend/src/test/java/com/aicp/module/enterprise/EnterpriseGovernanceApiTest.java
git commit -m "feat: expose enterprise budgets and approvals"
```

### Task 6: Build budget and approval pages

**Files:**
- Create: `aicp-frontend/src/views/enterprise/EnterpriseBudgets.vue`
- Create: `aicp-frontend/src/views/enterprise/EnterpriseApprovals.vue`
- Create: `aicp-frontend/src/views/enterprise/components/ApprovalDetailDrawer.vue`
- Create: `aicp-frontend/src/views/enterprise/enterpriseState.js`
- Modify: `aicp-frontend/src/api/enterprise.js`
- Modify: `aicp-frontend/src/router/index.js`
- Test: `aicp-frontend/tests/enterprise-governance-state.test.js`

- [ ] **Step 1: Add failing state tests**

Test independent loading/error states, server pagination query serialization, required rejection reason, conflict-triggered refresh, budget/wallet error distinction, and Workspace-change state reset.

- [ ] **Step 2: Verify failure**

Run: `cd aicp-frontend && node --test tests/enterprise-governance-state.test.js`

Expected: FAIL because state helpers are absent.

- [ ] **Step 3: Implement pages and state**

Render approval tabs `待我处理/我发起的/已处理`, filters, source-specific evidence, and backend-provided actions. Budgets render procurement policy separately from 3001 wallet/AI usage. Do not add batch approval controls.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/enterprise-governance-state.test.js tests/navigation-contract.test.js && npm run build`

Expected: PASS and build exits 0.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/enterprise aicp-frontend/src/api/enterprise.js aicp-frontend/src/router/index.js aicp-frontend/tests
git commit -m "feat: add enterprise budget and approval UI"
```

### Task 7: Verify purchase and asset approval journeys

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/enterprise/EnterpriseApprovalE2ETest.java`

- [ ] **Step 1: Add E2E scenarios**

Cover purchase submit → reserve → approve → explicit pay → consume; rejection → release; asset publish request → approve → listing; cross-department approver denied; duplicate decision idempotent.

- [ ] **Step 2: Run focused regression**

Run: `cd aicp-backend && mvn -Dtest='EnterpriseApprovalE2ETest,Enterprise*Test,AssetMarketLifecycleE2ETest,*Purchase*Test' test`

Run: `cd aicp-frontend && node --test tests/enterprise-governance-state.test.js && npm run build`

Expected: every command exits 0.

- [ ] **Step 3: Commit**

```bash
git add aicp-backend/src/test/java/com/aicp/module/enterprise/EnterpriseApprovalE2ETest.java
git commit -m "test: verify enterprise governance journeys"
```
