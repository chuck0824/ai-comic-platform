# Production SOP Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the static production SOP demo with a real project-scoped readiness check, immutable report, work-order review, and production-admission Gate.

**Architecture:** A project-scoped SOP application service assembles read-only facts from content projects and locked storyboard versions, executes a versioned deterministic rule registry, persists immutable runs/results, and derives Gate decisions. The Vue frontend consumes typed endpoints through a pure state projection layer and exposes a project list plus SOP workspace; source business data is fixed in its owning module, never inside SOP results.

**Tech Stack:** Java 17, Spring Boot 3.2, MyBatis-Plus, MySQL/H2, JUnit 5, Mockito, Vue 3, Vue Router, Element Plus, Node test runner, Vite.

**Scope boundary:** This plan implements Phase 1 only. Canvas node coloring and image/video/adopt/export Gates belong to Phase 2. Failure recovery, capacity estimation, report export, and quality analytics belong to Phase 3.

**Feature flag:** All SOP endpoints and UI routes are gated behind a single feature flag `sop.enabled` (default `true` in dev, configurable per environment). When disabled: the SOP sidebar menu item is hidden; all `/api/v1/sop/**` endpoints return HTTP 503 with `{"code": 72006, "message": "SOP module is not enabled"}`. This allows deploying the full code without exposing the feature until ready. Remove the flag after Phase 1 acceptance.

---

## File map

### Backend domain and persistence

- Create `aicp-backend/src/main/java/com/aicp/module/sop/domain/SopEnums.java`: result, severity, run, work-order, Gate, trigger and fix-policy enums.
- Create `aicp-backend/src/main/java/com/aicp/module/sop/domain/SopRuleDefinition.java`: immutable rule metadata.
- Create `aicp-backend/src/main/java/com/aicp/module/sop/domain/SopCheckContext.java`: normalized read-only facts and source revision map.
- Create `aicp-backend/src/main/java/com/aicp/module/sop/domain/SopRuleEvaluation.java`: evaluator output and overall-status aggregation.
- Create `aicp-backend/src/main/java/com/aicp/module/sop/entity/SopCheckRun.java`, `SopCheckResult.java`, `SopWorkOrder.java`, `SopWorkOrderEvent.java`, `SopGateDecision.java`, `SopRuleSetVersion.java`: Phase 1 persistence records.
- Create matching mappers (6) under `aicp-backend/src/main/java/com/aicp/module/sop/mapper/`.
- Delete `aicp-backend/src/main/java/com/aicp/module/sop/entity/SopAudit.java` and `mapper/SopAuditMapper.java`: their fields do not match the legacy table and they must not remain an active write path.
- Create `aicp-backend/src/main/resources/db/migration/V7__production_sop_core.sql`: new tables and indexes.
- Create `aicp-backend/src/main/resources/db/migration/V7_undo.sql`: DROP statements for rollback.
- Modify `aicp-backend/src/main/resources/db/schema.sql` and `schema-h2.sql`: keep bootstrapped schemas aligned with V7.

### Backend behavior and HTTP API

- Create `SopContextAssembler.java`: reads projects, content units, storyboards, scenes, shots and visual bindings.
- Create `SopRuleCatalog.java`: declares the 13 production-readiness rules and evaluates their truth tables.
- Create `SopRuleEngine.java`: executes applicable rules and isolates per-rule errors.
- Replace `SopService.java`: application orchestration, persistence and report queries; remove hardcoded values.
- Create `SopWorkOrderService.java`: fingerprint deduplication and review state machine.
- Create `SopGateService.java`: evaluates `PRODUCTION_ADMISSION` using only a fresh completed run.
- Create `SopRequests.java` and `SopViews.java`: validated request records and stable response records.
- Replace `SopController.java`: typed project-scoped endpoints plus one compatibility endpoint.
- Modify `ErrorCode.java`: stable 72xxx SOP error codes.

### Frontend

- Create `aicp-frontend/src/api/sop.js`: all Phase 1 SOP requests.
- Create `aicp-frontend/src/views/sop/sopState.js`: pure query/result projections and labels.
- Create `SopProjectList.vue`, `SopWorkspace.vue`, `SopSummaryCards.vue`, `SopCheckTable.vue`, and `SopWorkOrderTable.vue` under `aicp-frontend/src/views/sop/`.
- Replace `aicp-frontend/src/views/Sop.vue` with a compatibility redirect component.
- Modify `aicp-frontend/src/router/index.js` and `src/components/Sidebar.vue`: remove `/sop/1` and add project-scoped routing.
- Create `aicp-frontend/tests/sop-state.test.js`; modify `tests/navigation-contract.test.js`.

---

