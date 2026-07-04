# Canvas Production Kernel R4 Quality and Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为生成候选增加可定位的质量报告和分级采用策略，并将正式采用镜头固化为可审计素材清单、ZIP、EDL 和 FCPXML，完成外部后期交接。

**Architecture:** 质量报告是候选的附属事实，不能自动采用或创建导演台。ShotAdoption 继续是正式采用唯一事实源；DeliveryManifest 固化采用 revision 和资产版本。打包与交换文件是异步任务，不在 Canvas 中引入编辑时间线。

**Tech Stack:** Spring Boot, MyBatis-Plus, Jackson, existing generation/task/asset services, Vue 3, Node test runner, ZIP, XML, EDL text format, JUnit 5.

---

## File map

- Create `aicp-backend/src/main/resources/db/migration/V15__canvas_quality_delivery.sql` and mirrors.
- Create backend packages `com.aicp.module.quality.canvas` and `com.aicp.module.delivery`.
- Create tests under `aicp-backend/src/test/java/com/aicp/module/quality/canvas` and `delivery`.
- Create `aicp-frontend/src/views/canvas/quality/{qualityState.js,QualityReportPanel.vue}`.
- Create `aicp-frontend/src/views/canvas/delivery/{deliveryState.js,DeliveryManifestDrawer.vue}`.
- Modify Canvas API and workspace integration.
- Create `aicp-frontend/tests/quality-delivery-r4.test.js`.

### Task 1: Add V15 quality and delivery schema

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V15__canvas_quality_delivery.sql`
- Create: `aicp-backend/src/main/resources/db/migration/V15_undo.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/resources/db/schema.sql`
- Create: `aicp-backend/src/test/java/com/aicp/module/delivery/CanvasQualityDeliverySchemaTest.java`

- [ ] **Step 1: Write failing schema test**

```java
assertThat(columns("CANVAS_QUALITY_REPORTS")).contains("CANDIDATE_ID", "OVERALL_STATUS", "POLICY_VERSION");
assertThat(columns("CANVAS_QUALITY_ISSUES")).contains("START_MS", "END_MS", "SOURCE_NODE_ID", "SOURCE_TRACK_ID");
assertThat(columns("DELIVERY_MANIFESTS")).contains("PROJECT_ID", "REVISION", "STATUS", "MANIFEST_HASH");
assertThat(columns("DELIVERY_MANIFEST_ITEMS")).contains("SHOT_UNIT_ID", "ADOPTION_ID", "ASSET_VERSION_ID", "SORT_ORDER");
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=CanvasQualityDeliverySchemaTest test`

Expected: FAIL.

- [ ] **Step 3: Create V15 schema**

Add unique report per `(candidate_id, policy_version)`, issue range checks, unique manifest revision per project, immutable manifest items and package task references. Do not reuse the existing generic `quality_reports` table.

```sql
ALTER TABLE canvas_quality_reports ADD CONSTRAINT uk_canvas_quality_candidate_policy UNIQUE (candidate_id, policy_version);
ALTER TABLE delivery_manifests ADD CONSTRAINT uk_delivery_manifest_revision UNIQUE (project_id, revision);
```

- [ ] **Step 4: Run GREEN**

Run: `cd aicp-backend && mvn -Dtest='CanvasQualityDeliverySchemaTest,CanvasKernelSchemaTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/resources/db aicp-backend/src/test/java/com/aicp/module/delivery/CanvasQualityDeliverySchemaTest.java
git commit -m "feat: add canvas quality delivery schema"
```

### Task 2: Normalize quality reports and enforce policy

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/quality/canvas/CanvasQualityReport.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/quality/canvas/CanvasQualityIssue.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/quality/canvas/CanvasQualityService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/quality/canvas/CanvasQualityServiceTest.java`

- [ ] **Step 1: Write failing normalization tests**

```java
@Test
void issueRangeMustBeInsideCandidateDuration() {
    assertThatThrownBy(() -> service.record(candidate(6000), issue(5900, 6200)))
            .hasMessageContaining("时间区间");
}

@Test
void qualitySuggestionNeverCreatesDirectorAction() {
    var report = service.record(candidate(6000), issue(1000, 1500, "ACTION"));
    assertThat(report.issues()).allMatch(i -> !"CREATE_DIRECTOR".equals(i.suggestedAction()));
}
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=CanvasQualityServiceTest test`

Expected: FAIL.

- [ ] **Step 3: Implement dimensions and policy**

Support identity, composition, action, camera, physics, audio timing and continuity. Every issue includes severity, half-open time range, expected, observed, source node, optional source track and allowed suggested action. Policy returns `PASS`, `WARN` or `BLOCK` but never mutates candidates or director state.

