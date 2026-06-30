# Platform Home and Canvas Project Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real platform home and a traceable canvas-management flow in which every canvas belongs to a content project, production unit, content version, and storyboard version.

**Architecture:** Keep `Canvas.vue` as the focused editor and add two management surfaces over a shared canvas-project API: a global `CanvasProjectCenter` and a project-scoped `ProjectCanvasProduction`. Extend `canvas_projects` with explicit content-project and source-version relationships plus an immutable JSON production snapshot; route `/` to the new platform home and route canvas navigation through `/canvas-projects` before `/canvas/:canvasProjectId`.

**Tech Stack:** Vue 3, Vue Router, Pinia, Element Plus, Axios, Node test runner, Spring Boot 3.2, Java 17, MyBatis-Plus, H2/MySQL, JUnit 5, Mockito.

---

## File map

### Frontend

- Modify `aicp-frontend/src/router/index.js`: home, canvas-center, project-canvas, and editor routes.
- Modify `aicp-frontend/src/components/Sidebar.vue`: remove duplicate script entry and point canvas navigation to the center.
- Replace `aicp-frontend/src/views/Dashboard.vue`: platform-home composition and real API states.
- Create `aicp-frontend/src/views/dashboard/homeViewModel.js`: pure mapping for home cards and continuation actions.
- Create `aicp-frontend/tests/platform-home.test.js`: home mapping and route decisions.
- Modify `aicp-frontend/src/api/canvas.js`: list, create, copy, archive, restore, and project-scoped APIs.
- Create `aicp-frontend/src/views/canvas-project/CanvasProjectCenter.vue`: global list, filtering, empty/error/loading states.
- Create `aicp-frontend/src/views/canvas-project/CanvasProjectCard.vue`: reusable canvas summary.
- Create `aicp-frontend/src/views/canvas-project/CreateCanvasDialog.vue`: mandatory ownership and source selection.
- Create `aicp-frontend/src/views/canvas-project/ProjectCanvasProduction.vue`: project-scoped unit/canvas management.
- Create `aicp-frontend/src/views/canvas-project/canvasProjectViewModel.js`: pure filter, status, and route helpers.
- Create `aicp-frontend/tests/canvas-project-view-model.test.js`: mapping and required-field tests.
- Modify `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue`: workspace tab shell and canvas-production integration.
- Modify `aicp-frontend/src/views/Canvas.vue`: require a route ID and display source context; remove auto-create fallback.
- Modify `aicp-frontend/src/views/canvas/composables/useCanvasState.js`: remove hard-coded canvas ID.

### Backend

- Modify `aicp-backend/src/main/resources/db/schema.sql`.
- Modify `aicp-backend/src/main/resources/db/schema-mysql.sql`.
- Modify `aicp-backend/src/main/resources/db/schema-h2.sql`.
- Modify `aicp-backend/src/main/java/com/aicp/module/canvas/entity/CanvasProject.java`.
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/dto/CanvasProjectCreateRequest.java`.
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/dto/CanvasProjectQuery.java`.
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/dto/CanvasProjectSummary.java`.
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/dto/ProductionSnapshot.java`.
- Modify `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasService.java`.
- Modify `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasController.java`.
- Modify `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java`.
- Create `aicp-backend/src/test/java/com/aicp/module/canvas/schema/CanvasProjectSchemaTest.java`.
- Create `aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java`.
- Create `aicp-backend/src/test/java/com/aicp/module/canvas/CanvasProjectSecurityIntegrationTest.java`.

## Task 1: Lock the canvas ownership schema

**Files:**
- Modify: `aicp-backend/src/main/resources/db/schema.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/schema/CanvasProjectSchemaTest.java`

- [ ] **Step 1: Write the failing schema test**

```java
@SpringBootTest
@ActiveProfiles("dev")
class CanvasProjectSchemaTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void canvasProjectHasTraceableOwnershipColumns() {
        var columns = jdbc.queryForList(
            "select column_name from information_schema.columns where table_name='CANVAS_PROJECTS'",
            String.class);
        assertThat(columns).contains(
            "CONTENT_PROJECT_ID", "PRODUCTION_UNIT_TYPE", "PRODUCTION_UNIT_ID",
            "SOURCE_CONTENT_VERSION_ID", "SOURCE_STORYBOARD_VERSION_ID",
            "PRODUCTION_SNAPSHOT", "PURPOSE", "OWNER_ID", "THUMBNAIL_URL");
    }
}
```