### Task 1: Lock the Phase 1 domain contract

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/domain/SopEnums.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/domain/SopRuleDefinition.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/domain/SopRuleEvaluation.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/sop/domain/SopDomainContractTest.java`

- [ ] **Step 1: Write the failing domain contract test**

```java
@Test
void exposesStablePhaseOneEnumsAndTransitions() {
    assertThat(SopResult.values()).extracting(Enum::name)
            .containsExactly("PASS", "WARNING", "BLOCKED", "NOT_READY", "ERROR");
    assertThat(WorkOrderStatus.OPEN.canTransitionTo(WorkOrderStatus.ASSIGNED)).isTrue();
    assertThat(WorkOrderStatus.ASSIGNED.canTransitionTo(WorkOrderStatus.FIXING)).isTrue();
    assertThat(WorkOrderStatus.FIXING.canTransitionTo(WorkOrderStatus.PENDING_REVIEW)).isTrue();
    assertThat(WorkOrderStatus.PENDING_REVIEW.canTransitionTo(WorkOrderStatus.PASSED)).isTrue();
    assertThat(WorkOrderStatus.PENDING_REVIEW.canTransitionTo(WorkOrderStatus.REOPENED)).isTrue();
    assertThat(WorkOrderStatus.PASSED.canTransitionTo(WorkOrderStatus.FIXING)).isFalse();
    assertThat(GateType.values()).containsExactly(GateType.PRODUCTION_ADMISSION);
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `cd aicp-backend && mvn -q -Dtest=SopDomainContractTest test`

Expected: compilation failure because `SopEnums` does not exist.

- [ ] **Step 3: Implement the enums and immutable contracts**

```java
public final class SopEnums {
    public enum SopResult { PASS, WARNING, BLOCKED, NOT_READY, ERROR }
    public enum Severity { P0, P1, P2, P3 }
    public enum RunStatus { RUNNING, COMPLETED, STALE }
    public enum OverallStatus { GREEN, YELLOW, RED }
    public enum GateType { PRODUCTION_ADMISSION }
    public enum TriggerType { MANUAL, GATE }
    public enum FixPolicy { AUTO_SAFE, CONFIRM_REQUIRED, MANUAL_ONLY }
    public enum WorkOrderStatus {
        OPEN, ASSIGNED, FIXING, PENDING_REVIEW, PASSED, REOPENED, CANCELED;
        public boolean canTransitionTo(WorkOrderStatus next) {
            return switch (this) {
                case OPEN -> next == ASSIGNED || next == CANCELED;
                case ASSIGNED, REOPENED -> next == FIXING || next == CANCELED;
                case FIXING -> next == PENDING_REVIEW;
                case PENDING_REVIEW -> next == PASSED || next == REOPENED;
                case PASSED, CANCELED -> false;
            };
        }
    }
    private SopEnums() {}
}

public record SopRuleDefinition(
        String code, String name, String category,
        SopEnums.Severity severity, boolean critical,
        Set<SopEnums.GateType> gates, SopEnums.FixPolicy fixPolicy) {}

public record SopRuleEvaluation(
        String ruleCode, SopEnums.SopResult result, SopEnums.Severity severity,
        boolean critical,
        String targetType, String targetId, String issueFingerprint,
        Map<String, Object> evidence, String suggestion, SopEnums.FixPolicy fixPolicy) {}
```

Add error codes `72001 SOP_RUN_NOT_FOUND`, `72002 SOP_RUN_STALE`, `72003 SOP_GATE_BLOCKED`, `72004 SOP_WORK_ORDER_CONFLICT`, and `72005 SOP_INVALID_TRANSITION` to `ErrorCode` using the enum's existing constructor style.

- [ ] **Step 4: Run the domain test**

Run: `cd aicp-backend && mvn -q -Dtest=SopDomainContractTest test`

Expected: PASS.

- [ ] **Step 5: Commit the domain contract**

```bash
git add aicp-backend/src/main/java/com/aicp/module/sop/domain \
  aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java \
  aicp-backend/src/test/java/com/aicp/module/sop/domain/SopDomainContractTest.java
git commit -m "feat: define production SOP domain contract"
```

### Task 2: Add the Phase 1 schema

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V7__production_sop_core.sql`
- Modify: `aicp-backend/src/main/resources/db/schema.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Test: `aicp-backend/src/test/java/com/aicp/module/sop/schema/SopSchemaTest.java`

- [ ] **Step 1: Write the failing schema test**

```java
@Test
void phaseOneSchemaContainsImmutableRunsResultsWorkOrdersAndGates() throws Exception {
    String sql = Files.readString(Path.of("src/main/resources/db/migration/V7__production_sop_core.sql"));
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sop_check_runs");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sop_check_results");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sop_work_orders");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sop_work_order_events");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sop_gate_decisions");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sop_rule_set_versions");
    assertThat(sql).contains("UNIQUE (project_id, scope_hash, rule_set_version, snapshot_hash)");
    assertThat(sql).contains("UNIQUE (project_id, issue_fingerprint, active_marker)");
}
```

- [ ] **Step 2: Run the test and verify the migration is missing**

Run: `cd aicp-backend && mvn -q -Dtest=SopSchemaTest test`

Expected: FAIL with `NoSuchFileException` for V7.

- [ ] **Step 3: Create V7 with explicit columns and indexes**

The migration must create:

```sql
CREATE TABLE IF NOT EXISTS sop_check_runs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  content_unit_id BIGINT NULL,
  canvas_project_id BIGINT NULL,
  gate_type VARCHAR(40) NULL,
  trigger_type VARCHAR(20) NOT NULL,
  rule_set_version VARCHAR(32) NOT NULL,
  scope_hash VARCHAR(64) NOT NULL,
  snapshot_hash VARCHAR(64) NOT NULL,
  source_revisions_json TEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  overall_status VARCHAR(20) NULL,
  passed_count INT NOT NULL DEFAULT 0,
  warning_count INT NOT NULL DEFAULT 0,
  blocked_count INT NOT NULL DEFAULT 0,
  not_ready_count INT NOT NULL DEFAULT 0,
  error_count INT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  UNIQUE (project_id, scope_hash, rule_set_version, snapshot_hash)
);
```

Define `sop_check_results` with `run_id`, rule/result/severity, `critical`, target, fingerprint, `evidence_json`, suggestion and fix policy; `sop_work_orders` with source result, assignment, row version and application-managed nullable `active_marker` (`1` while active, `NULL` after `PASSED/CANCELED`); append-only events; `sop_rule_set_versions` with version identifier, rule count, published metadata and active flag; and Gate decisions with request idempotency key. The unique key `(project_id, issue_fingerprint, active_marker)` prevents duplicate active work while allowing historical closed rows. Add project/run/status indexes. V7 creates the repository's legacy `sop_audits` shape when absent, copies each legacy row to a work order plus `LEGACY_IMPORTED` event using fingerprint `legacy-audit:{id}`, and leaves the old table read-only. Copy the same logical schema into both bootstrap schema files using H2-compatible types in `schema-h2.sql`.

- [ ] **Step 4: Run schema and application-context tests**

Run: `cd aicp-backend && mvn -q -Dtest=SopSchemaTest,ContentProjectSchemaTest test`

Expected: PASS.

- [ ] **Step 5: Commit the migration**

```bash
git add aicp-backend/src/main/resources/db/migration/V7__production_sop_core.sql \
  aicp-backend/src/main/resources/db/schema.sql \
  aicp-backend/src/main/resources/db/schema-h2.sql \
  aicp-backend/src/test/java/com/aicp/module/sop/schema/SopSchemaTest.java
git commit -m "feat: add production SOP core schema"
```

**Migration rollback**：V7 is append-only (creates tables + copies legacy data without dropping old tables). Rollback is safe via `DROP TABLE IF EXISTS` for each new table. No data loss risk for existing `sop_audits` rows. Create `V7_undo.sql` alongside the migration with the corresponding DROP statements.

### Task 3: Map persistence records without business logic

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/entity/SopCheckRun.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/entity/SopCheckResult.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/entity/SopWorkOrder.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/entity/SopWorkOrderEvent.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/entity/SopGateDecision.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/entity/SopRuleSetVersion.java`
- Create: six matching mapper interfaces in `aicp-backend/src/main/java/com/aicp/module/sop/mapper/`
- Delete: `aicp-backend/src/main/java/com/aicp/module/sop/entity/SopAudit.java`
- Delete: `aicp-backend/src/main/java/com/aicp/module/sop/mapper/SopAuditMapper.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/sop/schema/SopEntityMappingTest.java`

- [ ] **Step 1: Write reflection tests for table and optimistic-lock mapping**

```java
@Test
void workOrderMapsToDedicatedTableAndUsesOptimisticLock() throws Exception {
    assertThat(SopWorkOrder.class.getAnnotation(TableName.class).value())
            .isEqualTo("sop_work_orders");
    assertThat(SopWorkOrder.class.getDeclaredField("rowVersion").isAnnotationPresent(Version.class))
            .isTrue();
    assertThat(SopCheckRun.class.getDeclaredField("projectId").getType()).isEqualTo(Long.class);
}
```

- [ ] **Step 2: Run and verify compilation fails**

Run: `cd aicp-backend && mvn -q -Dtest=SopEntityMappingTest test`

Expected: compilation failure for missing entities.

- [ ] **Step 3: Add one entity per table and one empty `BaseMapper<T>` per entity**

Use `Long` IDs/project IDs, `String` database enum values, `Integer rowVersion`, `LocalDateTime` timestamps, `@TableName`, `@TableId(type = IdType.AUTO)`, `@Version`, and the repository's existing insert/update fill annotations. Do not add state transitions or aggregation methods to entities. Delete the mismatched legacy `SopAudit` entity and mapper after V7 provides the one-time SQL import.

- [ ] **Step 4: Run entity and schema tests**

Run: `cd aicp-backend && mvn -q -Dtest=SopEntityMappingTest,SopSchemaTest test`

Expected: PASS.

- [ ] **Step 5: Commit persistence mappings**

```bash
git add aicp-backend/src/main/java/com/aicp/module/sop/entity \
  aicp-backend/src/main/java/com/aicp/module/sop/mapper \
  aicp-backend/src/test/java/com/aicp/module/sop/schema/SopEntityMappingTest.java
git commit -m "feat: map production SOP persistence records"
```

### Task 4: Assemble truthful source facts

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/domain/SopCheckContext.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/service/SopContextAssembler.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/sop/service/SopContextAssemblerTest.java`

- [ ] **Step 1: Write tests for locked storyboard selection and missing sources**

```java
@Test
void marksStoryboardUnavailableWhenNoLockedVersionExists() {
    when(projectMapper.selectById(3L)).thenReturn(project(3L, 2));
    when(storyboardMapper.selectList(any())).thenReturn(List.of(storyboard(null)));
    SopCheckContext context = assembler.assemble(3L, 8L, null);
    assertThat(context.sourceAvailability()).containsEntry("locked_storyboard", false);
    assertThat(context.shots()).isEmpty();
}

@Test
void hashesOnlyStableSourceIdsAndRevisions() {
    SopCheckContext context = assembler.assemble(3L, 8L, null);
    assertThat(context.sourceRevisions()).containsKeys(
            "project:3", "content-unit:8", "storyboard-version:21");
    assertThat(context.snapshotHash()).matches("[0-9a-f]{64}");
}
```

- [ ] **Step 2: Run and verify missing assembler failures**

Run: `cd aicp-backend && mvn -q -Dtest=SopContextAssemblerTest test`

Expected: compilation failure for missing context and assembler.

- [ ] **Step 3: Implement the normalized context**

```java
public record SopCheckContext(
        Long projectId, Long contentUnitId, Long canvasProjectId,
        ContentProject project, ContentUnit contentUnit,
        Storyboard storyboard, StoryboardVersion lockedVersion,
        List<StoryboardScene> scenes, List<StoryboardShot> shots,
        Map<Long, List<StoryboardShotVisualBinding>> visualBindings,
        Map<String, Boolean> sourceAvailability,
        Map<String, Integer> sourceRevisions,
        String scopeHash, String snapshotHash) {}
```

Assembler rules: the application service first requires `Action.PRODUCE`; the assembler verifies the unit belongs to the project, chooses only `currentLockedVersionId`, fetches scenes/shots/bindings for that version, never falls back to draft, uses sorted `sourceRevisions` JSON and SHA-256 to compute `snapshotHash`, and represents absent sources in `sourceAvailability` instead of inventing facts.

- [ ] **Step 4: Run assembler tests**

Run: `cd aicp-backend && mvn -q -Dtest=SopContextAssemblerTest test`

Expected: PASS.

- [ ] **Step 5: Commit context assembly**

```bash
git add aicp-backend/src/main/java/com/aicp/module/sop/domain/SopCheckContext.java \
  aicp-backend/src/main/java/com/aicp/module/sop/service/SopContextAssembler.java \
  aicp-backend/src/test/java/com/aicp/module/sop/service/SopContextAssemblerTest.java
git commit -m "feat: assemble production SOP source context"
```

### Task 5: Implement the versioned 13-rule catalog

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/service/SopRuleCatalog.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/service/SopRuleEngine.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/sop/service/SopRuleCatalogTest.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/sop/service/SopRuleEngineTest.java`

- [ ] **Step 1: Write parameterized truth-table tests for all 13 rule codes**

```java
@ParameterizedTest
@CsvSource({
  "SCENE_GOAL,PASS", "BEAT_COMPLETENESS,PASS",
  "RELATIONSHIP_CHANGE,PASS", "KEY_DIALOGUE_LOCK,PASS", "ASSET_BINDING,BLOCKED",
  "PROMPT_LENGTH,BLOCKED", "DUB_SUBTITLE_READY,WARNING"
})
void evaluatesEnabledRuleOutcome(String code, SopResult expected) {
    assertThat(catalog.evaluate(code, fixtureContext()).result()).isEqualTo(expected);
}

@Test
void skipsDisabledRulesAndReturnsEmptyResults() {
    List<String> disabled = List.of("PLOT_FIDELITY", "RISK_SHOT_MARKING",
        "COMPLEX_SHOT_SPLIT", "IMAGE_VIDEO_TABLE_SPLIT",
        "VOICE_BINDING", "CONTINUITY_INHERITANCE");
    disabled.forEach(code ->
        assertThat(catalog.evaluate(code, fixtureContext())).isNull());
}
```

- [ ] **Step 2: Run and verify the catalog is missing**

Run: `cd aicp-backend && mvn -q -Dtest=SopRuleCatalogTest,SopRuleEngineTest test`

Expected: compilation failure for missing catalog and engine.

- [ ] **Step 3: Implement rule metadata and exact Phase 1 truth tables**

Use rule-set version `production-readiness-v1`. The following 7 rules are **enabled** and have evaluable data sources; the remaining 6 are **disabled** (`enabled=false`) because their upstream data sources do not yet exist:

**Enabled rules (7):**

1. `SCENE_GOAL`: `BLOCKED/P1` when any scene lacks `dramaticGoal`; otherwise `PASS`.
2. `BEAT_COMPLETENESS`: `BLOCKED/P1` when any scene lacks `beatDescription`; otherwise `PASS`.
3. `RELATIONSHIP_CHANGE`: `WARNING/P2` when speaking/action shots lack `relationshipBlocking`; otherwise `PASS`.
4. `KEY_DIALOGUE_LOCK`: `PASS` only when a locked version exists and dialogue shots have `dialogueText`; missing locked version is `NOT_READY`.
5. `ASSET_BINDING`: `BLOCKED/P1` when scenes lack `locationRefId` or shots lack visual bindings.
6. `PROMPT_LENGTH`: `BLOCKED/P1` for image/video prompts over 500 characters; otherwise `PASS`.
7. `DUB_SUBTITLE_READY`: `BLOCKED/P1` when dialogue exists but `dubText` or `subtitleText` is blank; otherwise `PASS`.

**Disabled rules (6)** — evaluator code is fully implemented and tested, but `enabled=false` in `production-readiness-v1`:
- `PLOT_FIDELITY` (P0): requires structured locked-source comparison engine.
- `RISK_SHOT_MARKING` (P1): requires complexity grading data.
- `COMPLEX_SHOT_SPLIT` (P1): requires D/E grade + split strategy pair.
- `IMAGE_VIDEO_TABLE_SPLIT` (P2): requires both image and video prompt tables.
- `VOICE_BINDING` (P1): requires Voice ID relation.
- `CONTINUITY_INHERITANCE` (P0): requires continuity snapshot attached to locked version.

The rule engine **skips** disabled rules: they do not produce results, do not participate in Gate decisions, and do not affect overall status. When an upstream data source becomes available, the rule's `enabled` flag is set to `true` in a new rule-set version `production-readiness-v2` — no code change required.

Rule severities in `production-readiness-v1`: `SCENE_GOAL`, `BEAT_COMPLETENESS`, `KEY_DIALOGUE_LOCK`, `ASSET_BINDING`, `PROMPT_LENGTH`, `DUB_SUBTITLE_READY` are P1; `RELATIONSHIP_CHANGE` is P2. All P1 rules are critical. Every non-pass evaluation must include target IDs, evidence, suggestion, stable issue fingerprint and fix policy. The engine catches exceptions per rule and emits `ERROR` without dropping other results.

- [ ] **Step 4: Run catalog and engine tests**

Run: `cd aicp-backend && mvn -q -Dtest=SopRuleCatalogTest,SopRuleEngineTest test`

Expected: PASS with 7 enabled evaluations + 6 skipped disabled rules.

- [ ] **Step 5: Commit the rule engine**

```bash
git add aicp-backend/src/main/java/com/aicp/module/sop/service/SopRuleCatalog.java \
  aicp-backend/src/main/java/com/aicp/module/sop/service/SopRuleEngine.java \
  aicp-backend/src/test/java/com/aicp/module/sop/service/SopRuleCatalogTest.java \
  aicp-backend/src/test/java/com/aicp/module/sop/service/SopRuleEngineTest.java
git commit -m "feat: evaluate production readiness rules"
```

### Task 6: Persist immutable checks and derive summaries

**Files:**
- Replace: `aicp-backend/src/main/java/com/aicp/module/sop/service/SopService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/dto/SopViews.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/sop/service/SopServiceTest.java`

- [ ] **Step 1: Write failing tests for reuse, aggregation and staleness**

```java
@Test
void reusesCompletedRunForSameSnapshotAndRuleVersion() {
    when(runMapper.selectOne(any())).thenReturn(completedRun(41L, "abc"));
    CheckReportView report = service.runCheck(7L, 3L,
            new RunCheckRequest(8L, null, TriggerType.MANUAL));
    assertThat(report.runId()).isEqualTo(41L);
    verify(runMapper, never()).insert(any());
}

@Test
void criticalNotReadyProducesRedOverall() {
    OverallStatus status = service.aggregate(List.of(evaluation("VOICE_BINDING", NOT_READY, P1, true)));
    assertThat(status).isEqualTo(RED);
}
```

- [ ] **Step 2: Run and verify failures against the hardcoded service**

Run: `cd aicp-backend && mvn -q -Dtest=SopServiceTest test`

Expected: compilation/test failure because the current service has no typed check use case.

- [ ] **Step 3: Implement the check transaction**

`runCheck` must call `ProjectAccessService.require(..., Action.PRODUCE)`, assemble context, reuse a completed matching run, insert `RUNNING`, evaluate enabled rules (7 in `production-readiness-v1`), batch-insert results, update counts/overall/completed time, and return `CheckReportView`. `getReport` recomputes staleness by comparing the current `snapshotHash` (SHA-256 of sorted `sourceRevisions` JSON) against the stored hash, and marks the run as `STALE` without rewriting the original report — the `status` column is the only field updated post-completion. `listProjects`, `summary`, `listChecks`, and `getReport` require `Action.VIEW` and only return projects the user can access.

**Existing test migration**：The current `SopService` has hardcoded-data tests. Delete `aicp-backend/src/test/java/com/aicp/module/sop/service/SopServiceTest.java` (if it exists) and replace with the new `SopServiceTest` defined in this task. The new test covers reuse, aggregation, staleness, and pagination — the old hardcoded assertions are irrelevant to the new architecture.

Define response records in `SopViews`: `ProjectRiskSummary`, `SopSummaryView`, `CheckRunSummary`, `CheckResultView`, `CheckReportView`, `WorkOrderView`, and `GateDecisionView`. JSON field names follow the existing global Jackson strategy; do not expose persistence entities.

- [ ] **Step 4: Run service tests**

Run: `cd aicp-backend && mvn -q -Dtest=SopServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit immutable check orchestration**

```bash
git add aicp-backend/src/main/java/com/aicp/module/sop/service/SopService.java \
  aicp-backend/src/main/java/com/aicp/module/sop/dto/SopViews.java \
  aicp-backend/src/test/java/com/aicp/module/sop/service/SopServiceTest.java
git commit -m "feat: persist immutable SOP check reports"
```

#### Performance and observability

- **Check execution**：7 enabled rules execute sequentially within a single transaction. Expected duration < 2s for projects with ≤ 200 shots.
- **Rule-level logging**：Each rule evaluation logs `rule_code`, `result`, `duration_ms`, and `error` (if any) at INFO level. The engine logs a summary line with total duration and result counts.
- **Metric counters**：Increment Micrometer counters for `sop.checks.total`, `sop.checks.{PASS|WARNING|BLOCKED|NOT_READY|ERROR}`, `sop.gate.{allowed|denied}`, and `sop.work_order.{created|assigned|passed|reopened}`.
- **Slow check alert**：Checks exceeding 5s log a WARN with project ID and shot count for diagnosis.

### Task 7: Implement work-order assignment and review

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/service/SopWorkOrderService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/dto/SopRequests.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/sop/service/SopWorkOrderServiceTest.java`

- [ ] **Step 1: Write failing lifecycle and deduplication tests**

```java
@Test
void rejectsDuplicateActiveIssueFingerprint() {
    when(orderMapper.selectCount(any())).thenReturn(1L);
    assertThatThrownBy(() -> service.create(7L, 3L,
            new CreateWorkOrderRequest(55L, 9L, "director", null)))
            .isInstanceOf(BizException.class)
            .extracting("code").isEqualTo(ErrorCode.SOP_WORK_ORDER_CONFLICT.getCode());
}

@Test
void reviewerCanReopenPendingReviewButCannotPassOpenOrder() {
    when(orderMapper.selectById(5L)).thenReturn(order(PENDING_REVIEW));
    service.review(8L, 3L, 5L, new ReviewWorkOrderRequest(false, "连续性仍未修复"));
    verify(eventMapper).insert(argThat(e -> "REOPENED".equals(e.getToStatus())));
}
```

- [ ] **Step 2: Run and verify missing service/request failures**

Run: `cd aicp-backend && mvn -q -Dtest=SopWorkOrderServiceTest test`

Expected: compilation failure.

- [ ] **Step 3: Implement typed requests and state transitions**

```java
public record CreateWorkOrderRequest(
        @NotNull Long runId, @NotNull Long resultId,
        @NotBlank String responsibleRole, Long assigneeId) {}
public record RunCheckRequest(
        Long contentUnitId, Long canvasProjectId, @NotNull TriggerType triggerType) {}
public record GateRequest(
        @NotNull GateType gateType, Long contentUnitId, Long canvasProjectId,
        @NotBlank String idempotencyKey) {}
public record TransitionWorkOrderRequest(@NotNull WorkOrderStatus toStatus, String note) {}
public record ReviewWorkOrderRequest(boolean approved, @NotBlank String note) {}
```

Creation requires `Action.PRODUCE`, verifies the result belongs to the project and is not `PASS`, computes the active fingerprint uniqueness key, and inserts `OPEN` plus a `CREATED` event. Assignment also requires `Action.PRODUCE`. An assigned member may move only their own order from `ASSIGNED/REOPENED` to `FIXING` and then `PENDING_REVIEW`; each action first requires project membership through `Action.VIEW`. Review requires `Action.REVIEW`. Every transition checks `canTransitionTo`, uses optimistic lock, and appends an event in the same transaction.

- [ ] **Step 4: Run lifecycle tests**

Run: `cd aicp-backend && mvn -q -Dtest=SopWorkOrderServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit work-order lifecycle**

```bash
git add aicp-backend/src/main/java/com/aicp/module/sop/service/SopWorkOrderService.java \
  aicp-backend/src/main/java/com/aicp/module/sop/dto/SopRequests.java \
  aicp-backend/src/test/java/com/aicp/module/sop/service/SopWorkOrderServiceTest.java
git commit -m "feat: manage SOP work order review lifecycle"
```

### Task 8: Enforce the production-admission Gate

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/sop/service/SopGateService.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/sop/service/SopGateServiceTest.java`

- [ ] **Step 1: Write failing allow/deny/idempotency tests**

```java
@Test
void deniesWhenReportHasBlockedOrCriticalNotReadyResults() {
    when(sopService.runCheck(anyLong(), anyLong(), any())).thenReturn(redReport(41L));
    GateDecisionView decision = gate.evaluate(7L, 3L,
            new GateRequest(GateType.PRODUCTION_ADMISSION, 8L, null, "req-1"));
    assertThat(decision.allowed()).isFalse();
    assertThat(decision.blockerCount()).isEqualTo(2);
}

@Test
void returnsExistingDecisionForSameIdempotencyKey() {
    when(decisionMapper.selectOne(any())).thenReturn(allowedDecision("req-1"));
    assertThat(gate.evaluate(7L, 3L, request("req-1")).allowed()).isTrue();
    verify(sopService, never()).runCheck(anyLong(), anyLong(), any());
}
```

- [ ] **Step 2: Run and verify the Gate is missing**

Run: `cd aicp-backend && mvn -q -Dtest=SopGateServiceTest test`

Expected: compilation failure.

- [ ] **Step 3: Implement fail-closed Gate evaluation**

`evaluate` checks idempotency first, triggers/reuses a Gate-scoped check, rejects stale reports, allows only when there are no `BLOCKED`, critical `NOT_READY`, or critical `ERROR` results, persists the decision, and returns blocker result IDs and suggestions. A Gate decision is evidence, not a client override token; future production-command integration must verify its project, scope hash and snapshot hash server-side.

- [ ] **Step 4: Run Gate tests**

Run: `cd aicp-backend && mvn -q -Dtest=SopGateServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit Gate enforcement**

```bash
git add aicp-backend/src/main/java/com/aicp/module/sop/service/SopGateService.java \
  aicp-backend/src/test/java/com/aicp/module/sop/service/SopGateServiceTest.java
git commit -m "feat: enforce production admission SOP gate"
```

### Task 9: Replace the hardcoded HTTP controller

**Files:**
- Replace: `aicp-backend/src/main/java/com/aicp/module/sop/controller/SopController.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/sop/SopApiIntegrationTest.java`

- [ ] **Step 1: Write controller tests for typed project endpoints and compatibility**

```java
@Test
void compatibilityReadinessEndpointDelegatesToNewUseCase() throws Exception {
    mvc.perform(post("/api/v1/sop/check/production-readiness")
            .contentType(APPLICATION_JSON)
            .content("{\"project_id\":3,\"content_unit_id\":8}"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.data.runId").value(41));
    verify(sopService).runCheck(anyLong(), eq(3L), any(RunCheckRequest.class));
}
```

- [ ] **Step 2: Run and verify current hardcoded responses fail delegation assertions**

Run: `cd aicp-backend && mvn -q -Dtest=SopApiIntegrationTest test`

Expected: FAIL because the current controller neither injects services nor returns typed reports.

- [ ] **Step 3: Implement the Phase 1 endpoint surface**

Expose (Phase 1 only; releases and canvas endpoints are Phase 2):

```text
GET  /api/v1/sop/projects
GET  /api/v1/sop/projects/{projectId}/summary
POST /api/v1/sop/projects/{projectId}/checks
GET  /api/v1/sop/projects/{projectId}/checks
GET  /api/v1/sop/projects/{projectId}/checks/{runId}
GET  /api/v1/sop/projects/{projectId}/work-orders
POST /api/v1/sop/projects/{projectId}/work-orders
PATCH /api/v1/sop/projects/{projectId}/work-orders/{id}
POST /api/v1/sop/projects/{projectId}/work-orders/{id}/review
POST /api/v1/sop/projects/{projectId}/gates/production-admission/evaluate
POST /api/v1/sop/projects/{projectId}/fixes/{resultId}
POST /api/v1/sop/check/production-readiness  (compat)
```

All list endpoints must accept `page`, `size`, `sort`, `order` query parameters and return paginated responses with `{page, size, total, totalPages, items}` structure. Deferred endpoints (`/releases`, `/canvas/*`, `/failures`, `/capacity`) return HTTP 501 with a clear phase indicator.

Use `@RequiredArgsConstructor`, `@Valid`, `SecurityUtil.requireCurrentUserId()`, request records and response records. Remove every fixed project title, date, result and `System.currentTimeMillis()` identifier from the controller.

- [ ] **Step 4: Run SOP API and security tests**

Run: `cd aicp-backend && mvn -q -Dtest=SopApiIntegrationTest,ProjectAccessServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit the API**

```bash
git add aicp-backend/src/main/java/com/aicp/module/sop/controller/SopController.java \
  aicp-backend/src/test/java/com/aicp/module/sop/SopApiIntegrationTest.java
git commit -m "feat: expose project scoped production SOP API"
```

### Task 10: Build the frontend SOP state contract first

**Files:**
- Create: `aicp-frontend/src/api/sop.js`
- Create: `aicp-frontend/src/views/sop/sopState.js`
- Test: `aicp-frontend/tests/sop-state.test.js`

- [ ] **Step 1: Write failing pure-state tests**

```javascript
test('critical NOT_READY maps to red and cannot enter production', () => {
  const model = mapSopReport({ overallStatus: 'RED', results: [
    { ruleCode: 'VOICE_BINDING', result: 'NOT_READY', severity: 'P1', critical: true }
  ] })
  assert.equal(model.canEnterProduction, false)
  assert.equal(model.groups.notReady.length, 1)
})

test('serializes project scope without empty filters', () => {
  assert.deepEqual(serializeSopScope({ contentUnitId: 8, canvasProjectId: null }),
    { content_unit_id: 8 })
})
```

- [ ] **Step 2: Run and verify the module is missing**

Run: `cd aicp-frontend && node --test tests/sop-state.test.js`

Expected: FAIL with module-not-found.

- [ ] **Step 3: Implement API calls and pure projections**

```javascript
export const RESULT_LABELS = {
  PASS: '通过', WARNING: '告警', BLOCKED: '阻断',
  NOT_READY: '待配置', ERROR: '检查异常'
}

export function mapSopReport(report = {}) {
  const results = report.results || []
  const by = result => results.filter(item => item.result === result)
  return {
    ...report,
    groups: {
      passed: by('PASS'), warnings: by('WARNING'), blocked: by('BLOCKED'),
      notReady: by('NOT_READY'), errors: by('ERROR')
    },
    canEnterProduction: report.gateAllowed === true && report.status !== 'STALE'
  }
}

export function serializeSopScope(scope) {
  const value = {}
  if (scope.contentUnitId) value.content_unit_id = scope.contentUnitId
  if (scope.canvasProjectId) value.canvas_project_id = scope.canvasProjectId
  return value
}
```

`src/api/sop.js` must provide `listProjects`, `getSummary`, `runCheck`, `listChecks`, `getCheck`, `listWorkOrders`, `createWorkOrder`, `transitionWorkOrder`, `reviewWorkOrder`, and `evaluateAdmission` using the existing `request` wrapper.

- [ ] **Step 4: Run state tests**

Run: `cd aicp-frontend && node --test tests/sop-state.test.js`

Expected: PASS.

- [ ] **Step 5: Commit the frontend contract**

```bash
git add aicp-frontend/src/api/sop.js \
  aicp-frontend/src/views/sop/sopState.js \
  aicp-frontend/tests/sop-state.test.js
git commit -m "feat: add production SOP frontend state contract"
```

### Task 11: Replace the static page with project list and workspace

**Files:**
- Create: `aicp-frontend/src/views/sop/SopProjectList.vue`
- Create: `aicp-frontend/src/views/sop/SopWorkspace.vue`
- Create: `aicp-frontend/src/views/sop/components/SopSummaryCards.vue`
- Create: `aicp-frontend/src/views/sop/components/SopCheckTable.vue`
- Create: `aicp-frontend/src/views/sop/components/SopWorkOrderTable.vue`
- Replace: `aicp-frontend/src/views/Sop.vue`
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/src/components/Sidebar.vue`
- Modify: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Extend navigation contract tests**

```javascript
test('production SOP uses list and project-scoped routes', () => {
  const router = read('src/router/index.js')
  const sidebar = read('src/components/Sidebar.vue')
  assert.match(router, /path: 'sop'/)
  assert.match(router, /path: 'content-projects\/:projectId\/sop'/)
  assert.match(sidebar, /to="\/sop"/)
  assert.doesNotMatch(sidebar, /\/sop\/1/)
})
```

- [ ] **Step 2: Run and verify navigation contract failure**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js tests/sop-state.test.js`

Expected: FAIL because the sidebar still links to `/sop/1`.

- [ ] **Step 3: Implement the list and workspace states**

`SopProjectList.vue` loads authorized projects and displays loading, error, empty and risk-summary states. `SopWorkspace.vue` loads project summary, content-unit scope, latest report and work orders; it renders the three focused child components and exposes “重新检查”, “创建返工单”, “认领/处理/提交复核” and “复核通过/驳回” only when allowed by returned actions. Every mutation reloads summary, report and work orders from the server.

`SopCheckTable.vue` shows result, severity, evidence, target and suggestion; `NOT_READY` uses “待配置” rather than “不通过”. `Sop.vue` reads its old `projectId` route parameter and redirects to `/content-projects/{projectId}/sop` for bookmarked compatibility.

- [ ] **Step 4: Run frontend tests and production build**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js tests/sop-state.test.js && npm run build`

Expected: tests PASS and Vite build exits 0.

- [ ] **Step 5: Commit the real SOP workspace**

```bash
git add aicp-frontend/src/views/sop aicp-frontend/src/views/Sop.vue \
  aicp-frontend/src/router/index.js aicp-frontend/src/components/Sidebar.vue \
  aicp-frontend/tests/navigation-contract.test.js
git commit -m "feat: replace static SOP page with project workspace"
```

### Task 12: Prove the Phase 1 end-to-end lifecycle

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/sop/SopPhaseOneE2ETest.java`
- Modify: `docs/01-core/API接口文档_V1.5.md`
- Modify: `docs/01-core/用户端PRD.md`

- [ ] **Step 1: Write the end-to-end service lifecycle and error-path tests**

Happy path:

```java
@Test
void blockedIssueCanBeAssignedFixedReviewedRecheckedAndAdmitted() {
    CheckReportView first = fixture.runReadinessCheckWithPromptOverLimit();
    assertThat(first.overallStatus()).isEqualTo("RED");
    WorkOrderView order = fixture.createAndAssign(first, "ai_artist");
    fixture.fixSourcePrompt(order.targetId(), "short prompt");
    fixture.submitAndApprove(order.id());
    CheckReportView second = fixture.recheck();
    assertThat(second.runId()).isNotEqualTo(first.runId());
    assertThat(second.results()).noneMatch(r -> "PROMPT_LENGTH".equals(r.ruleCode())
            && "BLOCKED".equals(r.result()));
    // After fixing PROMPT_LENGTH, admission may still be blocked by other enabled
    // rules (e.g. KEY_DIALOGUE_LOCK returning NOT_READY when no locked version
    // exists in the fixture, or ASSET_BINDING returning BLOCKED for missing assets).
    // Disabled rules (PLOT_FIDELITY, CONTINUITY_INHERITANCE, etc.) do NOT block.
    assertThat(fixture.evaluateAdmission().blockers())
            .allMatch(b -> "NOT_READY".equals(b.result())
                    || "BLOCKED".equals(b.result()));
}
```

The final assertion is blocked by honest enabled-rule results; Phase 1 must never turn unknown facts into green results. Disabled rules do not participate — their `NOT_READY` would have been silent, not blocking.

Error path tests (same test class):

```java
@Test
void rejectsCrossProjectWorkOrderAccess() {
    // User with project 7 access cannot view/work on project 9 work orders
    assertThatThrownBy(() -> fixture.transitionAs(7L, 3L, orderFromProject9))
            .isInstanceOf(BizException.class)
            .extracting("code").isEqualTo(ErrorCode.FORBIDDEN.getCode());
}

@Test
void concurrentWorkOrderUpdateFailsWithOptimisticLock() {
    SopWorkOrder order = fixture.createAndAssign(first, "ai_artist");
    // Simulate concurrent update by directly bumping row version
    jdbcTemplate.update("UPDATE sop_work_orders SET row_version = row_version + 1 WHERE id = ?", order.id());
    assertThatThrownBy(() -> fixture.transitionToFixing(order.id()))
            .isInstanceOf(BizException.class)
            .extracting("code").isEqualTo(ErrorCode.SOP_WORK_ORDER_CONFLICT.getCode());
}

@Test
void gateReturnsExistingDecisionForSameIdempotencyKey() {
    GateDecisionView first = fixture.evaluateAdmission("key-abc");
    GateDecisionView second = fixture.evaluateAdmission("key-abc");
    assertThat(second.decisionId()).isEqualTo(first.decisionId());
    // Only one check run created
    assertThat(fixture.countCheckRuns()).isEqualTo(1);
}

@Test
void staleReportIsRejectedByGate() {
    CheckReportView report = fixture.runReadinessCheck();
    // Simulate source data change
    fixture.modifyStoryboardVersion();
    assertThatThrownBy(() -> fixture.evaluateAdmissionWithRun(report.runId()))
            .isInstanceOf(BizException.class)
            .extracting("code").isEqualTo(ErrorCode.SOP_RUN_STALE.getCode());
}
```

- [ ] **Step 2: Run the focused E2E test**

Run: `cd aicp-backend && mvn -q -Dtest=SopPhaseOneE2ETest test`

Expected: PASS and two distinct immutable check runs.

- [ ] **Step 3: Update authoritative API and PRD sections**

Document the project-scoped endpoints, five result enums, `STALE`, work-order transitions, production-admission Gate and compatibility endpoint. Mark canvas coloring/Gates as Phase 2 and failure/capacity/reporting as Phase 3; do not describe those capabilities as delivered in Phase 1.

- [ ] **Step 4: Run complete verification**

Run: `cd aicp-backend && mvn -q test`

Expected: all backend tests PASS.

Run: `cd aicp-frontend && node --test tests/*.test.js && npm run build`

Expected: all frontend tests PASS and Vite build exits 0.

Run: `git diff --check`

Expected: no whitespace errors.

- [ ] **Step 5: Commit E2E coverage and docs**

```bash
git add aicp-backend/src/test/java/com/aicp/module/sop/SopPhaseOneE2ETest.java \
  docs/01-core/API接口文档_V1.5.md docs/01-core/用户端PRD.md
git commit -m "test: verify production SOP phase one lifecycle"
```

---

## Phase 1 completion gate

Do not mark Phase 1 complete unless all conditions hold:

1. `Sop.vue`, `SopController`, and `SopService` contain no fixed project names, results, dates or generated IDs.
2. Every check result has a real source fact or an explicit `NOT_READY` dependency; disabled rules produce no results.
3. Completed reports are immutable and become `STALE` when source revisions change.
4. Work orders cannot skip assignment, fixing, pending review or review decisions.
5. P0/P1 and critical unknown/error results from **enabled rules** deny production admission.
6. The sidebar no longer links to `/sop/1`.
7. Backend tests, frontend tests and the production build pass.
8. Core API and PRD documents describe Phase 1 truthfully and defer Phase 2/3 behavior.
9. `sop_rule_set_versions` table is populated with `production-readiness-v1` (7 enabled + 6 disabled rules).
10. All list endpoints return paginated responses with `page`, `size`, `total`, `totalPages`, `items`.
11. Feature flag `sop.enabled=false` hides the sidebar entry and returns 503 from all SOP endpoints.
12. E2E tests cover the happy path + unauthorized access + optimistic lock conflict + idempotency + stale report rejection.
13. Micrometer counters are registered for checks, gate decisions, and work order transitions.
14. V7 migration has a companion `V7_undo.sql` for rollback.
