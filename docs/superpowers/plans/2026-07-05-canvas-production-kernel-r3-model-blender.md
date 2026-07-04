# Canvas Production Kernel R3 Model and Blender Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在供应商 Gate 通过后交付模型无关能力请求、版本化适配预览、真实 Seedance 任务和隔离 Blender 预演 Worker，使普通与导演两条链路都产生真实可追溯候选。

**Architecture:** Canvas 编译创作意图，现有 AiRouter/new-api 选择并调用供应商，版本化 Adapter 负责协议翻译。Blender Worker 只消费不可变 DirectorRevision，通过任务队列运行；领域 Y-up 坐标在 Worker 边界转换为 Blender Z-up。费用和状态复用 generation/任务事件中心。

**Tech Stack:** Spring Boot, Java 17, MyBatis-Plus, existing AiRouter/NewApiClient, Go new-api, Blender headless Python, FFmpeg, pytest/unittest, Vue 3.

---

## File map

- Create `aicp-backend/src/main/resources/db/migration/V14__generation_adapters_and_attempts.sql` and mirrors.
- Create backend package `com.aicp.module.generation.capability` and `com.aicp.module.generation.adapter`.
- Modify `AiRouter.java`, `GenerationService.java`, `GenerationExecutor.java`, `GenerationSettlementService.java`, `GenerationController.java`.
- Create `aicp-backend/src/test/java/com/aicp/module/generation/capability/` and `adapter/` tests.
- Create `workers/blender/{scene_builder.py,coordinate.py,animation_baker.py,camera_builder.py,renderer.py,manifest.py,worker.py}` and unittest tests.
- Create backend `BlenderWorkerClient` and `DirectorPreviewService` for idempotent preview dispatch.
- Create `workers/blender/deploy/worker-config.yaml` with pinned image, resources, concurrency, timeout and egress policy.
- Add new-api provider contract in `new-api/controller/video_proxy.go` and focused Go tests.
- Create `aicp-frontend/src/views/canvas/generation/modelRequestState.js` and `ModelRequestPreviewDialog.vue`.
- Modify `aicp-frontend/src/api/generation.js` and node floating editor.
- Create `aicp-frontend/tests/model-adapter-r3.test.js`.

### Task 1: Record and enforce Seedance provider Gate

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/generation/adapter/ModelCapabilityProfile.java`
- Create: `aicp-backend/src/main/resources/model-capabilities/seedance-2.0.json`
- Create: `aicp-backend/src/test/java/com/aicp/module/generation/adapter/SeedanceProviderGateTest.java`
- Create: `new-api/controller/video_proxy_seedance_test.go`

- [ ] **Step 1: Write failing capability and provider contract tests**

```java
@Test
void seedanceProfileIsVersionedAndProductionVerified() {
    var profile = loader.load("seedance-2.0");
    assertThat(profile.adapterVersion()).isNotBlank();
    assertThat(profile.productionVerified()).isTrue();
    assertThat(profile.limits().maxImages()).isEqualTo(9);
}
```

```go
func TestSeedanceReferenceRequestPreservesIdempotencyAndCallback(t *testing.T) {
    // assert upstream request contains provider model, idempotency key and callback URL
}
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=SeedanceProviderGateTest test; cd ../new-api && go test ./controller -run TestSeedanceReferenceRequestPreservesIdempotencyAndCallback`

Expected: FAIL because the profile and provider contract do not exist.

- [ ] **Step 3: Add the verified profile only after real sandbox evidence**

The JSON must contain provider model ID, region, accepted upload modes, formats, size/duration/aspect limits, callback/poll/cancel behavior, billing semantics, rate limits and `production_verified`. Keep the flag false until a recorded sandbox response proves the contract; the test remains RED by design until G0 evidence is attached to the implementation PR.

```json
{
  "profile_id": "seedance-2.0",
  "adapter_version": "seedance-v1",
  "production_verified": false,
  "limits": { "max_images": 9, "max_videos": 3, "max_audio": 3, "max_duration_seconds": 15 }
}
```

- [ ] **Step 4: Run Gate tests with credentials in the integration environment**

Run: `cd new-api && SEEDANCE_INTEGRATION=1 go test ./controller -run TestSeedanceReferenceRequestPreservesIdempotencyAndCallback && cd ../aicp-backend && mvn -Dtest=SeedanceProviderGateTest test`

Expected: PASS only in the approved integration environment; local runs skip the live call but still validate profile schema.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/resources/model-capabilities aicp-backend/src/main/java/com/aicp/module/generation/adapter aicp-backend/src/test/java/com/aicp/module/generation/adapter new-api/controller/video_proxy_seedance_test.go
git commit -m "test: gate seedance production adapter"
```

