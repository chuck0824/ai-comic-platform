# Standalone Blank Canvas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow a user to create an independent blank canvas with only a name and immediately enter the existing canvas editor, while preserving the existing upstream-bound creation flow.

**Architecture:** Keep the existing canvas route, table, API, and editor. Make upstream ownership fields optional as one atomic group, create a standalone production snapshot when that group is absent, and move the frontend's upstream selectors behind an optional association control. Pure frontend helpers define validation and API parameter contracts; the backend service remains the authoritative validator.

**Tech Stack:** Vue 3, Element Plus, Node test runner, Spring Boot, MyBatis-Plus, JUnit 5, Mockito, H2/MySQL.

---

## File map

- Modify `aicp-frontend/src/views/canvas-project/canvasProjectViewModel.js`: define blank-vs-bound draft validation and admission query serialization.
- Modify `aicp-frontend/tests/canvas-project-view-model.test.js`: cover the new creation contract and query parameter names.
- Modify `aicp-frontend/src/views/canvas-project/CreateCanvasDialog.vue`: make upstream association optional and preserve the existing bound workflow.
- Modify `aicp-backend/src/main/java/com/aicp/module/canvas/dto/CanvasProjectRequests.java`: allow nullable upstream fields in create requests.
- Modify `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasProjectManagementService.java`: validate atomic binding, create standalone snapshots, and persist workspace scope.
- Create `aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java`: regression tests for standalone, invalid partial, bound, and idempotent creation.
- Modify `aicp-frontend/tests/navigation-contract.test.js`: protect the create-success route contract.

### Task 1: Define the frontend blank-canvas contract

**Files:**
- Modify: `aicp-frontend/tests/canvas-project-view-model.test.js`
- Modify: `aicp-frontend/src/views/canvas-project/canvasProjectViewModel.js`

- [ ] **Step 1: Replace the ownership-only validation test with failing standalone and partial-binding tests**

```js
test('standalone canvas creation only requires name and purpose', () => {
  assert.deepEqual(validateCanvasDraft({ name: '', purpose: 'experiment' }), ['name'])
  assert.deepEqual(validateCanvasDraft({ name: '空白画布', purpose: 'experiment' }), [])
})

test('partial upstream binding reports the missing binding fields', () => {
  const missing = validateCanvasDraft({
    name: '绑定画布', purpose: 'official', contentProjectId: 1
  })
  assert.deepEqual(missing, [
    'productionUnitType', 'productionUnitId',
    'sourceContentVersionId', 'sourceStoryboardVersionId'
  ])
})

test('admission params use the backend query contract', () => {
  assert.deepEqual(buildAdmissionParams({
    contentProjectId: 7, productionUnitId: 9, purpose: 'official'
  }), {
    contentProjectId: 7, productionUnitId: 9, purpose: 'official'
  })
})
```

Add `buildAdmissionParams` to the test import.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js`

Expected: FAIL because standalone drafts still require every upstream field and `buildAdmissionParams` is not exported.

- [ ] **Step 3: Implement the minimal pure helpers**

```js
const UPSTREAM_FIELDS = [
  'contentProjectId', 'productionUnitType', 'productionUnitId',
  'sourceContentVersionId', 'sourceStoryboardVersionId'
]

export function validateCanvasDraft(draft = {}) {
  const missing = []
  if (!draft.name?.trim()) missing.push('name')
  if (!draft.purpose) missing.push('purpose')

  const hasBinding = UPSTREAM_FIELDS.some(field => Boolean(draft[field]))
  if (hasBinding) {
    missing.push(...UPSTREAM_FIELDS.filter(field => !draft[field]))
  }
  return missing
}

