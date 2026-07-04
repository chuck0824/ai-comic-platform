# Canvas Production Kernel R2 Director Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 DOM/CSS 导演台替换为基于 Three.js 的真实单镜头 3D 工作区，并提供可变草稿、不可变 revision、时间线、预设和冻结前检查。

**Architecture:** 导演台使用独立路由和独立状态模块；后端 `director` 域拥有草稿和冻结 revision。领域协议固定为 RH/Y-up/米制和 Quaternion，Three.js 直接消费；Blender 坐标转换留给 R3 Worker。所有预演和生成状态保持在 Task 域。

**Tech Stack:** Vue 3, Three.js, Element Plus, Node test runner, Spring Boot, MyBatis-Plus, Jackson, JUnit 5, H2/MySQL.

---

## File map

- Modify `aicp-frontend/package.json` and lockfile: add `three`.
- Create `aicp-backend/src/main/resources/db/migration/V13__director_scene_revisions.sql` and undo/schema mirrors.
- Create backend package `aicp-backend/src/main/java/com/aicp/module/director/{domain,entity,mapper,dto,service,controller}`.
- Create backend tests under `aicp-backend/src/test/java/com/aicp/module/director/`.
- Create `aicp-frontend/src/views/canvas/director/DirectorWorkspace.vue`.
- Create `director/state/directorDocument.js`, `viewport/DirectorViewport.vue`, `viewport/threeSceneController.js`, `timeline/DirectorTimeline.vue`, `timeline/timelineMath.js`, `presets/directorPresets.js`, `validation/directorValidation.js`.
- Modify `aicp-frontend/src/router/index.js`, `aicp-frontend/src/api/canvas.js`, and `Canvas.vue`.
- Create `aicp-frontend/tests/director-r2.test.js`.

### Task 1: Add V13 draft/revision schema

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V13__director_scene_revisions.sql`
- Create: `aicp-backend/src/main/resources/db/migration/V13_undo.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Create: `aicp-backend/src/test/java/com/aicp/module/director/DirectorSchemaTest.java`

- [ ] **Step 1: Write failing schema assertions**

```java
assertThat(columns("DIRECTOR_SCENES")).contains("SHOT_UNIT_ID", "CURRENT_DRAFT_ID");
assertThat(columns("DIRECTOR_DRAFTS")).contains("DOCUMENT_JSON", "ROW_VERSION");
assertThat(columns("DIRECTOR_REVISIONS")).contains("REVISION", "DOCUMENT_HASH", "DOCUMENT_JSON");
assertThat(columns("DIRECTOR_REVISION_ASSETS")).contains("REVISION_ID", "ASSET_ID", "ASSET_VERSION_ID");
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=DirectorSchemaTest test`

Expected: FAIL because V13 tables are absent.

- [ ] **Step 3: Create tables and constraints**

Enforce one scene per ShotWorkUnit, one `(scene_id, revision)`, immutable revision rows, and foreign keys to shot unit and assets. Drafts carry `row_version`; revisions do not carry task status.

```sql
ALTER TABLE director_scenes ADD CONSTRAINT uk_director_scene_unit UNIQUE (shot_unit_id);
ALTER TABLE director_revisions ADD CONSTRAINT uk_director_scene_revision UNIQUE (scene_id, revision);
```

- [ ] **Step 4: Run GREEN**

Run: `cd aicp-backend && mvn -Dtest='DirectorSchemaTest,CanvasKernelSchemaTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/resources/db aicp-backend/src/test/java/com/aicp/module/director/DirectorSchemaTest.java
git commit -m "feat: add director revision schema"
```

### Task 2: Define the canonical DirectorDocument protocol

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/director/domain/DirectorDocument.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/director/service/DirectorDocumentValidator.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/director/DirectorDocumentValidatorTest.java`
- Create: `aicp-frontend/src/views/canvas/director/state/directorDocument.js`
- Create: `aicp-frontend/tests/director-r2.test.js`

- [ ] **Step 1: Write failing protocol tests**

```java
assertThat(validDocument().coordinateSystem()).isEqualTo("RH_Y_UP_METERS");
assertThatThrownBy(() -> validator.validate(withNonUnitQuaternion()))
        .hasMessageContaining("Quaternion");
assertThatThrownBy(() -> validator.validate(withKeyframeAt(durationMs)))
        .hasMessageContaining("[0, duration_ms)");
