# Canvas Production Kernel R1 Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立探索/正式生产双模式、ShotWorkUnit、类型化端口、不可变请求快照、候选和正式采用，并以单画布事务将旧数据迁移到新生产内核。

**Architecture:** 扩展现有 Canvas 与 generation 域，不建立第二套任务中心。新链路写 V2 事实表；旧数据通过 `legacy-adapter` 影子读取，确认后单向升级。`ShotAdoption` 是正式采用唯一事实源，`GenerationVariant` 只保留兼容读取。

**Tech Stack:** Vue 3, Node test runner, Spring Boot 3, MyBatis-Plus, MySQL/H2, JUnit 5, Jackson, existing generation task/event infrastructure.

---

## File map

- Create `aicp-backend/src/main/resources/db/migration/V12__canvas_production_kernel.sql` and `V12_undo.sql`.
- Modify `aicp-backend/src/main/resources/db/schema-h2.sql`, `schema-mysql.sql`, `schema.sql`.
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/domain/CanvasKernelEnums.java`.
- Create entities/mappers for `CanvasShotUnit`, `GenerationRequestSnapshot`, `GenerationCandidate`, `ShotAdoption`, `CanvasMigrationReport`.
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasKernelService.java`.
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasPortRegistry.java`.
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasUpgradeService.java`.
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasKernelController.java`.
- Create backend schema, service and API tests under `aicp-backend/src/test/java/com/aicp/module/canvas/kernel/`.
- Create `aicp-frontend/src/views/canvas/ports/portRegistry.js`.
- Create `aicp-frontend/src/views/canvas/shot-units/shotUnitState.js`.
- Create `aicp-frontend/src/views/canvas/generation/candidateState.js`.
- Create `aicp-frontend/src/views/canvas/legacy-adapter/legacyCanvasAdapter.js`.
- Modify `aicp-frontend/src/api/canvas.js`, `useCanvasNodes.js`, `Canvas.vue`.
- Create `aicp-frontend/tests/canvas-kernel-r1.test.js`.

### Task 1: Add V12 schema with immutable and unique constraints

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V12__canvas_production_kernel.sql`
- Create: `aicp-backend/src/main/resources/db/migration/V12_undo.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/kernel/CanvasKernelSchemaTest.java`

- [ ] **Step 1: Write the failing schema test**

```java
@Test
void v12TablesAndConstraintsExist() {
    assertThat(columns("CANVAS_SHOT_UNITS")).contains("UUID", "PROJECT_ID", "MODE", "ROW_VERSION");
    assertThat(columns("GENERATION_REQUEST_SNAPSHOTS")).contains("UUID", "PAYLOAD_HASH", "ADAPTER_VERSION");
    assertThat(columns("GENERATION_CANDIDATES")).contains("UUID", "REQUEST_SNAPSHOT_ID", "ASSET_VERSION_ID");
    assertThat(columns("SHOT_ADOPTIONS")).contains("SHOT_UNIT_ID", "REVISION", "CANDIDATE_ID");
    assertThat(indexNames("SHOT_ADOPTIONS")).contains("UK_SHOT_ADOPTION_REVISION");
}
```

- [ ] **Step 2: Run and verify RED**

Run: `cd aicp-backend && mvn -Dtest=CanvasKernelSchemaTest test`

Expected: FAIL because V12 tables do not exist.

- [ ] **Step 3: Create the schema**

The migration must create:

```sql
CREATE TABLE canvas_shot_units (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
  project_id BIGINT NOT NULL, mode VARCHAR(16) NOT NULL,
  provisional_shot_id VARCHAR(64), source_shot_id BIGINT, source_shot_revision INT,
  duration_ms INT NOT NULL, fps INT NOT NULL, aspect_ratio VARCHAR(16) NOT NULL,
  row_version INT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL
);
CREATE TABLE generation_request_snapshots (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
  node_id BIGINT NOT NULL, shot_unit_id BIGINT NOT NULL, payload_json JSON NOT NULL,
  payload_hash VARCHAR(64) NOT NULL, resolved_model_id VARCHAR(128) NOT NULL,
  resolved_model_version VARCHAR(128), adapter_version VARCHAR(64) NOT NULL,
  estimated_credits INT NOT NULL, created_at DATETIME NOT NULL
);
```

Also create `generation_candidates`, `shot_adoptions`, `canvas_migration_reports`; add `shot_unit_id`, `node_schema_version` to `canvas_nodes` and contract/status columns to `canvas_edges`. Mirror types as `TEXT` in H2 where JSON is unsupported by the existing schema style.

- [ ] **Step 4: Run schema tests**

Run: `cd aicp-backend && mvn -Dtest='CanvasKernelSchemaTest,CanvasProjectSchemaTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/resources/db aicp-backend/src/test/java/com/aicp/module/canvas/kernel/CanvasKernelSchemaTest.java
git commit -m "feat: add canvas production kernel schema"
```