export function buildAdmissionParams({ contentProjectId, productionUnitId, purpose }) {
  return { contentProjectId, productionUnitId, purpose }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js`

Expected: all tests PASS.

- [ ] **Step 5: Commit the contract change**

```bash
git add aicp-frontend/src/views/canvas-project/canvasProjectViewModel.js aicp-frontend/tests/canvas-project-view-model.test.js
git commit -m "test: define standalone canvas creation contract"
```

### Task 2: Make the existing creation dialog blank-first

**Files:**
- Modify: `aicp-frontend/src/views/canvas-project/CreateCanvasDialog.vue`
- Modify: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Add failing source-contract tests for optional association and routing**

```js
const createCanvasDialogPath = fileURLToPath(new URL(
  '../src/views/canvas-project/CreateCanvasDialog.vue', import.meta.url
))

test('canvas dialog defaults to an independent experimental canvas', () => {
  const source = fs.readFileSync(createCanvasDialogPath, 'utf8')
  assert.match(source, /const linkContent = ref\(false\)/)
  assert.match(source, /purpose:\s*'experiment'/)
})

test('canvas dialog uses camelCase admission query parameters', () => {
  const source = fs.readFileSync(createCanvasDialogPath, 'utf8')
  assert.match(source, /buildAdmissionParams\(form\.value\)/)
  assert.doesNotMatch(source, /content_project_id:\s*form\.value\.contentProjectId/)
})

test('canvas center routes a newly created canvas to the existing editor', () => {
  const centerPath = fileURLToPath(new URL(
    '../src/views/canvas-project/CanvasProjectCenter.vue', import.meta.url
  ))
  const source = fs.readFileSync(centerPath, 'utf8')
  assert.match(source, /router\.push\(`\/canvas\/\$\{canvas\.uuid\}`\)/)
})
```

- [ ] **Step 2: Run the navigation contract test and verify RED**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js`

Expected: FAIL because `linkContent` and `buildAdmissionParams` are absent.

- [ ] **Step 3: Update the dialog template and form state**

Add an association switch after the name field and render existing upstream fields only when enabled:

```vue
<el-form-item label="关联内容项目">
  <el-switch v-model="linkContent" active-text="现在关联" inactive-text="稍后关联" />
</el-form-item>
<template v-if="linkContent">
  <el-form-item label="所属内容项目" prop="contentProjectId">
    <el-select v-model="form.contentProjectId" placeholder="选择内容项目" style="width:100%" @change="onProjectChange">
      <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
    </el-select>
  </el-form-item>
  <el-form-item label="生产单元" prop="productionUnitId">
    <el-select v-model="form.productionUnitId" placeholder="先选择内容项目" style="width:100%" :disabled="!form.contentProjectId" @change="onUnitChange">
      <el-option v-for="u in units" :key="u.id" :label="u.title || `单元 ${u.id}`" :value="u.id" />
    </el-select>
  </el-form-item>
  <el-form-item label="来源内容版本" v-if="sourceContentVersion">
    <span class="version-info">v{{ sourceContentVersion.versionNo }} — {{ sourceContentVersion.status }}</span>
  </el-form-item>
  <el-form-item label="来源分镜版本" v-if="sourceStoryboardVersion">
    <span class="version-info">修订 {{ sourceStoryboardVersion.revision || 1 }} — {{ sourceStoryboardVersion.status }}</span>
  </el-form-item>
  <el-alert
    v-if="form.productionUnitId && (!sourceContentVersion || !sourceStoryboardVersion)"
    type="warning"
    :closable="false"
    title="当前项目缺少可用的内容版本或分镜版本，请先补齐，或关闭关联后创建空白画布。"
  />
  <el-form-item v-if="admissionResult && !admissionResult.passed" label="准入状态">
    <el-alert type="warning" :closable="false" show-icon>
      <template #title>生产准入未通过</template>
      <ul style="margin:4px 0;padding-left:16px">
        <li v-for="r in admissionResult.missingRequirements" :key="r.code">{{ r.label }}</li>
      </ul>
    </el-alert>
  </el-form-item>
</template>
```

Initialize blank-first state and clear stale binding values when association is disabled:

```js
import { validateCanvasDraft, buildAdmissionParams } from './canvasProjectViewModel.js'

const linkContent = ref(false)

function blankForm() {
  return {
    name: '', contentProjectId: null, productionUnitType: null,
    productionUnitId: null, sourceContentVersionId: null,
    sourceStoryboardVersionId: null, purpose: 'experiment'
  }
}

function resetForm() {
  linkContent.value = false
  form.value = blankForm()
  projects.value = []
  units.value = []
  admissionResult.value = null
  sourceContentVersion.value = null
  sourceStoryboardVersion.value = null
}

watch(linkContent, enabled => {
  if (enabled) {
    form.value.productionUnitType = 'episode' // 当前仅有剧集型生产单元，后续扩展其他类型不在此改造范围
    loadProjects()
    return
  }
  const name = form.value.name
  form.value = { ...blankForm(), name }
  units.value = []
  admissionResult.value = null
  sourceContentVersion.value = null
  sourceStoryboardVersion.value = null
})
```

Use `buildAdmissionParams(form.value)` in `checkAdmission`. Build the create payload with upstream fields set to `null` when `linkContent` is false. The existing `onCreated` event remains unchanged.

**错误处理补充：**

- 内容项目或版本加载失败时，在关联区域显示 `el-alert type="error"` 并附带重试按钮调用 `loadProjects()`。
- 空白画布创建失败（API 返回非 2xx）时，保留用户已输入的名称不清空，仅弹出错误提示。
- 创建成功但路由跳转异常时，画布已在后端持久化，用户可在项目中心列表中再次进入；在 catch 中提示“画布已创建，请从列表进入”。

- [ ] **Step 4: Run both frontend test files and verify GREEN**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js tests/navigation-contract.test.js`

Expected: all tests PASS.

- [ ] **Step 5: Commit the blank-first dialog**

```bash
git add aicp-frontend/src/views/canvas-project/CreateCanvasDialog.vue aicp-frontend/tests/navigation-contract.test.js
git commit -m "feat: allow standalone canvas creation"
```

### Task 3: Support standalone creation in the backend service

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/dto/CanvasProjectRequests.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasProjectManagementService.java`

- [ ] **Step 1: Write failing service tests**

Create a Mockito test fixture with mocked `CanvasProjectMapper` and real `ObjectMapper`. Capture inserted records and assign an ID in the insert stub.

```java
@ExtendWith(MockitoExtension.class)
class CanvasProjectManagementServiceTest {
    @Mock CanvasProjectMapper projectMapper;
    CanvasProjectManagementService service;

    @BeforeEach
    void setUp() {
        service = new CanvasProjectManagementService(projectMapper, new ObjectMapper());
        when(projectMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            CanvasProject project = invocation.getArgument(0);
            project.setId(99L);
            return 1;
        }).when(projectMapper).insert(any(CanvasProject.class));
    }

    @Test
    void createsStandaloneCanvasWithoutUpstreamIds() {
        var request = new CreateCanvasProjectRequest(
                "空白画布", null, null, null, null, null,
                "experiment", 7L, "canvas-create:7:blank:abc");

        var result = service.create(7L, request);

        assertThat(result.uuid()).startsWith("canvas_");
        assertThat(result.purpose()).isEqualTo("experiment");
        assertThat(result.contentProjectId()).isNull();
        assertThat(result.productionUnitId()).isNull();
        assertThat(result.productionSnapshot().storyboardLocked()).isFalse();
        assertThat(result.productionSnapshot().metadata().get("standalone")).isEqualTo(true);
        verify(projectMapper).insert(argThat(project ->
                "draft".equals(project.getStatus())
                        && "personal_7".equals(project.getWorkspaceId())));
    }

    @Test
    void rejectsPartialUpstreamBinding() {
        var request = new CreateCanvasProjectRequest(
                "不完整绑定", 3L, null, null, null, null,
                "official", 7L, "canvas-create:7:partial");

        assertThatThrownBy(() -> service.create(7L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("关联内容项目时必须同时提供生产单元和来源版本");
        verify(projectMapper, never()).insert(any());
    }
}
```

Add the complete-binding and idempotency tests:

```java
@Test
void preservesCompleteUpstreamBinding() {
    var request = new CreateCanvasProjectRequest(
            "绑定画布", 3L, "episode", 4L, 5L, 6L,
            "official", 7L, "canvas-create:7:bound");

    service.create(7L, request);

    verify(projectMapper).insert(argThat(project ->
            Long.valueOf(3L).equals(project.getContentProjectId())
                    && Long.valueOf(4L).equals(project.getProductionUnitId())
                    && Long.valueOf(5L).equals(project.getSourceContentVersionId())
                    && Long.valueOf(6L).equals(project.getSourceStoryboardVersionId())));
}

@Test
void returnsExistingCanvasForRepeatedIdempotencyKey() {
    CanvasProject existing = new CanvasProject();
    existing.setId(12L);
    existing.setUuid("canvas_existing");
    existing.setUserId(7L);
    existing.setOwnerId(7L);
    existing.setName("已存在画布");
    existing.setPurpose("experiment");
    existing.setStatus("draft");
    existing.setCanvasVersion(1);
    existing.setRevision(0);
    existing.setIsDeleted(0);
    existing.setProductionSnapshot("{\"storyboardLocked\":false,\"shotCount\":0,\"fps\":25}");
    when(projectMapper.selectOne(any())).thenReturn(existing);

    var request = new CreateCanvasProjectRequest(
            "空白画布", null, null, null, null, null,
            "experiment", 7L, "canvas-create:7:blank:abc");

    var result = service.create(7L, request);

    assertThat(result.uuid()).isEqualTo("canvas_existing");
    verify(projectMapper, never()).insert(any());
}
```

- [ ] **Step 2: Run the backend test and verify RED**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: compilation or assertion failure because create-request upstream fields are still `@NotNull`/`@NotBlank`, standalone snapshots report locked storyboard state, and workspace scope is not persisted.

- [ ] **Step 3: Relax DTO annotations and add authoritative service validation**

Keep `name`, `ownerId`, `purpose`, and `idempotencyKey` validation. Remove required annotations from the five upstream fields.

Add service validation before the idempotency lookup:

```java
private void validateBinding(CreateCanvasProjectRequest request) {
    boolean any = request.contentProjectId() != null
            || StringUtils.hasText(request.productionUnitType())
            || request.productionUnitId() != null
            || request.sourceContentVersionId() != null
            || request.sourceStoryboardVersionId() != null;
    boolean complete = request.contentProjectId() != null
            && StringUtils.hasText(request.productionUnitType())
            && request.productionUnitId() != null
            && request.sourceContentVersionId() != null
            && request.sourceStoryboardVersionId() != null;
    if (any && !complete) {
        throw new BizException(ErrorCode.PARAM_INVALID,
                "关联内容项目时必须同时提供生产单元和来源版本");
    }
}
```

Call `validateBinding(request)` at the top of `create`. Only execute official admission when the binding is complete. Persist workspace scope with（`WorkspaceContext` 为项目已有类，`getWorkspaceContext()` 为当前线程上下文静态方法）：

```java
WorkspaceContext context = getWorkspaceContext();
project.setWorkspaceId(context != null && StringUtils.hasText(context.workspaceId())
        ? context.workspaceId()
        : "personal_" + userId);
```

Build standalone snapshots with null content/storyboard IDs, `storyboardLocked=false`, zero shots, and metadata `{ "standalone": true }`. Bound snapshots keep the current values and use metadata `{ "standalone": false }`.

- [ ] **Step 4: Run the focused backend test and verify GREEN**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: four tests PASS.

- [ ] **Step 5: Run canvas schema compatibility tests**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectSchemaTest,CanvasProjectMigrationTest test`

Expected: all tests PASS; no database migration is needed because all upstream columns are nullable.

- [ ] **Step 6: Commit the backend change**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas/dto/CanvasProjectRequests.java aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasProjectManagementService.java aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java
git commit -m "feat: create standalone blank canvases"
```

### Task 4: Full verification and browser acceptance

**Files:**
- No production files unless a verification failure reveals a regression; any fix starts with a focused failing test.

- [ ] **Step 1: Run the complete frontend test suite**

Run: `cd aicp-frontend && node --test tests/*.test.js`

Expected: all tests PASS with zero failures.

- [ ] **Step 2: Build the frontend**

Run: `cd aicp-frontend && npm run build`

Expected: Vite exits with code 0 and writes the static bundle.

- [ ] **Step 3: Run the complete backend test suite**

Run: `cd aicp-backend && mvn test`

Expected: Maven exits with `BUILD SUCCESS` and zero test failures.

- [ ] **Step 4: Restart the local backend with the dev profile**

Stop the existing `com.aicp.AicpApplication` process, then run `cd aicp-backend && mvn spring-boot:run`. Confirm `curl -fsS http://localhost:8080/api/health` exits with code 0 before browser testing.

- [ ] **Step 5: Verify the user flow in the in-app browser**

Perform this exact flow:

1. Log in with the documented dev account.
2. Open `/canvas-projects`.
3. Click “新建画布”.
4. Confirm “稍后关联” is selected by default.
5. Enter “空白画布验收” and create.
6. Confirm the URL matches `/canvas/canvas_*` and the existing editor renders.
7. Add one basic text node and confirm it remains after reload.
8. Return to `/canvas-projects` and confirm the new canvas is listed and can be reopened.

Expected: no “服务器繁忙” toast, no disabled create button after the name is entered, and no console errors related to canvas creation.

- [ ] **Step 6: Inspect the final diff**

Run: `git status --short && git diff --check && git log -5 --oneline`

Expected: only intended files are changed or committed; `git diff --check` reports no whitespace errors. Preserve all unrelated pre-existing user changes.