### Task 2: Add V14 task attempts and adapter metadata

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V14__generation_adapters_and_attempts.sql`
- Create: `aicp-backend/src/main/resources/db/migration/V14_undo.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/resources/db/schema.sql`
- Create: `aicp-backend/src/test/java/com/aicp/module/generation/GenerationAttemptSchemaTest.java`

- [ ] **Step 1: Write failing schema test**

```java
assertThat(columns("GENERATION_TASK_ATTEMPTS")).contains("TASK_ID", "ATTEMPT_NO", "PROVIDER_REQUEST_ID", "STATUS");
assertThat(columns("GENERATION_TASKS")).contains("REQUEST_SNAPSHOT_ID", "ACTUAL_CREDIT_COST");
assertThat(indexNames("GENERATION_TASK_ATTEMPTS")).contains("UK_GENERATION_TASK_ATTEMPT");
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=GenerationAttemptSchemaTest test`

Expected: FAIL.

- [ ] **Step 3: Create V14 migration**

Add append-only attempts, adapter profile/version fields, provider request ID, timestamps, error and raw-response storage reference. Do not store signed URLs or full sensitive prompts in attempts.

```sql
CREATE TABLE generation_task_attempts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, task_id BIGINT NOT NULL,
  attempt_no INT NOT NULL, provider_request_id VARCHAR(200), status VARCHAR(32) NOT NULL,
  started_at DATETIME, completed_at DATETIME, error_code VARCHAR(64), response_storage_key VARCHAR(500),
  UNIQUE KEY uk_generation_task_attempt (task_id, attempt_no)
);
```

- [ ] **Step 4: Run GREEN**

Run: `cd aicp-backend && mvn -Dtest='GenerationAttemptSchemaTest,CanvasKernelSchemaTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/resources/db aicp-backend/src/test/java/com/aicp/module/generation/GenerationAttemptSchemaTest.java
git commit -m "feat: add generation task attempts"
```

### Task 3: Compile model-independent capability requests

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/generation/capability/CapabilityRequest.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/generation/capability/CapabilityCompiler.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/generation/capability/CapabilityCompilerTest.java`

- [ ] **Step 1: Write failing compiler tests**

```java
@Test
void compilerKeepsSemanticReferenceRoles() {
    var request = compiler.compile(videoNode(), inputs());
    assertThat(request.references()).extracting("role")
            .containsExactly("identity", "scene", "composition", "audio_timing");
    assertThat(request.modelId()).isNull();
}
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=CapabilityCompilerTest test`

Expected: FAIL.

- [ ] **Step 3: Implement compiler**

Define generation mode, duration, aspect, quality tier, cost preference and semantic references. The compiler may reference DirectorRevision but must not know Seedance slot numbers.

```java
public record SemanticReference(String role, Long assetId, Long assetVersionId, Integer startMs, Integer endMs) {}
public record CapabilityRequest(String intent, String mode, int durationMs, String aspectRatio,
                                String qualityTier, String costPreference,
                                List<SemanticReference> references, Long directorRevisionId) {}
```

- [ ] **Step 4: Run GREEN**

Run: `cd aicp-backend && mvn -Dtest=CapabilityCompilerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/generation/capability aicp-backend/src/test/java/com/aicp/module/generation/capability
git commit -m "feat: compile model independent requests"
```

### Task 4: Implement adapter preview and immutable submission

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/generation/adapter/ModelAdapter.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/generation/adapter/SeedanceAdapter.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/generation/service/ModelRequestService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/generation/controller/GenerationController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/generation/adapter/SeedanceAdapterTest.java`

- [ ] **Step 1: Write failing adapter tests**

```java
@Test
void adapterPrioritizesRolesAndDoesNotFillSlotsNeedlessly() {
    var preview = adapter.preview(requestWithDuplicateReferences(), profile());
    assertThat(preview.images()).hasSize(4);
    assertThat(preview.warnings()).contains("duplicate identity reference removed");
}