- [ ] **Step 2: Run the test and verify failure**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectSchemaTest test`

Expected: FAIL because the new columns do not exist.

- [ ] **Step 3: Add the columns and indexes to all three schemas**

Use the same logical shape in H2 and MySQL:

```sql
content_project_id BIGINT NOT NULL,
production_unit_type VARCHAR(32) NOT NULL,
production_unit_id BIGINT NOT NULL,
source_content_version_id BIGINT NOT NULL,
source_storyboard_version_id BIGINT NOT NULL,
production_snapshot JSON NOT NULL,
purpose VARCHAR(32) NOT NULL DEFAULT 'official',
owner_id BIGINT NOT NULL,
thumbnail_url VARCHAR(500),
idempotency_key VARCHAR(100) NOT NULL,
archived_at DATETIME,
UNIQUE KEY uk_canvas_idempotency (user_id, idempotency_key),
INDEX idx_canvas_owner_status (user_id, status, updated_at),
INDEX idx_canvas_content_unit (content_project_id, production_unit_id)
```

Keep `script_id` and `episode_index` nullable during migration; mark them legacy in comments rather than removing them in this change.

- [ ] **Step 4: Run the schema test**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectSchemaTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/resources/db/schema*.sql aicp-backend/src/test/java/com/aicp/module/canvas/schema/CanvasProjectSchemaTest.java
git commit -m "feat: add traceable canvas ownership schema"
```

## Task 2: Define the canvas project API contract

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/entity/CanvasProject.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/dto/CanvasProjectCreateRequest.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/dto/CanvasProjectQuery.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/dto/CanvasProjectSummary.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/dto/ProductionSnapshot.java`

- [ ] **Step 1: Add compile-time DTO coverage to the service test**

```java
@Test
void createRequestCarriesEveryRequiredSourceReference() {
    var request = new CanvasProjectCreateRequest(
        "第 08 集正式生产", 10L, "episode", 80L, 501L, 900L,
        "official", 7L, "canvas-create:10:80:501:900:official");
    assertThat(request.contentProjectId()).isEqualTo(10L);
    assertThat(request.productionUnitId()).isEqualTo(80L);
    assertThat(request.sourceStoryboardVersionId()).isEqualTo(900L);
}
```

- [ ] **Step 2: Run test compilation and verify failure**

Run: `cd aicp-backend && mvn -DskipTests test-compile`

Expected: FAIL because `CanvasProjectCreateRequest` does not exist.

- [ ] **Step 3: Create validated records**

```java
public record CanvasProjectCreateRequest(
    @NotBlank String name,
    @NotNull Long contentProjectId,
    @NotBlank String productionUnitType,
    @NotNull Long productionUnitId,
    @NotNull Long sourceContentVersionId,
    @NotNull Long sourceStoryboardVersionId,
    @Pattern(regexp = "official|alternative|experiment") String purpose,
    @NotNull Long ownerId,
    @NotBlank String idempotencyKey
) {}
```

`ProductionSnapshot` must contain content version ID/hash, storyboard version ID/revision, platform rule version, plugin package version, aspect ratio, resolution, and fps. `CanvasProjectQuery` contains `page`, `pageSize`, `status`, `creationMode`, `contentProjectId`, and `keyword`. `CanvasProjectSummary` contains ownership labels, source labels, node/task counts, status, purpose, thumbnail, owner, and timestamps.

- [ ] **Step 4: Add matching fields to `CanvasProject` and compile**

Run: `cd aicp-backend && mvn -DskipTests test-compile`

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas
git commit -m "feat: define canvas project management contract"
```

## Task 3: Implement secure list and project-scoped queries

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Test these exact behaviors with Mockito: current-user restriction is always applied; `contentProjectId`, status, and keyword filters are optional; results order by `updatedAt` descending; archived rows are excluded unless explicitly requested.