```java
public enum QualityDimension { IDENTITY, COMPOSITION, ACTION, CAMERA, PHYSICS, AUDIO_TIMING, CONTINUITY }
public enum QualityStatus { PASS, WARN, BLOCK }
public record QualityIssue(QualityDimension dimension, String severity, int startMs, int endMs,
                           Long sourceNodeId, String sourceTrackId, String suggestedAction) {}
```

- [ ] **Step 4: Run GREEN**

Run: `cd aicp-backend && mvn -Dtest=CanvasQualityServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/quality/canvas aicp-backend/src/test/java/com/aicp/module/quality/canvas
git commit -m "feat: normalize canvas quality reports"
```

### Task 3: Gate formal adoption with audited override

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/service/ShotAdoptionService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/quality/canvas/QualityAdoptionPolicyTest.java`

- [ ] **Step 1: Write failing adoption policy tests**

```java
@Test
void blockingReportRequiresAuthorizedReason() {
    assertThatThrownBy(() -> adoption.adopt(unitId, candidateId, user, null))
            .hasMessageContaining("质检阻断");
}

@Test
void authorizedOverrideStoresReasonAndActor() {
    var result = adoption.adopt(unitId, candidateId, approver, "业务确认可接受");
    assertThat(result.overrideReason()).isEqualTo("业务确认可接受");
    assertThat(result.adoptedBy()).isEqualTo(approver.id());
}
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=QualityAdoptionPolicyTest test`

Expected: FAIL.

- [ ] **Step 3: Implement policy check**

Normal users may adopt PASS/WARN candidates. BLOCK requires `canvas:quality:override`, a nonblank reason and an audit log. Never rewrite or downgrade the quality report.

```java
if (report.blocksAdoption()) {
    permissions.require("canvas:quality:override");
    requireNonBlank(overrideReason, "质检阻断候选必须填写采用原因");
}
```

- [ ] **Step 4: Run GREEN**

Run: `cd aicp-backend && mvn -Dtest='QualityAdoptionPolicyTest,ShotAdoptionServiceTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas/service/ShotAdoptionService.java aicp-backend/src/test/java/com/aicp/module/quality/canvas/QualityAdoptionPolicyTest.java
git commit -m "feat: audit blocked candidate adoption"
```

### Task 4: Build immutable delivery manifests

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/delivery/entity/DeliveryManifest.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/delivery/entity/DeliveryManifestItem.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/delivery/mapper/DeliveryManifestMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/delivery/mapper/DeliveryManifestItemMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/delivery/service/DeliveryManifestService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/delivery/controller/DeliveryManifestController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/delivery/DeliveryManifestServiceTest.java`

- [ ] **Step 1: Write failing manifest tests**

```java
@Test
void manifestPinsAdoptionAndAssetVersions() {
    var manifest = service.create(projectId, idempotencyKey, userId);
    assertThat(manifest.items()).extracting("adoptionRevision").containsExactly(2, 1, 4);
    assertThat(manifest.manifestHash()).hasSize(64);
}

@Test
void explorationProjectCannotPublishManifest() {
    assertThatThrownBy(() -> service.create(explorationProjectId, key, userId))
            .hasMessageContaining("正式生产");
}
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=DeliveryManifestServiceTest test`

Expected: FAIL.

- [ ] **Step 3: Implement transactional manifest creation**

Lock the project, require PRODUCTION mode and one current adoption per ordered shot unit, copy adoption/asset versions into immutable items, canonicalize JSON and compute SHA-256. A repeated idempotency key returns the existing revision.

```java
@Transactional
public DeliveryManifestView create(Long projectId, String idempotencyKey, Long actorId);
```

- [ ] **Step 4: Run GREEN**

Run: `cd aicp-backend && mvn -Dtest=DeliveryManifestServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/delivery aicp-backend/src/test/java/com/aicp/module/delivery
git commit -m "feat: create immutable delivery manifests"
```

### Task 5: Generate ZIP, EDL and FCPXML packages

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/delivery/DeliveryPackageService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/delivery/EdlWriter.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/delivery/FcpxmlWriter.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/delivery/DeliveryExchangeWriterTest.java`

- [ ] **Step 1: Write failing golden-file tests**

```java
@Test
void edlUsesAdoptedShotOrderAndExactTimebase() {
    assertThat(edlWriter.write(fixture24fps())).isEqualTo(resource("/golden/delivery-24fps.edl"));
}

@Test
void fcpxmlReferencesPackagedRelativePaths() {
    var xml = fcpxmlWriter.write(fixture24fps());
    assertThat(xml).contains("media/SHOT_001.mp4").doesNotContain("https://");
}
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=DeliveryExchangeWriterTest test`

Expected: FAIL.

- [ ] **Step 3: Implement package task**

Build a ZIP containing version-pinned media, `manifest.json`, checksums, EDL, FCPXML and path mapping. Reject mixed unsupported frame rates instead of silently converting. Use storage keys internally and emit relative package paths; never include signed URLs.

```java
public interface ExchangeWriter {
    String fileName();
    byte[] write(DeliveryManifestView manifest);
}
```

- [ ] **Step 4: Run GREEN and validate XML**

Run: `cd aicp-backend && mvn -Dtest='DeliveryExchangeWriterTest,DeliveryManifestServiceTest' test`

Expected: PASS; XML parses with the JDK XML parser and golden files match.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/delivery aicp-backend/src/test/java/com/aicp/module/delivery
git commit -m "feat: package canvas delivery exchanges"
```