```

```js
assert.equal(frameCount(6000, 24), 144)
assert.deepEqual(validFrameRange(6000, 24), { first: 0, last: 143 })
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=DirectorDocumentValidatorTest test; cd ../aicp-frontend && node --test tests/director-r2.test.js`

Expected: both fail because protocol modules do not exist.

- [ ] **Step 3: Implement protocol v1**

Persist positions as three-number metre vectors and rotations as `{x,y,z,w}` normalized quaternions. Every keyframe stores `time_ms`; frame index is derived. Reject unknown asset versions, negative times, keyframes at `duration_ms`, and action clips with `out_ms <= in_ms`.

```java
public record Quaternion(double x, double y, double z, double w) {}
public record TimedTransform(int timeMs, Vector3 position, Quaternion rotation, Vector3 scale) {}
public record DirectorDocument(String coordinateSystem, int durationMs, int fps,
                               List<SceneObject> objects, Timeline timeline) {}
```

- [ ] **Step 4: Run GREEN**

Run both focused commands from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/director aicp-backend/src/test/java/com/aicp/module/director aicp-frontend/src/views/canvas/director/state aicp-frontend/tests/director-r2.test.js
git commit -m "feat: define director document protocol"
```

### Task 3: Implement draft locking, validation and freeze APIs

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/director/entity/DirectorScene.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/director/entity/DirectorDraft.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/director/entity/DirectorRevision.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/director/entity/DirectorRevisionAsset.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/director/mapper/DirectorSceneMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/director/mapper/DirectorDraftMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/director/mapper/DirectorRevisionMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/director/mapper/DirectorRevisionAssetMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/director/service/DirectorSceneService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/director/controller/DirectorRevisionController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/director/DirectorSceneServiceTest.java`

- [ ] **Step 1: Write failing service tests**

```java
@Test
void staleIfMatchIsRejected() {
    assertThatThrownBy(() -> service.saveDraft(sceneId, 3, payload, 7L))
            .isInstanceOf(OptimisticLockingFailureException.class);
}

@Test
void freezeCreatesStableImmutableRevision() {
    var r1 = service.freeze(sceneId, 7L);
    var retry = service.freeze(sceneId, 7L, r1.idempotencyKey());
    assertThat(retry.id()).isEqualTo(r1.id());
    assertThat(r1.documentHash()).hasSize(64);
}
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=DirectorSceneServiceTest test`

Expected: FAIL because services are absent.

- [ ] **Step 3: Implement endpoints**

Implement GET scene, PUT draft with `If-Match`, POST validate, POST revisions with `Idempotency-Key`, and GET revision. Freeze canonicalizes JSON, validates asset versions, writes revision plus asset refs in one transaction, then returns `ETag` for the new draft state.

```java
public DirectorDraftView saveDraft(Long sceneId, int expectedVersion, DirectorDocument document, Long actorId);
public ValidationResult validate(Long sceneId);
public DirectorRevisionView freeze(Long sceneId, String idempotencyKey, Long actorId);
```

- [ ] **Step 4: Run GREEN**

Run: `cd aicp-backend && mvn -Dtest='DirectorSceneServiceTest,DirectorDocumentValidatorTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/director aicp-backend/src/test/java/com/aicp/module/director
git commit -m "feat: version director scene drafts"
```

### Task 4: Add independent director route and autosave state

**Files:**
- Create: `aicp-frontend/src/views/canvas/director/DirectorWorkspace.vue`
- Create: `aicp-frontend/src/views/canvas/director/state/useDirectorDocument.js`
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/src/api/canvas.js`
- Modify: `aicp-frontend/src/views/Canvas.vue`
- Modify: `aicp-frontend/tests/director-r2.test.js`

- [ ] **Step 1: Add failing route/autosave tests**

```js
assert.equal(directorRoute('canvas_1', 'unit_2'), '/canvas/canvas_1/shot-units/unit_2/director')
assert.equal(shouldAutosave({ dirty: true, validating: false, frozen: false }), true)
assert.equal(shouldAutosave({ dirty: true, validating: false, frozen: true }), false)
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-frontend && node --test tests/director-r2.test.js`

Expected: FAIL because route/state helpers are absent.

- [ ] **Step 3: Implement route and debounced save**

Add route `/canvas/:projectId/shot-units/:unitId/director` behind `DIRECTOR_V2`. Save 1 second after the last edit using the current ETag; on 409 keep local JSON and show choices to load remote, retain local copy, or create a new draft from local data.

