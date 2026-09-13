# Canvas Production Kernel R0 Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 先恢复画布项目中心和编辑器的可信可用性，统一导演节点类型，移除与产品边界冲突或尚未接入的入口，并建立后续迁移所需的开关与契约保护。

**Architecture:** R0 不引入新的生产事实表。后端将上游不可用明确投影为可区分错误，前端使用有限骨架屏和只读降级；旧 `reference` 节点只做盘点和分类，不自动改写。所有行为通过配置开关发布，为 R1 的单画布升级保留安全边界。

**Tech Stack:** Vue 3, Element Plus, Node test runner, Spring Boot 3, Java 17, JUnit 5, Mockito, MyBatis-Plus, H2/MySQL.

---

## File map

- Create `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasLegacyAuditService.java`: classify legacy node and connection shapes without mutation.
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/dto/CanvasMigrationViews.java`: migration audit view (read-only, renamed from MigrationReport to MigrationAuditView to avoid conflict with R1 persistent entity).
- Modify `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasController.java`: expose read-only migration report.
- Modify `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`: add explicit upstream-unavailable code.
- Modify `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceAccessService.java`: return fail-closed, distinguishable upstream failure.
- Create `aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasLegacyAuditServiceTest.java`.
- Modify `aicp-backend/src/test/java/com/aicp/common/workspace/WorkspaceAccessServiceTest.java`.
- Create `aicp-frontend/src/config/canvasFeatures.js`: R0 feature flags.
- Create `aicp-frontend/src/views/canvas-project/canvasCenterState.js`: deterministic loading/error/degraded states.
- Modify `aicp-frontend/src/views/canvas-project/CanvasProjectCenter.vue`.
- Modify `aicp-frontend/src/views/canvas/composables/useCanvasNodes.js`: remove conflicting tools and local fake-success fallback.
- Modify `aicp-frontend/src/views/Canvas.vue`: hide fake actions and remove compose timeline entry.
- Delete `aicp-frontend/src/views/canvas/components/VideoComposeTimeline.vue` after all imports are removed.
- Modify `aicp-frontend/src/api/canvas.js`: remove compose/clip/splice client calls and add migration report.
- Create `aicp-frontend/tests/canvas-r0-contract.test.js`.

### Task 1: Make account-center failure distinguishable and fail-closed

**Files:**
- Modify: `aicp-backend/src/test/java/com/aicp/common/workspace/WorkspaceAccessServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceAccessService.java`

- [ ] **Step 1: Write the failing upstream-unavailable test**

```java
@Test
void upstreamFailureUsesStableErrorCode() throws Exception {
    when(client.membership("personal_7", "Bearer token"))
            .thenThrow(new AccountCenterPermissionClient.UpstreamUnavailableException("down", null));

    assertThatThrownBy(() -> service.resolve("personal_7", "Bearer token", 7L))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo(41008);
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd aicp-backend && mvn -Dtest=WorkspaceAccessServiceTest test`

Expected: FAIL because upstream failure still maps to the generic internal error code.

- [ ] **Step 3: Add and use the explicit error code**

```java
WORKSPACE_UPSTREAM_UNAVAILABLE(41008, "账户中心暂不可用，请稍后重试")
```

Replace the catch branch with:

```java
throw new BizException(ErrorCode.WORKSPACE_UPSTREAM_UNAVAILABLE);
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `cd aicp-backend && mvn -Dtest=WorkspaceAccessServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceAccessService.java aicp-backend/src/test/java/com/aicp/common/workspace/WorkspaceAccessServiceTest.java
git commit -m "fix: expose workspace dependency failure"
```

### Task 2: Define finite project-center states

**Files:**
- Create: `aicp-frontend/src/views/canvas-project/canvasCenterState.js`
- Create: `aicp-frontend/tests/canvas-r0-contract.test.js`

- [ ] **Step 1: Write the failing state reducer tests**

```js
import { resolveCanvasCenterState } from '../src/views/canvas-project/canvasCenterState.js'

test('workspace outage resolves to degraded instead of loading', () => {
  assert.deepEqual(resolveCanvasCenterState({ loading: false, code: 41008, cachedItems: [{ uuid: 'c1' }] }), {
    kind: 'degraded', readOnly: true, items: [{ uuid: 'c1' }]
  })
})

test('empty success is distinct from failure', () => {
  assert.equal(resolveCanvasCenterState({ loading: false, items: [] }).kind, 'empty')
  assert.equal(resolveCanvasCenterState({ loading: false, code: 500 }).kind, 'error')
})
```

- [ ] **Step 2: Run and verify RED**

Run: `cd aicp-frontend && node --test tests/canvas-r0-contract.test.js`

Expected: FAIL because the state helper does not exist.

- [ ] **Step 3: Implement the pure reducer**

```js
export function resolveCanvasCenterState({ loading, items = [], code, cachedItems = [] }) {
  if (loading) return { kind: 'loading', readOnly: false, items: [] }
  if (code === 41008) return { kind: 'degraded', readOnly: true, items: cachedItems }
  if (code) return { kind: 'error', readOnly: true, items: [] }
  if (!items.length) return { kind: 'empty', readOnly: false, items: [] }
  return { kind: 'ready', readOnly: false, items }
}
```

- [ ] **Step 4: Run and verify GREEN**

Run: `cd aicp-frontend && node --test tests/canvas-r0-contract.test.js`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas-project/canvasCenterState.js aicp-frontend/tests/canvas-r0-contract.test.js
git commit -m "test: define canvas center terminal states"
```

### Task 3: Render timeout, error and read-only degradation

**Files:**
- Modify: `aicp-frontend/src/views/canvas-project/CanvasProjectCenter.vue`
- Modify: `aicp-frontend/tests/canvas-r0-contract.test.js`

- [ ] **Step 1: Add failing source-contract assertions**

```js
test('canvas center exposes degraded and retry states', () => {
  const source = fs.readFileSync(centerPath, 'utf8')
  assert.match(source, /centerState\.kind === 'degraded'/)
  assert.match(source, /@click="search"/)
  assert.match(source, /:disabled="centerState\.readOnly"/)
})
```

- [ ] **Step 2: Run and verify RED**

Run: `cd aicp-frontend && node --test tests/canvas-r0-contract.test.js`

Expected: FAIL because the component has only loading/empty/error branches.

- [ ] **Step 3: Replace indefinite skeleton behavior**

Use an 8-second request guard and render:

```vue
<el-alert v-if="centerState.kind === 'degraded'" type="warning" :closable="false"
  title="账户中心暂不可用，当前仅可查看最近项目" />
<el-result v-else-if="centerState.kind === 'error'" icon="error" title="画布项目加载失败">
  <template #extra><el-button @click="search">重试</el-button></template>
</el-result>
<el-button type="primary" :disabled="centerState.readOnly" @click="showCreateDialog = true">新建画布</el-button>
```

Persist only successful summaries to `localStorage['canvas_recent_projects']`; never cache authorization failures.

- [ ] **Step 4: Run frontend contracts and build**

Run: `cd aicp-frontend && node --test tests/canvas-r0-contract.test.js && npm run build`

Expected: tests PASS and Vite exits 0.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas-project/CanvasProjectCenter.vue aicp-frontend/tests/canvas-r0-contract.test.js
git commit -m "fix: terminate canvas center loading states"
```

### Task 4: Add read-only legacy audit and director classification

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasLegacyAuditService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/dto/CanvasMigrationViews.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasLegacyAuditServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasController.java`

- [ ] **Step 1: Write failing classification tests**

```java
@Test
void classifiesDirectorReferenceWithoutMutatingNode() {
    CanvasNode node = node("reference", "{\"director\":{\"camera\":{}}}");
    var issue = service.classifyNode(node);
    assertThat(issue.suggestedType()).isEqualTo("director");
    assertThat(issue.status()).isEqualTo("AUTO_CLASSIFIED");
    assertThat(node.getType()).isEqualTo("reference");
}

@Test
void ambiguousReferenceRequiresConfirmation() {
    var issue = service.classifyNode(node("reference", "{}"));
    assertThat(issue.status()).isEqualTo("NEEDS_CONFIRMATION");
}

@Test
void legacyEdgesAreNotModifiedDuringAudit() {
    // legacy edges stay untouched during read-only audit;
    // R1 upgrade will set port_contract_version='legacy', status='NEEDS_CONFIRMATION'
    var report = service.report("canvas_1");
    assertThat(report.edges().stream().allMatch(e -> e.status().equals("LEGACY_UNMODIFIED"))).isTrue();
}
```

- [ ] **Step 2: Run and verify RED**

Run: `cd aicp-backend && mvn -Dtest=CanvasLegacyAuditServiceTest test`

Expected: FAIL because audit types do not exist.

- [ ] **Step 3: Implement classification and report endpoint**

Define:

```java
public record MigrationAuditIssue(String objectId, String objectType, String currentType,
                             String suggestedType, String status, String reason) {}
public record MigrationAuditReport(String projectUuid, int nodeCount, int edgeCount,
                              List<MigrationAuditIssue> issues) {}
```

Expose `GET /api/v1/canvas/projects/{id}/migration-report`. The service may read nodes and edges but must not call any mapper update method.

Note: R1's persistent entity is `CanvasMigrationRecord`, distinct from these read-only audit views. Legacy edges are NOT modified during audit; R1 upgrade sets `port_contract_version = 'legacy'` and `status = 'NEEDS_CONFIRMATION'` in bulk.

- [ ] **Step 4: Run backend canvas tests**

Run: `cd aicp-backend && mvn -Dtest='CanvasLegacyAuditServiceTest,CanvasProjectManagementServiceTest' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasLegacyAuditServiceTest.java
git commit -m "feat: audit legacy canvas shapes"
```

### Task 5: Remove fake and conflicting Canvas actions

**Files:**
- Modify: `aicp-frontend/src/views/canvas/composables/useCanvasNodes.js`
- Modify: `aicp-frontend/src/views/Canvas.vue`
- Modify: `aicp-frontend/src/api/canvas.js`
- Delete: `aicp-frontend/src/views/canvas/components/VideoComposeTimeline.vue`
- Modify: `aicp-frontend/tests/canvas-r0-contract.test.js`

- [ ] **Step 1: Add failing forbidden-entry tests**

```js
test('canvas no longer exposes post-production tools', () => {
  const sources = [canvasSource, nodesSource, apiSource].join('\n')
  for (const forbidden of ['VideoComposeTimeline', '视频剪辑', '视频合成', '音频截取', '音频变速']) {
    assert.doesNotMatch(sources, new RegExp(forbidden))
  }
  assert.doesNotMatch(canvasSource, /待接入/)
})
```

- [ ] **Step 2: Run and verify RED**

Run: `cd aicp-frontend && node --test tests/canvas-r0-contract.test.js`

Expected: FAIL with matches in the current Canvas sources.

- [ ] **Step 3: Remove the entries and fake local success path**

Keep only production-safe slash actions:

```js
const SLASH_COMMANDS = ['图像编辑', '多图参考融合', '全景模式', '智能打光', '宫格拆分', '镜像翻转', '旋转', '分镜组', '视频高清', '视频解析']
```

On node loading failure set an explicit error state; do not call `loadLocalCanvas()` for authenticated production projects. Remove compose/clip/splice API methods and all `VideoComposeTimeline` imports and template usage.

- [ ] **Step 4: Run full frontend tests and build**

Run: `cd aicp-frontend && npm test && npm run build`

Expected: all Node tests PASS and Vite exits 0.

- [ ] **Step 5: Commit**

```bash
git add -A aicp-frontend/src/views/Canvas.vue aicp-frontend/src/views/canvas aicp-frontend/src/api/canvas.js aicp-frontend/tests/canvas-r0-contract.test.js
git commit -m "refactor: remove canvas post-production placeholders"
```

### Task 6: Add R0 flags and release verification

**Files:**
- Create: `aicp-frontend/src/config/canvasFeatures.js`
- Modify: `aicp-backend/src/main/resources/application.yml`
- Modify: `aicp-frontend/tests/canvas-r0-contract.test.js`

- [ ] **Step 1: Add failing flag tests**

```js
test('R0 flags default to safe behavior', () => {
  assert.deepEqual(canvasFeatures({}), {
    kernelV2: false, typedPorts: false, directorV2: false,
    modelAdapterV2: false, qualityDeliveryV2: false
  })
})
```

- [ ] **Step 2: Run and verify RED**

Run: `cd aicp-frontend && node --test tests/canvas-r0-contract.test.js`

Expected: FAIL because `canvasFeatures` is absent.

- [ ] **Step 3: Implement environment-backed flags**

```js
export function canvasFeatures(env = import.meta.env) {
  const enabled = key => env[key] === 'true'
  return {
    kernelV2: enabled('VITE_CANVAS_KERNEL_V2'),
    typedPorts: enabled('VITE_TYPED_PORTS'),
    directorV2: enabled('VITE_DIRECTOR_V2'),
    modelAdapterV2: enabled('VITE_MODEL_ADAPTER_V2'),
    qualityDeliveryV2: enabled('VITE_QUALITY_DELIVERY_V2')
  }
}
```

Add matching backend defaults under `aicp.canvas.features`, all `false`.

- [ ] **Step 4: Run release verification**

Run: `cd aicp-backend && mvn test && cd ../aicp-frontend && npm test && npm run build`

Expected: Maven BUILD SUCCESS, all Node tests PASS, Vite exits 0.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/config/canvasFeatures.js aicp-frontend/tests/canvas-r0-contract.test.js aicp-backend/src/main/resources/application.yml
git commit -m "chore: gate canvas production kernel rollout"
```