@Test
void changedModelAfterConfirmationRequiresNewPreview() {
    assertThatThrownBy(() -> service.submit(previewId, "other-model", key))
            .hasMessageContaining("重新确认");
}
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=SeedanceAdapterTest test`

Expected: FAIL.

- [ ] **Step 3: Implement preview and submission endpoints**

Preview returns model/version, adapter version, reference roles, removed inputs, prompt, warnings and estimated credits. Submit validates the preview fingerprint, writes immutable snapshot, freezes credits through the existing settlement boundary, then creates one GenerationTask.

```java
public interface ModelAdapter {
    AdapterPreview preview(CapabilityRequest request, ModelCapabilityProfile profile);
    ProviderRequest compile(AdapterPreview confirmedPreview);
}
```

- [ ] **Step 4: Run GREEN**

Run: `cd aicp-backend && mvn -Dtest='SeedanceAdapterTest,CapabilityCompilerTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/generation aicp-backend/src/test/java/com/aicp/module/generation
git commit -m "feat: preview and submit model requests"
```

### Task 5: Build deterministic Blender Worker boundary

**Files:**
- Create: `workers/blender/coordinate.py`
- Create: `workers/blender/scene_builder.py`
- Create: `workers/blender/animation_baker.py`
- Create: `workers/blender/camera_builder.py`
- Create: `workers/blender/renderer.py`
- Create: `workers/blender/manifest.py`
- Create: `workers/blender/worker.py`
- Create: `workers/blender/tests/test_coordinate.py`
- Create: `workers/blender/tests/test_manifest.py`

- [ ] **Step 1: Write failing coordinate and manifest tests**

```python
class WorkerBoundaryTest(unittest.TestCase):
    def test_y_up_to_blender_z_up_round_trip(self):
        point = (1.0, 2.0, 3.0)
        actual = blender_to_domain(domain_to_blender(point))
        for expected_value, actual_value in zip(point, actual):
            self.assertAlmostEqual(expected_value, actual_value)

    def test_manifest_is_stable_for_same_inputs(self):
        self.assertEqual(build_manifest(fixture()), build_manifest(fixture()))
```

- [ ] **Step 2: Run RED**

Run: `python3 -m unittest discover -s workers/blender/tests -v`

Expected: FAIL because worker modules are absent.

- [ ] **Step 3: Implement the non-Blender pure boundary first**

Implement explicit matrices, Quaternion conversion, frame range, asset checksum and authorization verification, and deterministic manifest serialization. Reject asset manifests without license/portrait authorization state. Then add Blender imports behind functions so pure tests run without Blender installed. Add `deploy/worker-config.yaml` with pinned Blender image, CPU/GPU/memory/disk limits, one-task concurrency, timeout, cleanup TTL and deny-by-default network egress.

```python
def build_preview(revision: dict, assets: list[dict], output_dir: str) -> dict:
    verify_assets(assets)
    scene = build_scene(revision, assets)
    return render_and_manifest(scene, output_dir)
```

- [ ] **Step 4: Run pure and headless smoke tests**

Run: `python3 -m unittest discover -s workers/blender/tests -v && blender --background --python workers/blender/tests/smoke_scene.py`

Expected: unittest PASS; Blender exits 0 and writes manifest, first frame, last frame and MP4 to a temporary directory. Run the six golden scenes through the same image and record peak memory, render duration and output checksum before G2 approval.

- [ ] **Step 5: Commit**

```bash
git add workers/blender
git commit -m "feat: add deterministic blender preview worker"
```

### Task 6: Dispatch idempotent Blender preview tasks

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/director/adapter/BlenderWorkerClient.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/director/service/DirectorPreviewService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/director/controller/DirectorRevisionController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/director/DirectorPreviewServiceTest.java`

- [ ] **Step 1: Write the failing dispatch tests**

```java
@Test
void previewDispatchPinsRevisionAssetsAndWorkerVersion() {
    var task = service.create(revisionId, "preview-key", 7L);
    assertThat(task.getType()).isEqualTo("director_preview");
    assertThat(task.getParameters()).contains("director_revision_id", "worker_image_version");
}

@Test
void repeatedIdempotencyKeyDoesNotDispatchTwice() {
    var first = service.create(revisionId, "preview-key", 7L);
    var second = service.create(revisionId, "preview-key", 7L);
    assertThat(second.getId()).isEqualTo(first.getId());
    verify(workerClient, times(1)).enqueue(any());
}
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=DirectorPreviewServiceTest test`

Expected: FAIL because the dispatcher is absent.

- [ ] **Step 3: Implement preview dispatch and callback validation**

`POST /api/v1/director-revisions/{revisionId}/preview-renders` creates one `director_preview` GenerationTask, pins revision/asset/template/worker versions, then enqueues a signed internal manifest. Callback must match task UUID and manifest hash before outputs are settled as asset versions.

```java
public GenerationTask create(Long revisionId, String idempotencyKey, Long actorId);
public void acceptCallback(String taskUuid, String manifestHash, BlenderResult result);
```