### Task 6: Add quality and delivery UI

**Files:**
- Create: `aicp-frontend/src/views/canvas/quality/qualityState.js`
- Create: `aicp-frontend/src/views/canvas/quality/QualityReportPanel.vue`
- Create: `aicp-frontend/src/views/canvas/delivery/deliveryState.js`
- Create: `aicp-frontend/src/views/canvas/delivery/DeliveryManifestDrawer.vue`
- Modify: `aicp-frontend/src/api/canvas.js`
- Modify: `aicp-frontend/src/views/Canvas.vue`
- Create: `aicp-frontend/tests/quality-delivery-r4.test.js`

- [ ] **Step 1: Write failing UI-state tests**

```js
assert.deepEqual(issueTarget({ source_node_id: 'n1', source_track_id: null, start_ms: 1200 }), { route: 'canvas', nodeId: 'n1', timeMs: 1200 })
assert.deepEqual(issueTarget({ source_node_id: 'n2', source_track_id: 'camera', start_ms: 900 }), { route: 'director', nodeId: 'n2', trackId: 'camera', timeMs: 900 })
assert.equal(canCreateManifest({ mode: 'PRODUCTION', units: [{ adopted: true }] }), true)
assert.equal(canCreateManifest({ mode: 'EXPLORATION', units: [{ adopted: true }] }), false)
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-frontend && node --test tests/quality-delivery-r4.test.js`

Expected: FAIL.

- [ ] **Step 3: Implement panels**

Behind `QUALITY_DELIVERY_V2`, quality issues jump to source node or existing director track; no button or message suggests creating a director. Delivery drawer shows pinned adoption/asset versions, validation errors and asynchronous package status. The only export actions are manifest, ZIP, EDL and FCPXML.

```js
export function canCreateManifest({ mode, units }) {
  return mode === 'PRODUCTION' && units.length > 0 && units.every(unit => unit.adopted)
}
```

- [ ] **Step 4: Run full R4 verification**

Run: `cd aicp-backend && mvn test && cd ../aicp-frontend && npm test && npm run build`

Expected: Maven BUILD SUCCESS, all Node tests PASS, Vite exits 0.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/Canvas.vue aicp-frontend/src/views/canvas/quality aicp-frontend/src/views/canvas/delivery aicp-frontend/src/api/canvas.js aicp-frontend/tests/quality-delivery-r4.test.js
git commit -m "feat: expose canvas quality and delivery"
```

### Task 7: Add metrics, failure injection and release cleanup gates

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/delivery/CanvasProductionE2ETest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/delivery/DeliveryManifestService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/quality/canvas/CanvasQualityService.java`
- Modify: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Write the end-to-end acceptance test**

```java
@Test
void productionShotReachesExternalDeliveryWithoutComposeTask() {
    var candidate = fixture.generateAndSettle();
    fixture.recordPassingQuality(candidate);
    fixture.adopt(candidate);
    var manifest = fixture.createManifest();
    var pkg = fixture.packageManifest(manifest);
    assertThat(pkg.files()).contains("manifest.json", "timeline.edl", "timeline.fcpxml");
    assertThat(fixture.createdTaskTypes()).doesNotContain("compose");
}
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=CanvasProductionE2ETest test`

Expected: FAIL until all R4 wiring is complete.

- [ ] **Step 3: Wire metrics and failure injection**

Record time-to-submit, time-to-correction, generation count, actual credits, quality rework success and adoption. Add test fixtures for asset loss, provider partial failure, Blender timeout and settlement failure; ensure no path creates compose tasks.

```java
metrics.record(new CanvasProductionMetric(projectId, shotUnitId, "ADOPTION_CREATED",
        Map.of("generation_count", generationCount, "actual_credits", actualCredits)));
```

- [ ] **Step 4: Run release suite**

Run: `cd new-api && go test ./... && cd ../aicp-backend && mvn test && cd ../aicp-frontend && npm test && npm run build && cd .. && python3 -m unittest discover -s workers/blender/tests -v`

Expected: all commands exit 0; no forbidden compose API or UI references remain.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/test/java/com/aicp/module/delivery/CanvasProductionE2ETest.java aicp-backend/src/main/java/com/aicp/module/delivery aicp-backend/src/main/java/com/aicp/module/quality/canvas aicp-frontend/tests/navigation-contract.test.js
git commit -m "test: verify canvas production delivery"
```