```js
export const directorRoute = (projectId, unitId) => `/canvas/${projectId}/shot-units/${unitId}/director`
export const shouldAutosave = state => state.dirty && !state.validating && !state.frozen
```

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/director-r2.test.js && npm run build`

Expected: PASS and build exits 0.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/router/index.js aicp-frontend/src/api/canvas.js aicp-frontend/src/views/Canvas.vue aicp-frontend/src/views/canvas/director aicp-frontend/tests/director-r2.test.js
git commit -m "feat: add director workspace route"
```

### Task 5: Replace the DOM stage with Three.js

**Files:**
- Modify: `aicp-frontend/package.json`
- Modify: `aicp-frontend/package-lock.json`
- Create: `aicp-frontend/src/views/canvas/director/viewport/threeSceneController.js`
- Create: `aicp-frontend/src/views/canvas/director/viewport/DirectorViewport.vue`
- Modify: `aicp-frontend/tests/director-r2.test.js`

- [ ] **Step 1: Add failing lifecycle tests for the pure controller wrapper**

```js
test('scene controller disposes controls, geometries and renderer', () => {
  const doubles = fakeThreeResources()
  createSceneController(doubles).dispose()
  assert.equal(doubles.renderer.dispose.mock.calls.length, 1)
  assert.equal(doubles.controls.dispose.mock.calls.length, 1)
})
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-frontend && node --test tests/director-r2.test.js`

Expected: FAIL because Three.js/controller are absent.

- [ ] **Step 3: Add Three.js and implement the viewport**

Run: `cd aicp-frontend && npm install three@0.166.1`

Use `WebGLRenderer`, `OrbitControls`, `TransformControls`, `GLTFLoader`, `AnimationMixer`, helpers and one render loop. Dispose all GPU resources on route leave. Enforce object and triangle budgets before adding GLB content.

```js
export function createSceneController({ canvas, document, onChange }) {
  return { loadAsset, selectObject, setTransformMode, renderFrame, dispose }
}
```

- [ ] **Step 4: Run tests and production build**

Run: `cd aicp-frontend && npm test && npm run build`

Expected: PASS; Vite creates a separate director/three chunk and exits 0.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/package.json aicp-frontend/package-lock.json aicp-frontend/src/views/canvas/director/viewport aicp-frontend/tests/director-r2.test.js
git commit -m "feat: render director scenes with threejs"
```

### Task 6: Add timeline, presets and freeze checks

**Files:**
- Create: `aicp-frontend/src/views/canvas/director/timeline/timelineMath.js`
- Create: `aicp-frontend/src/views/canvas/director/timeline/DirectorTimeline.vue`
- Create: `aicp-frontend/src/views/canvas/director/presets/directorPresets.js`
- Create: `aicp-frontend/src/views/canvas/director/validation/directorValidation.js`
- Modify: `aicp-frontend/src/views/canvas/director/DirectorWorkspace.vue`
- Modify: `aicp-frontend/tests/director-r2.test.js`

- [ ] **Step 1: Write failing timeline and validation tests**

```js
assert.equal(interpolateScalar([{ timeMs: 0, value: 0 }, { timeMs: 1000, value: 10 }], 500, 'LINEAR'), 5)
assert.deepEqual(validateDirectorDocument(overlappingActions()).map(x => x.code), ['ACTION_OVERLAP'])
assert.ok(DIRECTOR_PRESETS.camera.some(x => x.id === 'medium_push_in'))
assert.ok(DIRECTOR_PRESETS.lighting.some(x => x.id === 'soft_key'))
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-frontend && node --test tests/director-r2.test.js`

Expected: FAIL because timeline/preset modules are absent.

- [ ] **Step 3: Implement six tracks and controlled presets**

Support transform, action, camera, lens, beat and continuity tracks. Presets may set business parameters only; do not expose skeleton joints or material node graphs. Freeze button calls backend validate, blocks on errors, allows warnings with acknowledgement, then freezes the revision.

```js
export const DIRECTOR_PRESETS = {
  camera: [{ id: 'medium_push_in', focalLengthMm: 50, motion: 'DOLLY_IN' }],
  action: [{ id: 'walk', clipKey: 'humanoid.walk.v1' }],
  lighting: [{ id: 'soft_key', intensity: 800, colorTemperatureK: 5200 }],
  material: [{ id: 'matte', roughness: 0.8, metallic: 0 }]
}
```

- [ ] **Step 4: Run full R2 verification**

Run: `cd aicp-backend && mvn -Dtest='Director*Test' test && cd ../aicp-frontend && npm test && npm run build`

Expected: backend director tests PASS, frontend tests PASS, build exits 0.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas/director aicp-frontend/tests/director-r2.test.js
git commit -m "feat: add director timeline and checks"
```