- [ ] **Step 4: Run GREEN**

Run: `cd aicp-backend && mvn -Dtest='DirectorPreviewServiceTest,DirectorSceneServiceTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/director aicp-backend/src/test/java/com/aicp/module/director/DirectorPreviewServiceTest.java
git commit -m "feat: dispatch blender preview tasks"
```

### Task 7: Integrate attempts, partial candidates and settlement

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/common/ai/AiRouter.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationExecutor.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationSettlementService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/generation/GenerationAttemptServiceTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/generation/service/GenerationSettlementServiceTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/generation/service/GenerationSettlementCompensatorTest.java`

- [ ] **Step 1: Write failing partial-result test**

```java
@Test
void successfulCandidatesSurvivePartialFailure() {
    var result = service.completeAttempt(task, providerResult(oneSuccess(), oneFailure()));
    assertThat(result.taskStatus()).isEqualTo("PARTIAL");
    assertThat(result.candidates()).hasSize(1);
    assertThat(result.refundCredits()).isPositive();
}

@Test
void settlementFailureLeavesTaskRecoverableAndWritesOutbox() {
    when(assetVersionMapper.insert(any())).thenThrow(new RuntimeException("storage metadata unavailable"));
    assertThatThrownBy(() -> settlementService.settle(task, input)).isInstanceOf(RuntimeException.class);
    verify(outboxMapper).insert(argThat(event -> "ASSET_CREATE".equals(event.getStage())));
}
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-backend && mvn -Dtest=GenerationAttemptServiceTest test`

Expected: FAIL.

- [ ] **Step 3: Implement attempt lifecycle and settlement**

Create a new attempt for every provider call; never overwrite prior errors. Set task success only after asset settlement. Persist each successful output as an asset version and candidate; calculate actual charge and failed-output release through settlement/outbox.

```java
public AttemptResult completeAttempt(GenerationTask task, ProviderResult result) {
    return result.hasFailures() ? settlePartial(task, result) : settleAll(task, result);
}
```

- [ ] **Step 4: Run backend generation tests**

Run: `cd aicp-backend && mvn -Dtest='GenerationAttemptServiceTest,GenerationSettlementServiceTest,GenerationSettlementCompensatorTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/common/ai/AiRouter.java aicp-backend/src/main/java/com/aicp/module/generation aicp-backend/src/test/java/com/aicp/module/generation
git commit -m "feat: settle generation attempts and candidates"
```

### Task 8: Add frontend preview and confirmation

**Files:**
- Create: `aicp-frontend/src/views/canvas/generation/modelRequestState.js`
- Create: `aicp-frontend/src/views/canvas/generation/ModelRequestPreviewDialog.vue`
- Modify: `aicp-frontend/src/api/generation.js`
- Modify: `aicp-frontend/src/views/canvas/components/NodeFloatingEditor.vue`
- Create: `aicp-frontend/tests/model-adapter-r3.test.js`

- [ ] **Step 1: Write failing state tests**

```js
assert.equal(canConfirm({ previewFingerprint: 'a', currentFingerprint: 'a', estimatedCredits: 50 }), true)
assert.equal(canConfirm({ previewFingerprint: 'a', currentFingerprint: 'b', estimatedCredits: 50 }), false)
assert.deepEqual(referenceSummary(preview).map(x => x.role), ['identity', 'scene', 'camera_motion'])
```

- [ ] **Step 2: Run RED**

Run: `cd aicp-frontend && node --test tests/model-adapter-r3.test.js`

Expected: FAIL.

- [ ] **Step 3: Implement dialog and replace hardcoded submission**

Behind `MODEL_ADAPTER_V2`, the generate action must always call preview, render recommendation, reference roles, removed inputs, warnings and credits, then submit with preview fingerprint and idempotency key. Remove default model overrides from execution handlers.

```js
export function canConfirm({ previewFingerprint, currentFingerprint, estimatedCredits }) {
  return previewFingerprint === currentFingerprint && Number.isFinite(estimatedCredits)
}
```

- [ ] **Step 4: Run R3 verification**

Run: `cd new-api && go test ./... && cd ../aicp-backend && mvn test && cd ../aicp-frontend && npm test && npm run build && cd .. && python3 -m unittest discover -s workers/blender/tests -v`

Expected: all commands exit 0.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas/generation aicp-frontend/src/views/canvas/components/NodeFloatingEditor.vue aicp-frontend/src/api/generation.js aicp-frontend/tests/model-adapter-r3.test.js
git commit -m "feat: confirm canvas model requests"
```