### Task 2: Implement ShotWorkUnit and project-mode invariants

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/domain/CanvasKernelEnums.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/entity/CanvasShotUnit.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/mapper/CanvasShotUnitMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasKernelService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/kernel/CanvasKernelServiceTest.java`

- [ ] **Step 1: Write failing invariant tests**

```java
@Test
void productionUnitRequiresSourceRevision() {
    assertThatThrownBy(() -> service.createUnit(project("PRODUCTION"), request(null, null)))
            .isInstanceOf(BizException.class).hasMessageContaining("正式生产镜头必须绑定分镜版本");
}

@Test
void explorationUnitGetsProvisionalShotId() {
    var unit = service.createUnit(project("EXPLORATION"), request(null, null));
    assertThat(unit.getMode()).isEqualTo("EXPLORATION");
    assertThat(unit.getProvisionalShotId()).startsWith("draft_shot_");
}
```

- [ ] **Step 2: Run and verify RED**

Run: `cd aicp-backend && mvn -Dtest=CanvasKernelServiceTest test`

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement explicit modes**

```java
public enum CanvasMode { EXPLORATION, PRODUCTION }
public enum MigrationStatus { NOT_AUDITED, AUTO_READY, NEEDS_CONFIRMATION, UPGRADED, FAILED }
```

`createUnit` must validate `durationMs > 0`, `fps` in `1..120`, and require both source fields in production mode. It must never infer the latest storyboard revision.

- [ ] **Step 4: Run and verify GREEN**

Run: `cd aicp-backend && mvn -Dtest=CanvasKernelServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/test/java/com/aicp/module/canvas/kernel/CanvasKernelServiceTest.java
git commit -m "feat: add canvas shot work units"
```

### Task 3: Add one authoritative typed-port registry

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasPortRegistry.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/kernel/CanvasPortRegistryTest.java`
- Create: `aicp-frontend/src/views/canvas/ports/portRegistry.js`
- Create: `aicp-frontend/tests/canvas-kernel-r1.test.js`

- [ ] **Step 1: Write failing compatibility tests in both runtimes**

```java
assertThat(registry.canConnect("image", "image_ref", "video", "image_ref")).isTrue();
assertThat(registry.canConnect("audio", "audio_ref", "image", "image_ref")).isFalse();
```

```js
assert.equal(canConnect({ nodeType: 'director', port: 'director_package' }, { nodeType: 'video', port: 'director_package' }), true)
assert.equal(canConnect({ nodeType: 'audio', port: 'audio_ref' }, { nodeType: 'image', port: 'image_ref' }), false)
```

- [ ] **Step 2: Run and verify RED**

Run: `cd aicp-backend && mvn -Dtest=CanvasPortRegistryTest test; cd ../aicp-frontend && node --test tests/canvas-kernel-r1.test.js`

Expected: both fail because registries are absent.

- [ ] **Step 3: Implement registry version `canvas-ports-v1`**

Define exactly the twelve payload types from the design. Backend `connectNodes` resolves source and target definitions, rejects mismatches with code `46031`, and persists `port_contract_version='canvas-ports-v1'`. Frontend mirrors the registry only for immediate drag feedback; backend remains authoritative.

```java
public record PortDefinition(String key, String payloadType, Direction direction) {}
public record ConnectionDecision(boolean allowed, String contractVersion, String reason) {}
public ConnectionDecision validate(CanvasNode source, String sourcePort, CanvasNode target, String targetPort);
```

- [ ] **Step 4: Run both tests**

Run: `cd aicp-backend && mvn -Dtest=CanvasPortRegistryTest test && cd ../aicp-frontend && node --test tests/canvas-kernel-r1.test.js`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/test/java/com/aicp/module/canvas/kernel aicp-frontend/src/views/canvas/ports aicp-frontend/tests/canvas-kernel-r1.test.js
git commit -m "feat: enforce typed canvas ports"
```

### Task 4: Add immutable request snapshots, candidates and adoption

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/generation/entity/GenerationRequestSnapshot.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/generation/entity/GenerationCandidate.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/generation/mapper/GenerationRequestSnapshotMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/generation/mapper/GenerationCandidateMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/entity/ShotAdoption.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/mapper/ShotAdoptionMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/service/ShotAdoptionService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/kernel/ShotAdoptionServiceTest.java`

- [ ] **Step 1: Write failing snapshot/adoption tests**

```java
@Test
void snapshotHashIsStableForCanonicalPayload() {
    assertThat(service.hash(Map.of("b", 2, "a", 1))).isEqualTo(service.hash(Map.of("a", 1, "b", 2)));
}

@Test
void adoptionRevisionIsUniqueAndAppendOnly() {
    var first = adoptionService.adopt(unitId, candidateId, 7L, "first");
    var second = adoptionService.adopt(unitId, otherCandidateId, 7L, "better motion");
    assertThat(first.revision()).isEqualTo(1);
    assertThat(second.revision()).isEqualTo(2);
}
```

- [ ] **Step 2: Run and verify RED**

Run: `cd aicp-backend && mvn -Dtest=ShotAdoptionServiceTest test`

Expected: FAIL because services and entities are absent.

- [ ] **Step 3: Implement canonical JSON hashing and append-only adoption**

