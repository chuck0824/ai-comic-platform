# Unified Task Event Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an independent task center that unifies generation and script-trade timelines, user actions, financial visibility, SLA alerts, reconciliation, and audited operator recovery.

**Architecture:** Keep `generation_tasks`, trade tables, and the 3001 account/ledger service as facts. Append normalized events in the Java service, project them into rebuildable task-center read models, and route commands back to the owning domain. Deliver user and operator UIs from the same projection with separate authorization and action sets.

**Tech Stack:** Java 17, Spring Boot 3.2, MyBatis-Plus, H2/MySQL, Go/Gin/GORM for 3001 integration, Vue 3, Vue Router, Element Plus, Axios, Node test runner.

---

## Delivery decomposition

This is a program plan for three independently deployable releases:

- **R1 — Tasks 1–10:** event foundation, generation/trade projection, user commands, user task center, initial SLA exceptions.
- **R2 — Tasks 11–13:** operator console, reconciliation, approvals, work orders, financial recovery.
- **R3 — Tasks 14–15:** migration/cutover, scale, performance, observability, and documentation.

Do not begin R2 until the R1 end-to-end tests pass. Do not remove legacy monitoring reads until Task 14 dual-read comparison passes.

## File map

### Backend task-center module

- `aicp-backend/src/main/java/com/aicp/module/taskcenter/domain/TaskCenterEnums.java` — stable summary, health, alert, command, and link enums.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/entity/*.java` — task case, links, events, attempts, commands, alerts, reconciliation, and work-order rows.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/mapper/*.java` — MyBatis mappers only.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/dto/TaskCenterRequests.java` — validated filters and command requests.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/dto/TaskCenterViews.java` — stable snake_case response records.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskEventService.java` — idempotent append and event lookup.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskProjectionService.java` — deterministic event-to-case projection.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskCenterQueryService.java` — workspace-scoped list/detail/facets.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskCommandService.java` — permission, idempotency, and domain routing.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskSlaService.java` — deadline evaluation and alert lifecycle.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskReconciliationService.java` — order/task versus wallet reconciliation.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/controller/TaskCenterController.java` — user APIs.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/controller/OpsTaskCenterController.java` — operator APIs.
- `aicp-backend/src/main/java/com/aicp/module/taskcenter/controller/TaskCenterStreamController.java` — SSE cursor stream.

### Existing backend integrations

- `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationService.java` — trusted creation, retry, and cancel semantics.
- `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationExecutor.java` — terminal-state ordering and event emission.
- `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationSettlementService.java` — atomic asset completion and compensation events.
- `aicp-backend/src/main/java/com/aicp/common/ai/client/NewApiClient.java` — request ID capture and fail-closed behavior.
- `aicp-backend/src/main/java/com/aicp/module/trade/service/OrderService.java` — order/payment/delivery events.
- `aicp-backend/src/main/java/com/aicp/module/trade/service/RefundService.java` — refund/reversal events.
- `aicp-backend/src/main/java/com/aicp/module/trade/service/TradeRecoveryService.java` — reconciliation and exhausted-recovery events.
- `aicp-backend/src/main/java/com/aicp/module/trade/wallet/WalletClient.java` — transfer status used by reconciliation.

### Database

- `aicp-backend/src/main/resources/db/migration/V7__unified_task_event_center.sql` — production migration.
- `aicp-backend/src/main/resources/db/schema-h2.sql` — test schema mirror.
- `aicp-backend/src/main/resources/db/schema-mysql.sql` — MySQL bootstrap mirror.
- `aicp-backend/src/main/resources/db/schema.sql` — default bootstrap mirror.

### Frontend

- `aicp-frontend/src/api/taskCenter.js` — all task-center requests and SSE URL construction.
- `aicp-frontend/src/views/task-center/taskCenterState.js` — pure status/action/time/amount projections.
- `aicp-frontend/src/views/task-center/useTaskCenter.js` — URL filters, request cancellation, polling fallback, and selection.
- `aicp-frontend/src/views/task-center/TaskCenter.vue` — user page shell.
- `aicp-frontend/src/views/task-center/OpsTaskCenter.vue` — operator page shell.
- `aicp-frontend/src/views/task-center/components/*.vue` — summary, filter, list, detail timeline, finance, alerts, command dialogs.
- `aicp-frontend/src/router/index.js` and `aicp-frontend/src/components/Sidebar.vue` — independent routes and navigation.

## R1 — Unified foundation and user closed loop

### Task 1: Add task-center schema and schema contract test

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V7__unified_task_event_center.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/resources/db/schema.sql`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/schema/TaskCenterSchemaTest.java`

- [ ] **Step 1: Write the failing schema test**

```java
@Test
void schemaDefinesTaskCenterTablesAndIdempotencyKeys() throws Exception {
    String schema = Files.readString(Path.of("src/main/resources/db/schema-h2.sql"));
    assertThat(schema).contains("CREATE TABLE IF NOT EXISTS task_cases");
    assertThat(schema).contains("CREATE TABLE IF NOT EXISTS task_events");
    assertThat(schema).contains("UNIQUE (source_system, event_id)");
    assertThat(schema).contains("CREATE TABLE IF NOT EXISTS task_commands");
    assertThat(schema).contains("CREATE TABLE IF NOT EXISTS reconciliation_cases");
    assertThat(schema).contains("CREATE TABLE IF NOT EXISTS task_work_orders");
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `cd aicp-backend && mvn -Dtest=TaskCenterSchemaTest test`

Expected: FAIL because the task-center tables are absent.

- [ ] **Step 3: Add the eight tables and indexes to V7 and all bootstrap schemas**

Use this column contract in every schema variant; adapt only auto-increment and JSON syntax:

```sql
CREATE TABLE IF NOT EXISTS task_cases (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_uuid VARCHAR(36) NOT NULL UNIQUE,
  workspace_id VARCHAR(64) NOT NULL,
  domain VARCHAR(16) NOT NULL,
  task_type VARCHAR(64) NOT NULL,
  summary_status VARCHAR(24) NOT NULL,
  domain_stage VARCHAR(64) NOT NULL,
  health_status VARCHAR(24) NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  current_owner BIGINT,
  next_action VARCHAR(64),
  estimated_amount BIGINT NOT NULL DEFAULT 0,
  actual_amount BIGINT NOT NULL DEFAULT 0,
  refunded_amount BIGINT NOT NULL DEFAULT 0,
  unit VARCHAR(16) NOT NULL,
  aggregate_version BIGINT NOT NULL DEFAULT 0,
  sla_due_at DATETIME,
  last_event_at DATETIME NOT NULL,
  completed_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tc_workspace_status ON task_cases(workspace_id, summary_status, last_event_at);
CREATE INDEX idx_tc_health_sla ON task_cases(health_status, sla_due_at);

CREATE TABLE IF NOT EXISTS task_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  event_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(96) NOT NULL,
  aggregate_type VARCHAR(32) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  occurred_at DATETIME NOT NULL,
  received_at DATETIME NOT NULL,
  actor_type VARCHAR(16),
  actor_id VARCHAR(64),
  trace_id VARCHAR(64),
  payload JSON,
  UNIQUE (source_system, event_id)
);
```

Add the remaining tables with these exact minimum columns and constraints:

```sql
CREATE TABLE task_case_links (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, case_id BIGINT NOT NULL,
  link_type VARCHAR(32) NOT NULL, source_system VARCHAR(32) NOT NULL,
  external_id VARCHAR(64) NOT NULL, display_ref VARCHAR(128), created_at DATETIME NOT NULL,
  UNIQUE (case_id, link_type, source_system, external_id)
);
CREATE TABLE task_attempts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, case_id BIGINT NOT NULL, attempt_no INT NOT NULL,
  source_task_id VARCHAR(64), provider_request_id VARCHAR(128), status VARCHAR(24) NOT NULL,
  started_at DATETIME, completed_at DATETIME, error_code VARCHAR(64), error_summary VARCHAR(500),
  estimated_amount BIGINT NOT NULL DEFAULT 0, actual_amount BIGINT NOT NULL DEFAULT 0,
  unit VARCHAR(16) NOT NULL, retry_of_attempt_id BIGINT, UNIQUE (case_id, attempt_no)
);
CREATE TABLE task_commands (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, command_uuid VARCHAR(36) NOT NULL UNIQUE,
  case_id BIGINT NOT NULL, workspace_id VARCHAR(64) NOT NULL, actor_user_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL, idempotency_key VARCHAR(128) NOT NULL, request_hash VARCHAR(64) NOT NULL,
  reason VARCHAR(500), approval_status VARCHAR(24), status VARCHAR(24) NOT NULL,
  target_service VARCHAR(32) NOT NULL, response_summary JSON, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
  UNIQUE (workspace_id, actor_user_id, idempotency_key)
);
CREATE TABLE task_alerts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, case_id BIGINT NOT NULL, rule_code VARCHAR(64) NOT NULL,
  severity VARCHAR(4) NOT NULL, status VARCHAR(24) NOT NULL, aggregation_key VARCHAR(128) NOT NULL,
  owner_user_id BIGINT, first_triggered_at DATETIME NOT NULL, last_triggered_at DATETIME NOT NULL,
  suppressed_until DATETIME, resolved_event_id VARCHAR(64), resolution_reason VARCHAR(500),
  UNIQUE (case_id, rule_code, aggregation_key)
);
CREATE TABLE reconciliation_cases (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, reconciliation_uuid VARCHAR(36) NOT NULL UNIQUE,
  case_id BIGINT NOT NULL, discrepancy_type VARCHAR(64) NOT NULL, fingerprint VARCHAR(128) NOT NULL UNIQUE,
  left_ref VARCHAR(128) NOT NULL, right_ref VARCHAR(128), difference_amount BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL, owner_domain VARCHAR(32) NOT NULL, auto_attempts INT NOT NULL DEFAULT 0,
  conclusion VARCHAR(500), work_order_id BIGINT, resolved_at DATETIME, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL
);
CREATE TABLE task_work_orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, work_order_uuid VARCHAR(36) NOT NULL UNIQUE,
  case_id BIGINT NOT NULL, source_alert_id BIGINT, title VARCHAR(200) NOT NULL,
  severity VARCHAR(4) NOT NULL, status VARCHAR(24) NOT NULL, assignee_user_id BIGINT,
  resolution VARCHAR(1000), created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, closed_at DATETIME
);
```

- [ ] **Step 4: Run schema and existing trade schema tests**

Run: `cd aicp-backend && mvn -Dtest=TaskCenterSchemaTest,TradeMarketSchemaTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/resources/db aicp-backend/src/test/java/com/aicp/module/taskcenter/schema
git commit -m "feat: add task center schema"
```

### Task 2: Define domain types, entities, and mappers

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/domain/TaskCenterEnums.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/entity/TaskCase.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/entity/TaskCaseLink.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/entity/TaskEvent.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/entity/TaskAttempt.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/entity/TaskCommand.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/entity/TaskAlert.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/entity/ReconciliationCase.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/entity/TaskWorkOrder.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/mapper/*.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/domain/TaskCenterEnumsTest.java`

- [ ] **Step 1: Write enum contract tests**

```java
@Test
void summaryAndHealthStatesStaySeparate() {
    assertThat(SummaryStatus.values()).extracting(Enum::name)
        .containsExactly("WAITING_ACTION", "IN_PROGRESS", "SUCCEEDED", "FAILED", "CANCELED", "EXCEPTION");
    assertThat(HealthStatus.values()).extracting(Enum::name)
        .containsExactly("NORMAL", "AT_RISK", "SLA_BREACHED", "INCONSISTENT");
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `cd aicp-backend && mvn -Dtest=TaskCenterEnumsTest test`

Expected: compilation failure because the enums do not exist.

- [ ] **Step 3: Implement the stable enums**

```java
public final class TaskCenterEnums {
    private TaskCenterEnums() {}
    public enum Domain { GENERATION, TRADE }
    public enum SummaryStatus { WAITING_ACTION, IN_PROGRESS, SUCCEEDED, FAILED, CANCELED, EXCEPTION }
    public enum HealthStatus { NORMAL, AT_RISK, SLA_BREACHED, INCONSISTENT }
    public enum AlertStatus { OPEN, ACKNOWLEDGED, RESOLVED, SUPPRESSED }
    public enum CommandStatus { ACCEPTED, RUNNING, SUCCEEDED, FAILED, PENDING_APPROVAL }
    public enum LinkType { GENERATION_TASK, ORDER, REFUND, WALLET_TRANSFER, ENTITLEMENT, ASSET, CONTENT_PROJECT, CANVAS_PROJECT, NODE, WORK_ORDER }
}
```

- [ ] **Step 4: Add entities matching Task 1 columns and zero-method mappers**

Each mapper uses the same pattern:

```java
@Mapper
public interface TaskCaseMapper extends BaseMapper<TaskCase> {}
```

Use `@TableName`, `@TableId(type = IdType.AUTO)`, and `LocalDateTime`. Do not put projection logic in entities or mappers.

- [ ] **Step 5: Run tests**

Run: `cd aicp-backend && mvn -Dtest=TaskCenterEnumsTest,TaskCenterSchemaTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/taskcenter aicp-backend/src/test/java/com/aicp/module/taskcenter/domain
git commit -m "feat: define task center domain model"
```

### Task 3: Implement idempotent event append and deterministic projection

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskEventService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskProjectionService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskEventEnvelope.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/service/TaskProjectionServiceTest.java`

- [ ] **Step 1: Write tests for duplicate and out-of-order events**

```java
@Test
void duplicateEventIsIgnoredAndOlderVersionCannotRegressCase() {
    service.append(generation("evt-1", 2, "generation.running"));
    service.append(generation("evt-1", 2, "generation.running"));
    service.append(generation("evt-0", 1, "generation.queued"));
    TaskCase taskCase = cases.selectByUuid(CASE_UUID);
    assertThat(events.selectCount(null)).isEqualTo(2);
    assertThat(taskCase.getDomainStage()).isEqualTo("RUNNING");
    assertThat(taskCase.getAggregateVersion()).isEqualTo(2);
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=TaskProjectionServiceTest test`

Expected: FAIL because append/projection services do not exist.

- [ ] **Step 3: Define the immutable envelope**

```java
public record TaskEventEnvelope(
    String eventId, String eventType, String sourceSystem,
    String aggregateType, String aggregateId, long aggregateVersion,
    String workspaceId, Long createdBy, LocalDateTime occurredAt,
    String traceId, Map<String, Object> payload) {}
```

- [ ] **Step 4: Implement transactional append**

```java
@Transactional
public TaskCase append(TaskEventEnvelope e) {
    TaskEvent duplicate = eventMapper.selectBySourceAndEventId(e.sourceSystem(), e.eventId());
    if (duplicate != null) return caseMapper.selectById(duplicate.getCaseId());
    TaskCase taskCase = projector.findOrCreateCase(e);
    eventMapper.insert(toEntity(taskCase.getId(), e));
    projector.apply(taskCase, e);
    caseMapper.updateById(taskCase);
    return taskCase;
}
```

`apply` must ignore state mutations when `aggregateVersion <= taskCase.aggregateVersion`, while still retaining the event for audit.

- [ ] **Step 5: Add explicit generation and trade projection maps**

```java
private Projection generation(String type) {
    return switch (type) {
        case "generation.created" -> p(IN_PROGRESS, "CREATED", NORMAL, 0);
        case "generation.queued" -> p(IN_PROGRESS, "QUEUED", NORMAL, 5);
        case "generation.running" -> p(IN_PROGRESS, "RUNNING", NORMAL, 30);
        case "generation.asset_registered" -> p(IN_PROGRESS, "SETTLING", NORMAL, 90);
        case "generation.succeeded" -> p(SUCCEEDED, "SUCCEEDED", NORMAL, 100);
        case "generation.failed" -> p(FAILED, "FAILED", NORMAL, 100);
        case "generation.settlement_exhausted" -> p(EXCEPTION, "SETTLEMENT", INCONSISTENT, 95);
        default -> throw new IllegalArgumentException("Unsupported event type: " + type);
    };
}
```

Add equivalent explicit mappings for order created, approval pending, paying, paid, delivered, refund requested, reversed, and recovery exhausted. Unknown event types must be rejected and dead-lettered by the caller, not silently projected.

- [ ] **Step 6: Run tests**

Run: `cd aicp-backend && mvn -Dtest=TaskProjectionServiceTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/taskcenter/service aicp-backend/src/test/java/com/aicp/module/taskcenter/service
git commit -m "feat: project normalized task events"
```

### Task 4: Correct generation lifecycle and emit task events

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationExecutor.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationSettlementService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationSettlementCompensator.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/generation/controller/GenerationController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/generation/service/GenerationLifecycleTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/generation/service/GenerationSettlementServiceTest.java`

- [ ] **Step 1: Write failing lifecycle tests**

```java
@Test
void retryPreservesWorkspaceAndLinksOriginalAttempt() {
    GenerationTask retry = service.retryTask(ctx, original.getUuid(), "retry-key");
    assertThat(retry.getWorkspaceId()).isEqualTo(ctx.workspaceId());
    assertThat(retry.getCreatedBy()).isEqualTo(ctx.userId());
    assertThat(retry.getRetryOfTaskId()).isEqualTo(original.getId());
}

@Test
void taskCannotSucceedBeforeAssetSettlement() {
    executor.execute(taskWithoutStorageKey());
    assertThat(taskMapper.selectById(task.getId()).getStatus()).isEqualTo("failed");
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=GenerationLifecycleTest test`

Expected: FAIL on missing trusted context/retry link and premature success.

- [ ] **Step 3: Require trusted creation context and idempotency**

Change the service signature to:

```java
public GenerationTask createTask(WorkspaceContext ctx, CreateGenerationTask request, String idempotencyKey)
```

Set `workspaceId`, `createdBy`, `contentProjectId`, `assetType`, `requestId`, and `idempotencyKey` from trusted context/request. Reject duplicate key with a different request hash. Remove database defaults that silently assign `personal_1`.

- [ ] **Step 4: Make success the last settlement step**

```java
GenerationTask routed = aiRouter.executeTask(task.getId()); // leaves status output_ready
SettlementResult result = settlementService.settle(routed, outputParser.parse(routed));
if (result == null) throw new GenerationException("ASSET_SETTLEMENT_FAILED");
routed.setStatus("succeeded");
routed.setProgress(100);
routed.setCompletedAt(clock.now());
taskMapper.updateById(routed);
events.append(generationEvent(routed, "generation.succeeded"));
```

Do not catch and downgrade asset-registration failures to warnings.

- [ ] **Step 5: Emit normalized events at every durable stage**

Emit `generation.created`, `queued`, `running`, `output_ready`, `asset_registered`, `succeeded`, `failed`, `cancel_requested`, `canceled`, and `settlement_exhausted`. Increment a persisted task version for every transition.

- [ ] **Step 6: Run generation and asset settlement tests**

Run: `cd aicp-backend && mvn -Dtest=GenerationLifecycleTest,GenerationSettlementServiceTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/generation aicp-backend/src/test/java/com/aicp/module/generation
git commit -m "fix: make generation lifecycle observable and atomic"
```

### Task 5: Capture authoritative 3001 request and billing facts

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/common/ai/client/NewApiClient.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/ai/AiRouter.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/generation/entity/GenerationTask.java`
- Create: `aicp-backend/src/main/java/com/aicp/common/account/AccountUsageClient.java`
- Modify: `aicp-backend/src/main/resources/db/migration/V7__unified_task_event_center.sql`
- Create: `aicp-backend/src/test/java/com/aicp/common/ai/client/NewApiClientTest.java`
- Modify: `new-api/model/log.go`
- Create: `new-api/controller/aicp_usage.go`
- Modify: `new-api/router/api-router.go`
- Create: `new-api/controller/aicp_usage_test.go`

- [ ] **Step 1: Write failing fail-closed and request-ID tests**

```java
@Test
void unavailableGatewayThrowsInsteadOfReturningMockSuccess() {
    mockServer.expect(requestTo(baseUrl + "/v1/images/generations"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    assertThatThrownBy(() -> client.imageGeneration(Map.of("model", "x")))
        .isInstanceOf(NewApiUnavailableException.class);
}

@Test
void responseCarriesGatewayRequestId() {
    mockServer.expect(requestTo(baseUrl + "/v1/images/generations"))
        .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON)
            .header("X-Request-Id", "req-7"));
    assertThat(client.imageGeneration(Map.of()).requestId()).isEqualTo("req-7");
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=NewApiClientTest test`

Expected: FAIL because the client returns mock maps and drops response headers.

- [ ] **Step 3: Return a typed gateway response**

```java
public record NewApiResponse(String requestId, Map<String, Object> body) {}
```

Inject `RestTemplate` through the constructor so `MockRestServiceServer` can test it. `post` must throw on network/HTTP failure in production. A mock transport may only be injected under an explicit `dev-mock` profile and must mark results `mock=true`; mock output must never be settled as a production asset.

- [ ] **Step 4: Add authenticated 3001 estimate and usage-fact endpoints**

Add `POST /api/aicp/usage-estimates` and `GET /api/aicp/usage-requests/:requestId` under the existing HMAC-protected internal group. The estimate response is:

```go
type UsageEstimate struct {
    EstimatedQuota int    `json:"estimated_quota"`
    Unit           string `json:"unit"`
    Model          string `json:"model"`
}
type UsageFact struct {
    RequestID      string `json:"request_id"`
    Status         string `json:"status"`
    ActualQuota    int    `json:"actual_quota"`
    RefundedQuota  int    `json:"refunded_quota"`
    Model          string `json:"model"`
    CompletedAt    int64  `json:"completed_at"`
}
```

The fact query reads consume/refund logs by exact `request_id`; it returns 404 when no authoritative fact exists and never exposes prompts, API keys, IPs, or provider payloads.

- [ ] **Step 5: Persist request ID and billing projection**

Add `gateway_request_id`, `estimated_cost`, `actual_cost`, `refunded_cost`, and `cost_unit` to `generation_tasks`. `AccountUsageClient` calls the HMAC-protected estimate/fact APIs; `AiRouter` persists the response request ID and the task projector stores only returned billing facts. Do not write an authoritative local credit balance or local billing ledger.

- [ ] **Step 6: Run tests**

Run: `cd aicp-backend && mvn -Dtest=NewApiClientTest,GenerationLifecycleTest test`

Run: `cd new-api && go test ./controller ./model`

Expected: both commands PASS.

- [ ] **Step 7: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/common/ai aicp-backend/src/main/java/com/aicp/common/account aicp-backend/src/main/java/com/aicp/module/generation aicp-backend/src/main/resources/db/migration/V7__unified_task_event_center.sql aicp-backend/src/test/java/com/aicp/common/ai new-api/model/log.go new-api/controller/aicp_usage.go new-api/controller/aicp_usage_test.go new-api/router/api-router.go
git commit -m "fix: track authoritative generation billing requests"
```

### Task 6: Emit trade, delivery, refund, and recovery events

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/OrderService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/service/TradePaymentService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/PurchaseApprovalService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/DeliveryService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/RefundService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/TradeRecoveryService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/service/TradeTaskProjectionIntegrationTest.java`

- [ ] **Step 1: Write the failing paid-but-not-delivered test**

```java
@Test
void paidOrderWithDeliveryFailureIsInconsistentNotPaymentFailed() {
    wallet.succeed("WT-1");
    delivery.failOnce();
    assertThatThrownBy(() -> orders.pay(ctx, orderNo)).isInstanceOf(BizException.class);
    TaskCase taskCase = taskCases.byOrderNo(orderNo);
    assertThat(taskCase.getSummaryStatus()).isEqualTo("EXCEPTION");
    assertThat(taskCase.getHealthStatus()).isEqualTo("INCONSISTENT");
    assertThat(taskCase.getDomainStage()).isEqualTo("PAID_PENDING_DELIVERY");
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=TradeTaskProjectionIntegrationTest test`

Expected: FAIL because payment and delivery failures are collapsed.

- [ ] **Step 3: Split payment and delivery transaction boundaries**

`OrderService.pay` delegates to `TradePaymentService`. Use three boundaries: mark `PAYING`, call the wallet without an open database transaction, then persist `PAID_PENDING_DELIVERY` plus `trade.payment.succeeded` in `REQUIRES_NEW` before attempting delivery in a separate transaction. Delivery failure emits `trade.delivery.failed`; it must never rewrite the order to `PAYMENT_FAILED`.

- [ ] **Step 4: Emit all trade events**

Emit order created/canceled/expired, approval requested/approved/rejected, payment started/succeeded/failed/unknown, delivery started/succeeded/failed, settlement released, refund requested/approved/rejected/reversed, and recovery exhausted. Link wallet transfer, entitlement, copy, and refund IDs via `task_case_links`.

- [ ] **Step 5: Run trade tests**

Run: `cd aicp-backend && mvn -Dtest=TradeTaskProjectionIntegrationTest,FreeOrderDeliveryTest,ListingServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/trade aicp-backend/src/test/java/com/aicp/module/taskcenter/service
git commit -m "feat: publish complete trade task timelines"
```

### Task 7: Add workspace-scoped query, detail, facets, and SSE APIs

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/dto/TaskCenterRequests.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/dto/TaskCenterViews.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskCenterQueryService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/controller/TaskCenterController.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/controller/TaskCenterStreamController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/TaskCenterApiIntegrationTest.java`

- [ ] **Step 1: Write API isolation tests**

```java
mockMvc.perform(get("/api/v1/task-center/cases").header("X-Workspace-Id", "personal_2"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.items[?(@.workspace_id == 'personal_1')]").doesNotExist());
```

- [ ] **Step 2: Run and verify 404/failure**

Run: `cd aicp-backend && mvn -Dtest=TaskCenterApiIntegrationTest test`

Expected: FAIL because the endpoints do not exist.

- [ ] **Step 3: Implement validated query records**

```java
public record CaseQuery(
    String domain, String status, String health, String taskType,
    String keyword, LocalDateTime from, LocalDateTime to,
    @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {}
```

- [ ] **Step 4: Implement list/detail/facets**

Every query starts with `workspace_id = ctx.workspaceId()`. Annotate `TaskCenterViews` records with `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)`. Return a module-local `PageView<CaseSummary>` whose fields are `items`, `page`, `page_size`, `total`, `total_pages`, and `has_more`; do not reuse the camelCase global `PageResult`. Detail responses include event cursor pages, finance summaries, links, attempts, alerts, and server-calculated `allowed_actions`.

- [ ] **Step 5: Implement cursor SSE**

Use `SseEmitter` with a 30-second heartbeat and `Last-Event-ID`/`cursor`. Only stream events whose Case belongs to the trusted Workspace. On timeout, complete the emitter; the client reconnects with the last cursor.

- [ ] **Step 6: Run API tests**

Run: `cd aicp-backend && mvn -Dtest=TaskCenterApiIntegrationTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/taskcenter aicp-backend/src/test/java/com/aicp/module/taskcenter/TaskCenterApiIntegrationTest.java
git commit -m "feat: expose workspace task center APIs"
```

### Task 8: Add idempotent user command routing

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskCommandService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/taskcenter/controller/TaskCenterController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/service/TaskCommandServiceTest.java`

- [ ] **Step 1: Write command idempotency and authorization tests**

```java
@Test
void sameKeyReturnsOriginalResultAndDifferentHashIsRejected() {
    CommandResult first = commands.execute(ctx, caseId, "retry", "key-1", request("reason-a"));
    assertThat(commands.execute(ctx, caseId, "retry", "key-1", request("reason-a"))).isEqualTo(first);
    assertThatThrownBy(() -> commands.execute(ctx, caseId, "retry", "key-1", request("reason-b")))
        .isInstanceOf(IdempotencyConflictException.class);
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=TaskCommandServiceTest test`

Expected: FAIL because command routing does not exist.

- [ ] **Step 3: Implement action registry and state guards**

```java
private static final Map<String, String> REQUIRED_PERMISSION = Map.of(
    "cancel", "generation.cancel",
    "retry", "generation.retry",
    "continue_payment", "trade.purchase",
    "cancel_order", "trade.purchase",
    "submit_refund", "trade.refund.request",
    "add_evidence", "trade.refund.request");
```

Validate trusted Workspace, current domain stage, `allowed_actions`, permission, reason requirements, and request hash before calling the owning service.

- [ ] **Step 4: Return accepted command state**

The endpoint is `POST /api/v1/task-center/cases/{caseUuid}/commands/{action}`. Return `command_uuid`, status, accepted time, and Case link. Completion arrives through events; do not claim success synchronously for long-running actions.

- [ ] **Step 5: Run tests**

Run: `cd aicp-backend && mvn -Dtest=TaskCommandServiceTest,TaskCenterApiIntegrationTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/taskcenter aicp-backend/src/test/java/com/aicp/module/taskcenter/service/TaskCommandServiceTest.java
git commit -m "feat: route idempotent task commands"
```

### Task 9: Build frontend state, API client, and composable

**Files:**
- Create: `aicp-frontend/src/api/taskCenter.js`
- Create: `aicp-frontend/src/views/task-center/taskCenterState.js`
- Create: `aicp-frontend/src/views/task-center/useTaskCenter.js`
- Create: `aicp-frontend/tests/task-center-state.test.js`

- [ ] **Step 1: Write pure projection tests**

```js
test('inconsistent paid delivery is not shown as payment failed', () => {
  const card = toTaskCard({ summary_status: 'EXCEPTION', health_status: 'INCONSISTENT', domain_stage: 'PAID_PENDING_DELIVERY' })
  assert.equal(card.title, '支付成功，交付处理中')
  assert.equal(card.tone, 'danger')
})
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/task-center-state.test.js`

Expected: FAIL because the state module does not exist.

- [ ] **Step 3: Implement stable labels and action projections**

Export `summaryStatusLabel`, `healthLabel`, `domainStageLabel`, `formatTaskAmount`, `toTaskCard`, and `isTerminal`. Unknown values must render the raw code rather than disappear.

- [ ] **Step 4: Implement the API client**

```js
export const taskCenterApi = {
  overview: () => request.get('/task-center/overview'),
  list: (params, signal) => request.get('/task-center/cases', { params, signal }),
  detail: (uuid) => request.get(`/task-center/cases/${uuid}`),
  events: (uuid, cursor) => request.get(`/task-center/cases/${uuid}/events`, { params: { cursor } }),
  command: (uuid, action, data, key) => request.post(`/task-center/cases/${uuid}/commands/${action}`, data, { headers: { 'Idempotency-Key': key } })
}
```

- [ ] **Step 5: Implement URL-driven list state**

`useTaskCenter` parses domain/status/health/type/keyword/from/to/page/page_size from the route, cancels stale requests with `AbortController`, and resets on Workspace change. Consume SSE with authenticated `fetch()` plus `ReadableStream` so the request can include `Authorization` and `X-Workspace-Id`; do not put bearer tokens in URLs. Reconnect with the last cursor and fall back to 15-second incremental polling.

- [ ] **Step 6: Run tests**

Run: `cd aicp-frontend && node --test tests/task-center-state.test.js`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add aicp-frontend/src/api/taskCenter.js aicp-frontend/src/views/task-center aicp-frontend/tests/task-center-state.test.js
git commit -m "feat: add task center frontend state"
```

### Task 10: Build user task center and independent navigation

**Files:**
- Create: `aicp-frontend/src/views/task-center/TaskCenter.vue`
- Create: `aicp-frontend/src/views/task-center/components/TaskSummaryCards.vue`
- Create: `aicp-frontend/src/views/task-center/components/TaskFilterBar.vue`
- Create: `aicp-frontend/src/views/task-center/components/TaskCaseList.vue`
- Create: `aicp-frontend/src/views/task-center/components/TaskCaseDrawer.vue`
- Create: `aicp-frontend/src/views/task-center/components/TaskTimeline.vue`
- Create: `aicp-frontend/src/views/task-center/components/TaskFinancePanel.vue`
- Create: `aicp-frontend/src/views/task-center/components/TaskCommandDialog.vue`
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/src/components/Sidebar.vue`
- Modify: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Add failing navigation assertions**

```js
test('task center is independent from asset history', () => {
  assert.match(router, /path:\s*['"]task-center['"]/)
  assert.match(sidebar, /to="\/task-center"/)
  assert.doesNotMatch(router, /task-monitor[\s\S]*asset-history\?status/)
})
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js tests/task-center-state.test.js`

Expected: FAIL because `/task-center` is absent and `/task-monitor` redirects to assets.

- [ ] **Step 3: Add the page shell and four tabs**

Implement tabs for overview, generation, trade, and costs. Preserve filters in URL. Render skeleton, empty, no-match, forbidden, and load-error states separately.

- [ ] **Step 4: Add details and commands**

The drawer renders summary, timeline, finance, attempts, links, alerts, and audit sections. Render command buttons only from `allowed_actions`; generate a UUID idempotency key per submitted command and disable the button until its command status is known.

- [ ] **Step 5: Replace legacy route behavior**

Add `/task-center`; redirect `/task-monitor` to `/task-center`. Add the sidebar item “任务中心” under intelligent production. Keep `/asset-history` as the asset-only workbench.

- [ ] **Step 6: Run frontend tests and build**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js tests/task-center-state.test.js && npm run build`

Expected: all tests PASS and Vite build exits 0.

- [ ] **Step 7: Commit**

```bash
git add aicp-frontend/src/views/task-center aicp-frontend/src/router/index.js aicp-frontend/src/components/Sidebar.vue aicp-frontend/tests
git commit -m "feat: add independent user task center"
```

## R2 — Operations, SLA, reconciliation, and recovery

### Task 11: Add SLA evaluation and alert lifecycle

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskSlaService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/service/TaskSlaServiceTest.java`

- [ ] **Step 1: Write clock-controlled SLA tests**

```java
@Test
void missingAssetAfterTwoMinutesRaisesP2AndMarksCaseBreached() {
    clock.advance(Duration.ofMinutes(3));
    sla.evaluate();
    assertThat(alerts.openFor(caseId).getSeverity()).isEqualTo("P2");
    assertThat(cases.selectById(caseId).getHealthStatus()).isEqualTo("SLA_BREACHED");
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=TaskSlaServiceTest test`

Expected: FAIL because no evaluator exists.

- [ ] **Step 3: Implement configured rules and scheduled evaluation**

Start with the eight rules in design section 13.1. Key alerts by `(rule_code, case_id, active_window)` so repeated scheduler runs update `last_triggered_at` instead of creating duplicates.

- [ ] **Step 4: Implement acknowledgement and resolution**

Acknowledgement requires `task.alert.manage`; suppression requires a reason and expiry. A recovery event automatically resolves the matching open alert and records the resolution event ID.

- [ ] **Step 5: Run tests and commit**

Run: `cd aicp-backend && mvn -Dtest=TaskSlaServiceTest,TaskProjectionServiceTest test`

Expected: PASS.

```bash
git add aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskSlaService.java aicp-backend/src/test/java/com/aicp/module/taskcenter/service/TaskSlaServiceTest.java
git commit -m "feat: detect task SLA breaches"
```

### Task 12: Add wallet reconciliation and approved recovery commands

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskReconciliationService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskCommandService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/service/TaskReconciliationServiceTest.java`

- [ ] **Step 1: Write discrepancy and no-double-compensation tests**

```java
@Test
void paidWalletTransferWithUndeliveredOrderCreatesOneReconciliationCase() {
    wallet.transfer(orderNo, "SUCCEEDED", 2990, 0);
    reconciler.reconcileOrder(orderNo);
    reconciler.reconcileOrder(orderNo);
    assertThat(reconciliation.countOpen(orderNo, "PAID_NOT_DELIVERED")).isEqualTo(1);
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=TaskReconciliationServiceTest test`

Expected: FAIL because reconciliation cases do not exist in service code.

- [ ] **Step 3: Implement read-only comparison first**

Compare generation request/settlement facts, trade order facts, existing Outbox status, and `WalletClient.findByBusinessOrder`. Persist discrepancy fingerprints; do not mutate balances or business states during detection.

- [ ] **Step 4: Add safe automatic recovery**

Allow idempotent requery, redelivery, and asset-registration retry. Manual reverse/refund/credit operations require reason, `task.financial.compensate`, and `PENDING_APPROVAL` when amount exceeds the configured threshold. Execution must call the formal 3001 reverse/settlement API; never update a balance field.

- [ ] **Step 5: Run tests and commit**

Run: `cd aicp-backend && mvn -Dtest=TaskReconciliationServiceTest,TaskCommandServiceTest test`

Expected: PASS.

```bash
git add aicp-backend/src/main/java/com/aicp/module/taskcenter/service aicp-backend/src/test/java/com/aicp/module/taskcenter/service/TaskReconciliationServiceTest.java
git commit -m "feat: reconcile and recover task inconsistencies"
```

### Task 13: Add operator APIs and console

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/controller/OpsTaskCenterController.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskWorkOrderService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/OpsTaskCenterApiTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/service/TaskWorkOrderServiceTest.java`
- Create: `aicp-frontend/src/views/task-center/OpsTaskCenter.vue`
- Create: `aicp-frontend/src/views/task-center/components/OpsSituationBoard.vue`
- Create: `aicp-frontend/src/views/task-center/components/ExceptionQueue.vue`
- Create: `aicp-frontend/src/views/task-center/components/ReconciliationPanel.vue`
- Create: `aicp-frontend/src/views/task-center/components/AlertPanel.vue`
- Create: `aicp-frontend/src/views/task-center/components/WorkOrderPanel.vue`
- Modify: `aicp-frontend/src/api/taskCenter.js`
- Modify: `aicp-frontend/src/router/index.js`

- [ ] **Step 1: Write operator permission tests**

```java
mockMvc.perform(get("/api/v1/ops/task-center/overview").with(user(normalUser)))
    .andExpect(status().isForbidden());
mockMvc.perform(get("/api/v1/ops/task-center/overview").with(user(taskOperator)))
    .andExpect(status().isOk());
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=OpsTaskCenterApiTest test`

Expected: FAIL because operator endpoints do not exist.

- [ ] **Step 3: Implement operator endpoints and work orders**

Require `task.ops.view` for all endpoints and narrower permissions for claim/assign, alert management, reconciliation, and financial compensation. Cross-Workspace searches require a reason header and write an audit record containing the filter hash. `TaskWorkOrderService` creates at most one open work order per alert, supports claim/assign/resolve/close, and appends every ownership or resolution change to the Task Case timeline.

- [ ] **Step 4: Implement the operator console**

Add situation, generation, trade, reconciliation, alert, and work-order tabs. Show SLA breached, inconsistent, compensation exhausted, and unassigned counts. Require a reason dialog for every mutation and an approval dialog for financial commands.

- [ ] **Step 5: Run backend, frontend, and build checks**

Run: `cd aicp-backend && mvn -Dtest=OpsTaskCenterApiTest test`

Run: `cd aicp-frontend && node --test tests/task-center-state.test.js tests/navigation-contract.test.js && npm run build`

Expected: PASS and build exits 0.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/taskcenter/controller aicp-backend/src/test/java/com/aicp/module/taskcenter/OpsTaskCenterApiTest.java aicp-frontend/src/views/task-center aicp-frontend/src/api/taskCenter.js aicp-frontend/src/router/index.js
git commit -m "feat: add task operations console"
```

## R3 — Migration, cutover, and hardening

### Task 14: Backfill legacy tasks and perform dual-read cutover

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/service/TaskCenterBackfillService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/taskcenter/controller/TaskCenterMigrationController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/service/TaskCenterBackfillServiceTest.java`
- Modify: `aicp-backend/src/main/resources/application.yml`
- Modify: `aicp-frontend/src/router/index.js`

- [ ] **Step 1: Write backfill idempotency and quarantine tests**

```java
@Test
void unknownWorkspaceIsQuarantinedInsteadOfAssignedToUserOne() {
    backfill.runBatch(100);
    assertThat(quarantine.findByLegacyId(orphanTask.getId())).isPresent();
    assertThat(cases.findByExternalId(orphanTask.getUuid())).isEmpty();
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=TaskCenterBackfillServiceTest test`

Expected: FAIL because the backfill service does not exist.

- [ ] **Step 3: Implement checkpointed backfill**

Process batches of 500, persist the last source ID, and map the most recent 90 days plus all non-terminal generation tasks, orders, refunds, and failed Outbox rows. Re-running a batch must not duplicate cases/events.

- [ ] **Step 4: Add dual-read comparison metrics**

Compare counts and terminal states by Workspace/domain/day. Emit mismatch counts without repairing during reads. Gate `/task-monitor -> /task-center` cutover on zero ownership mismatches and documented state differences below 0.1%.

- [ ] **Step 5: Run migration tests and commit**

Run: `cd aicp-backend && mvn -Dtest=TaskCenterBackfillServiceTest,TaskCenterApiIntegrationTest test`

Expected: PASS.

```bash
git add aicp-backend/src/main/java/com/aicp/module/taskcenter aicp-backend/src/test/java/com/aicp/module/taskcenter/service/TaskCenterBackfillServiceTest.java aicp-backend/src/main/resources/application.yml aicp-frontend/src/router/index.js
git commit -m "feat: backfill and cut over task monitoring"
```

### Task 15: Add end-to-end, load, observability, and documentation gates

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/TaskCenterE2ETest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/taskcenter/TaskCenterReplayTest.java`
- Create: `aicp-frontend/tests/task-center-contract.test.js`
- Modify: `docs/01-core/API接口文档_V1.5.md`
- Modify: `docs/01-core/后端产品功能设计_V1.5.md`
- Modify: `docs/01-core/用户端产品功能设计.md`
- Modify: `docs/02-derived/流程图文档.md`

- [ ] **Step 1: Add end-to-end and replay tests**

Cover generation create → gateway request → asset → settlement, order → payment → delivery → release, paid-not-delivered automatic recovery, missing billing receipt reconciliation, and approved manual compensation. Replay every captured event twice and assert one financial effect and one delivery/asset effect.

- [ ] **Step 2: Add frontend contract tests**

Assert every backend summary/health/domain stage has a label, raw fallback, and appropriate action rendering. Assert financial values remain integers and prompts/payment evidence are absent from list payloads.

- [ ] **Step 3: Run the full verification suite**

Run: `cd aicp-backend && mvn test`

Run: `cd aicp-frontend && node --test tests/*.test.js && npm run build`

Run: `cd new-api && go test ./model ./controller ./middleware ./router`

Expected: all commands exit 0.

- [ ] **Step 4: Verify performance and failure behavior**

With a seeded 100k-case/1m-event dataset, verify list P95 ≤ 300ms and event-to-view P95 ≤ 5s. Kill/restart the projector and replay the last hour; assert no duplicate command, debit, delivery, refund, or asset. Disconnect SSE and verify cursor catch-up.

- [ ] **Step 5: Update canonical documentation**

Document the exact API schemas, state tables, permissions, SLA defaults, event envelope, error codes, operator approval matrix, cutover flag, rollback procedure, and runbook queries. Remove the obsolete statement that task monitoring is part of asset history.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/test/java/com/aicp/module/taskcenter aicp-frontend/tests docs/01-core docs/02-derived/流程图文档.md
git commit -m "test: verify unified task center end to end"
```

## Final release checklist

- [ ] R1 generation and trade user journeys pass with 3001 available and fail closed when it is unavailable.
- [ ] Duplicate and out-of-order events do not regress state or duplicate side effects.
- [ ] User APIs cannot read or mutate another Workspace.
- [ ] Operator APIs require explicit permissions and audit cross-Workspace searches.
- [ ] Financial compensation uses formal 3001 APIs and configured approval thresholds.
- [ ] `gen_tasks` is no longer a live monitoring source after dual-read acceptance.
- [ ] `/task-monitor` redirects to `/task-center`; `/asset-history` remains asset-only.
- [ ] All tests, builds, performance gates, migration reports, and rollback checks pass.