```java
@Test
void listNeverDropsCurrentUserScope() {
    var query = new CanvasProjectQuery(1, 20, "editing", null, 10L, "第 08 集");
    service.listProjects(query);
    verify(projectMapper).selectPage(any(), argThat(wrapper ->
        wrapper.getTargetSql().contains("user_id") &&
        wrapper.getTargetSql().contains("content_project_id")));
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: FAIL because `listProjects` is absent.

- [ ] **Step 3: Implement paged queries and summary mapping**

Add `GET /api/v1/canvas/projects` in `CanvasController` and `GET /api/v1/content-projects/{projectId}/canvas-projects` in `ContentProjectController`. Reuse one service method; the second endpoint supplies a mandatory `contentProjectId`. Both paths must call `ProjectAccessService.requireView(projectId)` before querying and return `items`, `page`, `page_size`, `total`, and `has_more`.

- [ ] **Step 4: Run service tests**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java
git commit -m "feat: add secure canvas project queries"
```

## Task 4: Create canvases with immutable source snapshots

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasController.java`
- Modify: `aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java`

- [ ] **Step 1: Write failing creation tests**

Cover: missing content project, production unit outside that project, source content version outside the unit, unlocked/missing storyboard version, unauthorized owner, and duplicate idempotency key. The duplicate test must assert that the existing canvas is returned and no second insert occurs.

```java
@Test
void createIsIdempotent() {
    when(projectMapper.selectOne(any())).thenReturn(existingCanvas);
    var result = service.createProject(validRequest);
    assertThat(result.getUuid()).isEqualTo(existingCanvas.getUuid());
    verify(projectMapper, never()).insert(any());
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: FAIL on the new creation rules.

- [ ] **Step 3: Replace map-based creation with the validated request**

Build `ProductionSnapshot` from server-side records; never trust client-supplied hashes, revisions, project IDs embedded in version objects, or access decisions. Serialize it with `ObjectMapper`, persist it in the same transaction as the canvas, and return `409` only when the same key conflicts with different source fields.

- [ ] **Step 4: Run tests**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java
git commit -m "feat: create canvases from immutable source snapshots"
```

## Task 5: Add copy, archive, restore, and security behavior

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/CanvasProjectSecurityIntegrationTest.java`

- [ ] **Step 1: Write failing endpoint/security tests**

Test owner access and denial for another user for list, get, copy, archive, restore, node list, and update. Copy must preserve the source snapshot and create a new UUID/idempotency key; archive sets `status=archived` and `archived_at`; restore returns to `editing`.

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectSecurityIntegrationTest test`

Expected: FAIL because lifecycle endpoints are absent.

- [ ] **Step 3: Add endpoints**

```text
POST /api/v1/canvas/projects/{id}/copy
POST /api/v1/canvas/projects/{id}/archive
POST /api/v1/canvas/projects/{id}/restore
DELETE /api/v1/canvas/projects/{id}
```

Deletion must reject completed canvases with export records; those canvases can only be archived.

- [ ] **Step 4: Run canvas backend tests**

Run: `cd aicp-backend && mvn -Dtest='CanvasProject*' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/test/java/com/aicp/module/canvas
git commit -m "feat: add canvas project lifecycle and security"
```

## Task 6: Fix root routing and sidebar semantics

**Files:**
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/src/components/Sidebar.vue`
- Create: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Write a failing source-contract test**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

test('root and sidebar expose one unambiguous navigation model', () => {
  const router = fs.readFileSync(new URL('../src/router/index.js', import.meta.url), 'utf8')
  const sidebar = fs.readFileSync(new URL('../src/components/Sidebar.vue', import.meta.url), 'utf8')
  assert.match(router, /redirect:\s*['"]\/['"]/)
  assert.match(router, /path:\s*['"]canvas-projects['"]/)
  assert.equal((sidebar.match(/>\s*剧本创作\s*</g) || []).length, 1)
  assert.match(sidebar, /to="\/canvas-projects"/)
})
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js`

Expected: FAIL because root redirects to `/canvas` and the sidebar duplicates “剧本创作”.

- [ ] **Step 3: Implement route and sidebar changes**

Use `/` for `Dashboard`, redirect `/dashboard` to `/`, add `/canvas-projects`, keep `/canvas/:projectId` for the editor, and redirect bare `/canvas` to `/canvas-projects`. Add “首页” as the first sidebar item and remove the management-group duplicate.

- [ ] **Step 4: Run the test**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/router/index.js aicp-frontend/src/components/Sidebar.vue aicp-frontend/tests/navigation-contract.test.js
git commit -m "fix: establish platform home navigation"
```

## Task 7: Build the platform home

**Files:**
- Replace: `aicp-frontend/src/views/Dashboard.vue`
- Create: `aicp-frontend/src/views/dashboard/homeViewModel.js`
- Create: `aicp-frontend/tests/platform-home.test.js`
- Modify: `aicp-frontend/src/api/contentProject.js`
- Modify: `aicp-frontend/src/api/canvas.js`

- [ ] **Step 1: Write failing view-model tests**

```js
test('creation cards preserve three explicit modes', () => {
  assert.deepEqual(creationCards.map(x => x.mode), ['short_drama', 'long_form', 'tvc'])
})

test('continuation action follows current stage', () => {
  assert.equal(continuationAction({ stage: 'content', projectId: 7 }).path, '/script-gen/7/workspace')
  assert.equal(continuationAction({ stage: 'canvas', canvasProjectId: 'canvas_x' }).path, '/canvas/canvas_x')
})
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/platform-home.test.js`

Expected: FAIL because the view model does not exist.

- [ ] **Step 3: Implement pure mapping and the page**

The page loads content projects and canvas summaries in parallel and renders: three creation cards; 3–5 continuation rows; canvas-production summary; warehouse/market/asset links; four lightweight metrics. Fix the current `Dashboard.vue` reference to undefined `data`; errors must degrade per section rather than blank the whole page.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/platform-home.test.js && npm run build`

Expected: tests PASS and Vite build succeeds.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/Dashboard.vue aicp-frontend/src/views/dashboard aicp-frontend/src/api aicp-frontend/tests/platform-home.test.js
git commit -m "feat: build platform creation home"
```

## Task 8: Build the global canvas project center

**Files:**
- Create: `aicp-frontend/src/views/canvas-project/CanvasProjectCenter.vue`
- Create: `aicp-frontend/src/views/canvas-project/CanvasProjectCard.vue`
- Create: `aicp-frontend/src/views/canvas-project/canvasProjectViewModel.js`
- Create: `aicp-frontend/tests/canvas-project-view-model.test.js`
- Modify: `aicp-frontend/src/api/canvas.js`

- [ ] **Step 1: Write failing view-model tests**

Test status labels, purpose labels, query serialization, card primary action, archived read-only behavior, and empty keyword omission.

```js
test('editor route always uses the canvas UUID', () => {
  assert.equal(canvasRoute({ uuid: 'canvas_abc' }), '/canvas/canvas_abc')
})
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js`

Expected: FAIL because helpers do not exist.

- [ ] **Step 3: Implement API and center UI**

Add `listProjects`, `listByContentProject`, `copyProject`, `archiveProject`, `restoreProject`, and `deleteProject`. Build loading, retry, empty, filter, search, pagination, card menu, and destructive-action confirmation states. Do not fetch nodes per card; summary counts must come from the list API.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js && npm run build`

Expected: PASS and build success.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas-project aicp-frontend/src/api/canvas.js aicp-frontend/tests/canvas-project-view-model.test.js
git commit -m "feat: add global canvas project center"
```

## Task 9: Enforce owned-canvas creation in the UI

**Files:**
- Create: `aicp-frontend/src/views/canvas-project/CreateCanvasDialog.vue`
- Modify: `aicp-frontend/src/views/canvas-project/CanvasProjectCenter.vue`
- Modify: `aicp-frontend/src/views/canvas-project/canvasProjectViewModel.js`
- Modify: `aicp-frontend/tests/canvas-project-view-model.test.js`

- [ ] **Step 1: Write failing validation tests**

```js
test('canvas creation requires every ownership field', () => {
  assert.deepEqual(validateCanvasDraft({}), [
    'content_project_id', 'production_unit_id', 'source_content_version_id',
    'source_storyboard_version_id', 'purpose', 'name'
  ])
})
```

Also test deterministic idempotency key generation from user, project, unit, content version, storyboard version, and purpose.

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js`

Expected: FAIL on validation helpers.

- [ ] **Step 3: Implement dependent selection and submission**

Project selection loads production units; unit selection loads approved/locked content versions and locked storyboard versions. Disable submission until all required fields are present. On `409`, show the existing canvas link; on production-admission failure, show missing requirements and a link back to the storyboard.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js && npm run build`

Expected: PASS and build success.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas-project aicp-frontend/tests/canvas-project-view-model.test.js
git commit -m "feat: require canvas ownership during creation"
```

## Task 10: Add project-scoped canvas production

**Files:**
- Create: `aicp-frontend/src/views/canvas-project/ProjectCanvasProduction.vue`
- Modify: `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue`
- Modify: `aicp-frontend/src/views/canvas-project/canvasProjectViewModel.js`
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Extend the failing navigation test**

Add and test a pure `workspaceTab(route.query.tab)` helper in `canvasProjectViewModel.js`. Assert that it returns `canvas` only for the supported query value and defaults to `workflow`; also assert that project-scoped canvas API calls receive `projectId`.

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js`

Expected: FAIL because the workspace has no canvas tab.

- [ ] **Step 3: Add the tab and project-scoped view**

The left column lists production units with canvas counts. The right column shows source content/storyboard versions, admission state, upstream-change warning, and all official/alternative/experiment canvases for the selected unit. Reuse `CanvasProjectCard` and `CreateCanvasDialog`; do not duplicate their lifecycle logic.

- [ ] **Step 4: Run frontend tests and build**

Run: `cd aicp-frontend && node --test tests/*.test.js && npm run build`

Expected: all Node tests PASS and build succeeds.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas-project aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue aicp-frontend/src/router/index.js aicp-frontend/tests
git commit -m "feat: add project canvas production workspace"
```

## Task 11: Make the canvas editor ID-safe and source-aware

**Files:**
- Modify: `aicp-frontend/src/views/Canvas.vue`
- Modify: `aicp-frontend/src/views/canvas/composables/useCanvasState.js`
- Create: `aicp-frontend/tests/canvas-route-state.test.js`

- [ ] **Step 1: Write failing state tests**

Extract `resolveCanvasProjectId(route)` and test that missing IDs return `null`, never `canvas_a1b2c3`; a valid route returns the exact UUID.

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/canvas-route-state.test.js`

Expected: FAIL because the composable still hard-codes a demo ID.

- [ ] **Step 3: Remove editor auto-creation**

When no ID exists, redirect to `/canvas-projects`. When the requested canvas is missing or forbidden, show a recoverable error and link to the center. Remove `ensureProject()` fallback creation and local `local_canvas` assignment. Display breadcrumb/source snapshot metadata returned by `getProject`.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/canvas-route-state.test.js && npm run build`

Expected: PASS and build success.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/Canvas.vue aicp-frontend/src/views/canvas/composables/useCanvasState.js aicp-frontend/tests/canvas-route-state.test.js
git commit -m "fix: require explicit canvas editor identity"
```

## Task 12: Verify the complete vertical flow

**Files:**
- No planned file changes; this task is verification-only.

- [ ] **Step 1: Run all backend tests**

Run: `cd aicp-backend && mvn test`

Expected: BUILD SUCCESS with all existing and new tests passing.

- [ ] **Step 2: Run all frontend tests**

Run: `cd aicp-frontend && node --test tests/*.test.js`

Expected: all tests PASS.

- [ ] **Step 3: Build the frontend**

Run: `cd aicp-frontend && npm run build`

Expected: Vite build succeeds without unresolved imports or duplicate route names.

- [ ] **Step 4: Perform browser acceptance checks**

Verify these exact paths on the local app:

```text
/ → home with three creation cards
/canvas → redirects to /canvas-projects
/canvas-projects → filterable global center
/script-gen/{id}/workspace?tab=canvas → only that project's canvases
/canvas/{uuid} → the selected editor with source context
```

Also verify: one sidebar “剧本创作”; no auto-created blank canvas; new canvas cannot submit without ownership; cross-user canvas URLs do not expose existence; upstream version changes do not overwrite an existing snapshot.

- [ ] **Step 5: Record the verification result**

Run: `git status --short`

Expected: no new uncommitted files from verification. If a command fails, return to the task that owns the failing behavior, add a focused failing test there, and make a separate commit that stages only the exact files changed for that fix.