Use one configured Jackson `ObjectMapper` with ordered map keys, SHA-256, and a transaction that locks the ShotWorkUnit before selecting `max(revision)`. Reject candidates from another unit or unsettled assets.

```java
public String hash(Map<String, Object> payload);
@Transactional
public ShotAdoptionView adopt(Long shotUnitId, Long candidateId, Long actorId, String reason);
```

- [ ] **Step 4: Run and verify GREEN**

Run: `cd aicp-backend && mvn -Dtest=ShotAdoptionServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/generation aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/test/java/com/aicp/module/canvas/kernel/ShotAdoptionServiceTest.java
git commit -m "feat: persist canvas candidates and adoptions"
```

### Task 5: Implement transactional single-canvas upgrade

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasUpgradeService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasKernelController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/kernel/CanvasUpgradeServiceTest.java`

- [ ] **Step 1: Write failing idempotency and ambiguity tests**

```java
@Test
void ambiguousReportCannotUpgrade() {
    when(audit.report("canvas_1")).thenReturn(reportWith("NEEDS_CONFIRMATION"));
    assertThatThrownBy(() -> service.upgrade("canvas_1", "key-1", 7L)).hasMessageContaining("待确认");
}

@Test
void sameIdempotencyKeyReturnsExistingUpgrade() {
    var a = service.upgrade("canvas_1", "key-2", 7L);
    var b = service.upgrade("canvas_1", "key-2", 7L);
    assertThat(b.reportId()).isEqualTo(a.reportId());
}
```

- [ ] **Step 2: Run and verify RED**

Run: `cd aicp-backend && mvn -Dtest=CanvasUpgradeServiceTest test`

Expected: FAIL because upgrade service is absent.

- [ ] **Step 3: Implement backup, transaction and one-way upgrade**

Within one transaction: lock project, store canonical backup JSON and checksum in `canvas_migration_reports`, create shot units, update node/edge contract fields, classify director nodes, then set project schema version to 2. Never update `GenerationVariant` or old compose tasks.

```java
@Transactional
public UpgradeResult upgrade(String projectUuid, String idempotencyKey, Long actorId) {
    MigrationReport report = auditService.report(projectUuid);
    report.requireNoAmbiguity();
    return executeOnce(projectUuid, idempotencyKey, actorId, report);
}
```

- [ ] **Step 4: Run upgrade and migration tests**

Run: `cd aicp-backend && mvn -Dtest='CanvasUpgradeServiceTest,CanvasProjectMigrationTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/test/java/com/aicp/module/canvas/kernel/CanvasUpgradeServiceTest.java
git commit -m "feat: upgrade canvases to production kernel"
```

### Task 6: Build the R1 frontend module boundaries

**Files:**
- Create: `aicp-frontend/src/views/canvas/shot-units/shotUnitState.js`
- Create: `aicp-frontend/src/views/canvas/generation/candidateState.js`
- Create: `aicp-frontend/src/views/canvas/legacy-adapter/legacyCanvasAdapter.js`
- Modify: `aicp-frontend/src/api/canvas.js`
- Modify: `aicp-frontend/src/views/canvas/composables/useCanvasNodes.js`
- Modify: `aicp-frontend/src/views/Canvas.vue`
- Modify: `aicp-frontend/tests/canvas-kernel-r1.test.js`

- [ ] **Step 1: Write failing pure-state tests**

```js
assert.deepEqual(gatesFor({ inputsReady: true, costConfirmed: false }), ['INPUT_READY'])
assert.equal(canFormallyAdopt({ mode: 'EXPLORATION' }), false)
assert.equal(canFormallyAdopt({ mode: 'PRODUCTION', sourceShotRevision: 4 }), true)
assert.equal(projectLegacyNode({ type: 'reference', data: { director: {} } }).type, 'director')
```

- [ ] **Step 2: Run and verify RED**

Run: `cd aicp-frontend && node --test tests/canvas-kernel-r1.test.js`

Expected: FAIL because state modules are absent.

- [ ] **Step 3: Implement modules and keep Canvas.vue orchestration-only**

Move gate derivation, candidate selection and legacy projection into the new pure modules. Add API methods for shot units, candidates, candidate selection, adoptions, migration report and upgrade. `Canvas.vue` consumes module outputs and must not calculate adoption or migration rules inline. New reads and writes activate only when `CANVAS_KERNEL_V2` is enabled; typed drag behavior additionally requires `TYPED_PORTS`.

```js
export const SHOT_GATES = ['INPUT_READY', 'COST_CONFIRMED', 'GENERATED', 'QUALITY_COMPLETE', 'ADOPTED']
export function canFormallyAdopt(unit) {
  return unit.mode === 'PRODUCTION' && Number.isInteger(unit.sourceShotRevision)
}
```

- [ ] **Step 4: Run full verification**

Run: `cd aicp-backend && mvn test && cd ../aicp-frontend && npm test && npm run build`

Expected: Maven BUILD SUCCESS, Node tests PASS, Vite exits 0.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/Canvas.vue aicp-frontend/src/views/canvas aicp-frontend/src/api/canvas.js aicp-frontend/tests/canvas-kernel-r1.test.js
git commit -m "feat: expose canvas production work units"
```
