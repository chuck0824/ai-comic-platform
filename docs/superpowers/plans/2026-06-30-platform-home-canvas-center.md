# Platform Home and Canvas Project Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real platform home and a traceable canvas-management flow in which every canvas belongs to a content project, production unit, content version, and storyboard version.

**Architecture:** Keep `Canvas.vue` as the focused editor and add two management surfaces over a shared canvas-project API: a global `CanvasProjectCenter` and a project-scoped `ProjectCanvasProduction`. Extend `canvas_projects` with explicit content-project and source-version relationships plus an immutable JSON production snapshot; route `/` to the new platform home and route canvas navigation through `/canvas-projects` before `/canvas/:canvasProjectId`.

**Tech Stack:** Vue 3, Vue Router, Pinia, Element Plus, Axios, Node test runner, Spring Boot 3.2, Java 17, MyBatis-Plus, H2/MySQL, JUnit 5, Mockito.

**Naming convention:** The sidebar label "画布工作台" and the page title "画布项目中心" refer to the same feature. All code identifiers (components, routes, API paths) use `CanvasProjectCenter` / `canvas-projects` consistently.

---

## File map

### Frontend

- Modify `aicp-frontend/src/router/index.js`: home, canvas-center, project-canvas, and editor routes.
- Modify `aicp-frontend/src/components/Sidebar.vue`: remove duplicate script entry and point canvas navigation to the center.
- Replace `aicp-frontend/src/views/Dashboard.vue`: platform-home composition and real API states.
- Create `aicp-frontend/src/views/dashboard/homeViewModel.js`: pure mapping for home cards and continuation actions.
- Create `aicp-frontend/tests/platform-home.test.js`: home mapping and route decisions.
- Modify `aicp-frontend/src/api/canvas.js`: list, create, copy, archive, restore, move, project-scoped, and admission-check APIs.
- Create `aicp-frontend/src/views/canvas-project/CanvasProjectCenter.vue`: global list, filtering, empty/error/loading states.
- Create `aicp-frontend/src/views/canvas-project/CanvasProjectCard.vue`: reusable canvas summary with upstream-change indicator.
- Create `aicp-frontend/src/views/canvas-project/CreateCanvasDialog.vue`: mandatory ownership and source selection with admission pre-check.
- Create `aicp-frontend/src/views/canvas-project/ProjectCanvasProduction.vue`: project-scoped unit/canvas management.
- Create `aicp-frontend/src/views/canvas-project/canvasProjectViewModel.js`: pure filter, status, route helpers, and validation.
- Create `aicp-frontend/src/views/canvas-project/CanvasSourceSnapshotDiff.vue`: source-vs-upstream diff with severity badges.
- Create `aicp-frontend/src/views/canvas-project/CanvasBreadcrumb.vue`: unified breadcrumb for canvas pages.
- Create `aicp-frontend/tests/canvas-project-view-model.test.js`: mapping and required-field tests.
- Modify `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue`: workspace tab shell and canvas-production integration.
- Modify `aicp-frontend/src/views/Canvas.vue`: require a route ID, display source context and upstream-change banner; remove auto-create fallback.
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
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/dto/SourceDiffResult.java`.
- Create `aicp-backend/src/main/java/com/aicp/module/canvas/dto/ProductionAdmissionResult.java`.
- Modify `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasService.java`.
- Modify `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasController.java`.
- Modify `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java`.
- Create `aicp-backend/src/test/java/com/aicp/module/canvas/schema/CanvasProjectSchemaTest.java`.
- Create `aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java`.
- Create `aicp-backend/src/test/java/com/aicp/module/canvas/CanvasProjectSecurityIntegrationTest.java`.
- Create `aicp-backend/src/test/java/com/aicp/module/canvas/CanvasProjectMigrationTest.java`.

---

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
idempotency_key VARCHAR(200) NOT NULL,
archived_at DATETIME,
UNIQUE KEY uk_canvas_idempotency (user_id, idempotency_key),
INDEX idx_canvas_owner_status (user_id, status, updated_at),
INDEX idx_canvas_content_unit (content_project_id, production_unit_id)
```

Keep `script_id` and `episode_index` nullable during migration; mark them `-- legacy: replaced by content_project_id + production_unit_id` in comments rather than removing them in this change. Expand `idempotency_key` to `VARCHAR(200)` to accommodate the composite key format: `canvas-create:{userId}:{contentProjectId}:{productionUnitId}:{contentVersionId}:{storyboardVersionId}:{purpose}`.

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
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/dto/SourceDiffResult.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/canvas/dto/ProductionAdmissionResult.java`

- [ ] **Step 1: Add compile-time DTO coverage to the service test**

```java
@Test
void createRequestCarriesEveryRequiredSourceReference() {
    var request = new CanvasProjectCreateRequest(
        "第 08 集正式生产", 10L, "episode", 80L, 501L, 900L,
        "official", 7L, "canvas-create:7:10:80:501:900:official");
    assertThat(request.contentProjectId()).isEqualTo(10L);
    assertThat(request.productionUnitId()).isEqualTo(80L);
    assertThat(request.sourceStoryboardVersionId()).isEqualTo(900L);
}

@Test
void idempotencyKeyIsDeterministic() {
    var key1 = CanvasProjectCreateRequest.buildIdempotencyKey(7L, 10L, 80L, 501L, 900L, "official");
    var key2 = CanvasProjectCreateRequest.buildIdempotencyKey(7L, 10L, 80L, 501L, 900L, "official");
    assertThat(key1).isEqualTo(key2);
    assertThat(key1).startsWith("canvas-create:");
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
) {
    public static String buildIdempotencyKey(
        Long userId, Long contentProjectId, Long productionUnitId,
        Long contentVersionId, Long storyboardVersionId, String purpose) {
        return "canvas-create:%d:%d:%d:%d:%d:%s".formatted(
            userId, contentProjectId, productionUnitId,
            contentVersionId, storyboardVersionId, purpose);
    }
}
```

`ProductionSnapshot` must contain content version ID/hash/title/summary, storyboard version ID/revision/shot-count/locked-status, platform rule version, plugin package version, aspect ratio, resolution, and fps. `CanvasProjectQuery` contains `page`, `pageSize`, `status`, `creationMode`, `contentProjectId`, and `keyword`. `CanvasProjectSummary` contains ownership labels, source labels, node/task counts, status, purpose, thumbnail, owner, timestamps, and `hasUpstreamChanges` boolean. `SourceDiffResult` contains diff dimensions (content, storyboard, params, rules) each with severity (`blocking`, `warning`, `info`) and a list of field-level changes. `ProductionAdmissionResult` contains `passed` boolean and a list of missing requirements with remediation links.

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

Test these exact behaviors with Mockito: current-user restriction is always applied; `contentProjectId`, status, and keyword filters are optional; results order by `updatedAt` descending; archived rows are excluded unless explicitly requested; keyword search matches against content project name, production unit name, and canvas name via LIKE.

```java
@Test
void listNeverDropsCurrentUserScope() {
    var query = new CanvasProjectQuery(1, 20, "editing", null, 10L, "第 08 集");
    service.listProjects(query);
    verify(projectMapper).selectPage(any(), argThat(wrapper ->
        wrapper.getTargetSql().contains("user_id") &&
        wrapper.getTargetSql().contains("content_project_id")));
}

@Test
void listRespectsWorkspacePermissionContext() {
    // When workspace context is active, results are additionally scoped
    // to the current workspace's member projects
    workspaceContext.setCurrentWorkspaceId(100L);
    var query = new CanvasProjectQuery(1, 20, null, null, null, null);
    service.listProjects(query);
    verify(projectMapper).selectPage(any(), argThat(wrapper ->
        wrapper.getTargetSql().contains("workspace_id")));
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: FAIL because `listProjects` is absent.

- [ ] **Step 3: Implement paged queries and summary mapping**

Add `GET /api/v1/canvas/projects` in `CanvasController` and `GET /api/v1/content-projects/{projectId}/canvas-projects` in `ContentProjectController`. Reuse one service method; the second endpoint supplies a mandatory `contentProjectId`. Both paths must call `ProjectAccessService.requireView(projectId)` before querying, enforce workspace-level scope via `WorkspaceContext`, and return `items`, `page`, `page_size`, `total`, and `has_more`. Summary for each item must include `hasUpstreamChanges` computed by comparing snapshot versions against current upstream versions.

- [ ] **Step 4: Run service tests**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java
git commit -m "feat: add secure canvas project queries"
```

## Task 4: Create canvases with immutable source snapshots and admission checks

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasController.java`
- Modify: `aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java`

- [ ] **Step 1: Write failing creation tests**

Cover: missing content project, production unit outside that project, source content version outside the unit, unlocked/missing storyboard version, unauthorized owner, duplicate idempotency key, and production admission failure for official canvases. The duplicate test must assert that the existing canvas is returned (HTTP 200) and no second insert occurs. The admission test must assert that creating an official canvas without a locked storyboard is rejected.

```java
@Test
void createIsIdempotent() {
    when(projectMapper.selectOne(any())).thenReturn(existingCanvas);
    var result = service.createProject(validRequest);
    assertThat(result.getUuid()).isEqualTo(existingCanvas.getUuid());
    verify(projectMapper, never()).insert(any());
}

@Test
void officialCanvasRequiresLockedStoryboard() {
    when(storyboardMasterService.isLocked(900L)).thenReturn(false);
    assertThrows(ProductionAdmissionException.class,
        () -> service.createProject(officialRequest));
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: FAIL on the new creation rules.

- [ ] **Step 3: Implement admission-check API and validated creation**

Add `GET /api/v1/canvas/production-admission?contentProjectId={}&productionUnitId={}&purpose={}` that returns `ProductionAdmissionResult`. For official canvases, check: storyboard locked, content version approved, all required shots have assets assigned, plugin/rule versions compatible. For alternative and experiment canvases, only require storyboard existence (locked status optional).

Build `ProductionSnapshot` from server-side records; never trust client-supplied hashes, revisions, project IDs embedded in version objects, or access decisions. Serialize it with `ObjectMapper`, persist it in the same transaction as the canvas, and return `200` (with existing canvas) when the same idempotency key already exists. Return `409` only when the same key conflicts with different source fields.

- [ ] **Step 4: Run tests**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java
git commit -m "feat: create canvases from immutable source snapshots with admission checks"
```

## Task 5: Add copy, move, archive, restore, and security behavior

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/CanvasProjectSecurityIntegrationTest.java`

- [ ] **Step 1: Write failing endpoint/security tests**

Test owner access and denial for another user for list, get, copy, move, archive, restore, node list, and update. Copy must preserve the source snapshot and create a new UUID/idempotency key; archive sets `status=archived` and `archived_at`; restore returns to `editing`. Move must validate that the target production unit belongs to the same content project and that the source versions are compatible.

```java
@Test
void moveValidatesTargetUnitCompatibility() {
    // Moving to a unit in a different content project must fail
    assertThrows(ValidationException.class,
        () -> service.moveProject(canvasId, incompatibleUnitId));
}

@Test
void movePreservesAllSourceReferences() {
    var moved = service.moveProject(canvasId, compatibleUnitId);
    assertThat(moved.getContentProjectId()).isEqualTo(originalContentProjectId);
    assertThat(moved.getProductionUnitId()).isEqualTo(compatibleUnitId);
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectSecurityIntegrationTest test`

Expected: FAIL because lifecycle endpoints are absent.

- [ ] **Step 3: Add endpoints**

```text
POST /api/v1/canvas/projects/{id}/copy
POST /api/v1/canvas/projects/{id}/move       -- body: { targetProductionUnitId }
POST /api/v1/canvas/projects/{id}/archive
POST /api/v1/canvas/projects/{id}/restore
DELETE /api/v1/canvas/projects/{id}
```

Deletion must reject completed canvases with export records; those canvases can only be archived. Move must reject cross-project or cross-user targets.

- [ ] **Step 4: Run canvas backend tests**

Run: `cd aicp-backend && mvn -Dtest='CanvasProject*' test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/test/java/com/aicp/module/canvas
git commit -m "feat: add canvas project lifecycle and security"
```

## Task 6: Implement source-diff and home-aggregation APIs

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/controller/CanvasController.java`
- Modify: `aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java`

- [ ] **Step 1: Write failing source-diff tests**

```java
@Test
void sourceDiffReturnsAllChangedDimensions() {
    // Snapshot has old content version; upstream has new
    var diff = service.computeSourceDiff(canvasId);
    assertThat(diff.dimensions()).containsKey("content");
    assertThat(diff.dimensions().get("content").severity()).isEqualTo("warning");
}

@Test
void sourceDiffDetectsBlockingStoryboardReset() {
    // Snapshot storyboard was locked; upstream is now unlocked (reset)
    var diff = service.computeSourceDiff(canvasId);
    assertThat(diff.dimensions().get("storyboard").severity()).isEqualTo("blocking");
}

@Test
void homeAggregationReturnsContinueWorkingItems() {
    var result = service.getHomeContinueWorking(userId);
    assertThat(result).hasSizeLessThanOrEqualTo(5);
    assertThat(result.get(0)).hasFieldOrProperty("updatedAt");
    // Items with task errors appear first
    assertThat(result.get(0).hasErrors()).isTrue();
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: FAIL on `computeSourceDiff` and `getHomeContinueWorking`.

- [ ] **Step 3: Implement APIs**

Add:
- `GET /api/v1/canvas/projects/{id}/source-diff` — compares snapshot against current upstream versions, returns `SourceDiffResult` with per-dimension severity and field-level changes.
- `GET /api/v1/home/continue-working` — aggregates user's in-progress content projects and canvases, ordered by error-priority then `updatedAt` descending, max 5 items, excludes archived.

Both endpoints enforce current-user and workspace scope.

- [ ] **Step 4: Run service tests**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectManagementServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/test/java/com/aicp/module/canvas/service/CanvasProjectManagementServiceTest.java
git commit -m "feat: add source diff and home aggregation APIs"
```

## Task 7: Migrate existing canvas data

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V2__backfill_canvas_ownership.sql`
- Create: `aicp-backend/src/test/java/com/aicp/module/canvas/CanvasProjectMigrationTest.java`

- [ ] **Step 1: Write failing migration test**

```java
@SpringBootTest
@ActiveProfiles("dev")
class CanvasProjectMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void backfillPopulatesAllNewColumnsForExistingRows() {
        // Insert a legacy row with only script_id and episode_index
        jdbc.update("""
            INSERT INTO canvas_projects (id, name, script_id, episode_index,
                status, user_id, created_at, updated_at)
            VALUES (9999, 'Legacy Canvas', 100, 5, 'editing', 1, NOW(), NOW())
            """);

        // Run migration
        migrationRunner.run("V2__backfill_canvas_ownership.sql");

        // Verify new columns are populated
        var row = jdbc.queryForMap(
            "SELECT * FROM canvas_projects WHERE id = 9999");
        assertThat(row.get("CONTENT_PROJECT_ID")).isNotNull();
        assertThat(row.get("PRODUCTION_UNIT_TYPE")).isEqualTo("episode");
        assertThat(row.get("PRODUCTION_UNIT_ID")).isNotNull();
        assertThat(row.get("PURPOSE")).isEqualTo("official");
        assertThat(row.get("PRODUCTION_SNAPSHOT")).isNotNull();
    }

    @Test
    void backfillArchivesOrphanCanvasWithNoScriptId() {
        jdbc.update("""
            INSERT INTO canvas_projects (id, name, script_id, episode_index,
                status, user_id, created_at, updated_at)
            VALUES (9998, 'Orphan Canvas', NULL, NULL, 'editing', 1, NOW(), NOW())
            """);

        migrationRunner.run("V2__backfill_canvas_ownership.sql");

        var status = jdbc.queryForObject(
            "SELECT status FROM canvas_projects WHERE id = 9998", String.class);
        assertThat(status).isEqualTo("archived");
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectMigrationTest test`

Expected: FAIL because migration script does not exist.

- [ ] **Step 3: Write migration SQL**

```sql
-- V2__backfill_canvas_ownership.sql
-- Backfill content_project_id, production_unit_type, production_unit_id,
-- source_content_version_id, source_storyboard_version_id, production_snapshot,
-- purpose, owner_id, idempotency_key for existing canvas_projects rows.

-- 1. Archive orphan canvases (no script_id)
UPDATE canvas_projects
SET status = 'archived', archived_at = NOW()
WHERE script_id IS NULL AND status != 'archived';

-- 2. Backfill for canvases with valid script_id + episode_index
UPDATE canvas_projects cp
JOIN scripts s ON s.id = cp.script_id
LEFT JOIN content_versions cv ON cv.script_id = cp.script_id AND cv.status = 'approved'
LEFT JOIN storyboard_masters sm ON sm.script_id = cp.script_id AND sm.status = 'locked'
SET
  cp.content_project_id = s.content_project_id,
  cp.production_unit_type = 'episode',
  cp.production_unit_id = (SELECT id FROM production_units pu WHERE pu.content_project_id = s.content_project_id AND pu.episode_index = cp.episode_index LIMIT 1),
  cp.source_content_version_id = COALESCE(cv.id, 0),
  cp.source_storyboard_version_id = COALESCE(sm.id, 0),
  cp.purpose = 'official',
  cp.owner_id = cp.user_id,
  cp.production_snapshot = JSON_OBJECT(
    'contentVersionId', COALESCE(cv.id, 0),
    'contentVersionHash', COALESCE(cv.content_hash, 'migrated'),
    'storyboardVersionId', COALESCE(sm.id, 0),
    'storyboardRevision', COALESCE(sm.revision, 0),
    'storyboardLocked', COALESCE(sm.status = 'locked', FALSE),
    'platformRuleVersion', 'legacy',
    'pluginPackageVersion', 'legacy',
    'aspectRatio', '16:9',
    'resolution', '1920x1080',
    'fps', 24,
    'migratedAt', NOW()
  ),
  cp.idempotency_key = CONCAT('migrated:', cp.id)
WHERE cp.content_project_id IS NULL
  AND cp.script_id IS NOT NULL
  AND cp.status != 'archived';

-- 3. Verify no non-archived row has NULL content_project_id
-- (Run as a post-condition check; fail if any row remains)
```

- [ ] **Step 4: Run migration tests**

Run: `cd aicp-backend && mvn -Dtest=CanvasProjectMigrationTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/resources/db/migration/V2__backfill_canvas_ownership.sql aicp-backend/src/test/java/com/aicp/module/canvas/CanvasProjectMigrationTest.java
git commit -m "feat: backfill canvas ownership for existing data"
```

## Task 8: Fix root routing and sidebar semantics

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
  // Sidebar label is "画布工作台" (user-facing); route is /canvas-projects
  assert.match(sidebar, /画布工作台/)
})
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js`

Expected: FAIL because root redirects to `/canvas` and the sidebar duplicates "剧本创作".

- [ ] **Step 3: Implement route and sidebar changes**

Use `/` for `Dashboard`, redirect `/dashboard` to `/`, add `/canvas-projects`, keep `/canvas/:projectId` for the editor, and redirect bare `/canvas` to `/canvas-projects`. Add "首页" as the first sidebar item, remove the management-group duplicate, and ensure the single canvas entry uses label "画布工作台" with route `/canvas-projects`.

- [ ] **Step 4: Run the test**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/router/index.js aicp-frontend/src/components/Sidebar.vue aicp-frontend/tests/navigation-contract.test.js
git commit -m "fix: establish platform home navigation"
```

## Task 9: Build the platform home

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

test('continuation items sort errors first', () => {
  const items = [
    { stage: 'canvas', updatedAt: '2026-06-30T10:00:00Z', hasErrors: false },
    { stage: 'content', updatedAt: '2026-06-29T10:00:00Z', hasErrors: true },
  ]
  const sorted = sortContinueWorking(items)
  assert.equal(sorted[0].hasErrors, true)
})

test('empty continue-working list returns null, not error', () => {
  const result = homeViewModel({ continueWorking: [], canvasSummary: [], metrics: {} })
  assert.equal(result.continueWorkingEmpty, true)
})
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/platform-home.test.js`

Expected: FAIL because the view model does not exist.

- [ ] **Step 3: Implement pure mapping and the page**

The page loads content projects and canvas summaries (from `GET /api/v1/home/continue-working`) in parallel and renders: three creation cards; 3–5 continuation rows; canvas-production summary; warehouse/market/asset links; four lightweight metrics. Fix the current `Dashboard.vue` reference to undefined `data`; errors must degrade per section (each section has independent error boundary) rather than blank the whole page. Empty states render per design spec section 11. Skeleton placeholders shown during initial load.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/platform-home.test.js && npm run build`

Expected: tests PASS and Vite build succeeds.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/Dashboard.vue aicp-frontend/src/views/dashboard aicp-frontend/src/api aicp-frontend/tests/platform-home.test.js
git commit -m "feat: build platform creation home"
```

## Task 10: Build the global canvas project center

**Files:**
- Create: `aicp-frontend/src/views/canvas-project/CanvasProjectCenter.vue`
- Create: `aicp-frontend/src/views/canvas-project/CanvasProjectCard.vue`
- Create: `aicp-frontend/src/views/canvas-project/canvasProjectViewModel.js`
- Create: `aicp-frontend/tests/canvas-project-view-model.test.js`
- Modify: `aicp-frontend/src/api/canvas.js`

- [ ] **Step 1: Write failing view-model tests**

Test status labels, purpose labels, query serialization (omit empty keyword), card primary action, archived read-only behavior, upstream-change indicator, and deterministic editor route.

```js
test('editor route always uses the canvas UUID', () => {
  assert.equal(canvasRoute({ uuid: 'canvas_abc' }), '/canvas/canvas_abc')
})

test('card shows upstream change indicator', () => {
  const card = canvasProjectCard({ hasUpstreamChanges: true })
  assert.equal(card.showUpstreamWarning, true)
})

test('archived canvas disables edit action', () => {
  const actions = canvasActions({ status: 'archived' })
  assert.equal(actions.canEdit, false)
  assert.equal(actions.canArchive, false)
  assert.equal(actions.canRestore, true)
})

test('search query omits empty keyword', () => {
  const params = buildQueryParams({ keyword: '', status: 'editing' })
  assert.equal(params.keyword, undefined)
})
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js`

Expected: FAIL because helpers do not exist.

- [ ] **Step 3: Implement API and center UI**

Add `listProjects`, `listByContentProject`, `copyProject`, `moveProject`, `archiveProject`, `restoreProject`, and `deleteProject` to the API layer. Build loading, retry, empty (with illustration + CTA), filter, search (300ms debounced), pagination (20/page), card menu, destructive-action confirmation, and upstream-change warning badge states. Do not fetch nodes per card; summary counts must come from the list API.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js && npm run build`

Expected: PASS and build success.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas-project aicp-frontend/src/api/canvas.js aicp-frontend/tests/canvas-project-view-model.test.js
git commit -m "feat: add global canvas project center"
```

## Task 11: Enforce owned-canvas creation in the UI

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

test('idempotency key is deterministic from ownership fields', () => {
  const key1 = buildIdempotencyKey(7, 10, 80, 501, 900, 'official')
  const key2 = buildIdempotencyKey(7, 10, 80, 501, 900, 'official')
  assert.equal(key1, key2)
  assert.match(key1, /^canvas-create:/)
})

test('admission check is called before official canvas submission', () => {
  const steps = creationFlow({ purpose: 'official' })
  assert.equal(steps.includes('admissionCheck'), true)
})

test('admission failure blocks submission and shows remediation links', () => {
  const state = creationState({ admissionPassed: false, missingRequirements: ['storyboard_lock'] })
  assert.equal(state.canSubmit, false)
  assert.equal(state.remediationLinks.length, 1)
})
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js`

Expected: FAIL on validation helpers.

- [ ] **Step 3: Implement dependent selection and submission**

Project selection loads production units; unit selection loads approved/locked content versions and locked storyboard versions. On purpose and unit selection, call `GET /api/v1/canvas/production-admission` to pre-check admission status. Disable submission until all required fields are present and admission has passed (for official canvases). Show admission failure reasons inline with remediation links. On `409`, show the existing canvas link; on admission failure, show missing requirements and a link back to the storyboard or asset assignment page.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js && npm run build`

Expected: PASS and build success.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas-project aicp-frontend/tests/canvas-project-view-model.test.js
git commit -m "feat: require canvas ownership and admission during creation"
```

## Task 12: Add project-scoped canvas production

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

The left column lists production units with canvas counts (grouped by unit type: episode/chapter/tvc_variant). The right column shows source content/storyboard versions, admission state, upstream-change warning, and all official/alternative/experiment canvases for the selected unit. Reuse `CanvasProjectCard` and `CreateCanvasDialog`; do not duplicate their lifecycle logic. Empty units show "0 个画布" with a "创建画布" entry point.

- [ ] **Step 4: Run frontend tests and build**

Run: `cd aicp-frontend && node --test tests/*.test.js && npm run build`

Expected: all Node tests PASS and build succeeds.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas-project aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue aicp-frontend/src/router/index.js aicp-frontend/tests
git commit -m "feat: add project canvas production workspace"
```

## Task 13: Build source diff UI and breadcrumb navigation

**Files:**
- Create: `aicp-frontend/src/views/canvas-project/CanvasSourceSnapshotDiff.vue`
- Create: `aicp-frontend/src/views/canvas-project/CanvasBreadcrumb.vue`
- Modify: `aicp-frontend/src/views/Canvas.vue`
- Modify: `aicp-frontend/src/views/canvas-project/CanvasProjectCenter.vue`
- Modify: `aicp-frontend/src/views/canvas-project/ProjectCanvasProduction.vue`
- Modify: `aicp-frontend/tests/canvas-project-view-model.test.js`

- [ ] **Step 1: Write failing diff/breadcrumb tests**

```js
test('diff severity determines badge color', () => {
  assert.equal(severityBadge('blocking'), 'danger')
  assert.equal(severityBadge('warning'), 'warning')
  assert.equal(severityBadge('info'), 'info')
})

test('breadcrumb for editor from project canvas page', () => {
  const crumbs = buildBreadcrumb('canvas-editor', {
    projectName: '我的短剧', canvasName: '第08集正式生产',
    referrer: 'project-canvas'
  })
  assert.deepEqual(crumbs, [
    { label: '首页', path: '/' },
    { label: '剧本创作', path: '/script-gen' },
    { label: '我的短剧', path: '/script-gen/10/workspace?tab=canvas' },
    { label: '第08集正式生产', path: null }
  ])
})

test('breadcrumb for editor from global center', () => {
  const crumbs = buildBreadcrumb('canvas-editor', {
    canvasName: '第08集正式生产', referrer: 'canvas-center'
  })
  assert.deepEqual(crumbs, [
    { label: '首页', path: '/' },
    { label: '画布项目中心', path: '/canvas-projects' },
    { label: '第08集正式生产', path: null }
  ])
})
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js`

Expected: FAIL because diff/breadcrumb helpers don't exist.

- [ ] **Step 3: Implement components**

`CanvasSourceSnapshotDiff` fetches from `GET /api/v1/canvas/projects/{id}/source-diff` and renders: summary header (total changes by severity), expandable per-dimension sections with field-level before/after comparison, and storyboard visual diff grid for shot order changes. Severity badges use Element Plus `el-tag` with `danger`/`warning`/`info` types.

`CanvasBreadcrumb` renders `el-breadcrumb` with context-aware path derivation: reads current route and referrer to determine the correct intermediate levels. Last crumb is always plain text (current page). Editor breadcrumb includes status and purpose tags inline.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/canvas-project-view-model.test.js && npm run build`

Expected: PASS and build success.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas-project/CanvasSourceSnapshotDiff.vue aicp-frontend/src/views/canvas-project/CanvasBreadcrumb.vue aicp-frontend/src/views/Canvas.vue aicp-frontend/src/views/canvas-project/CanvasProjectCenter.vue aicp-frontend/src/views/canvas-project/ProjectCanvasProduction.vue aicp-frontend/tests/canvas-project-view-model.test.js
git commit -m "feat: add source diff viewer and breadcrumb navigation"
```

## Task 14: Make the canvas editor ID-safe and source-aware

**Files:**
- Modify: `aicp-frontend/src/views/Canvas.vue`
- Modify: `aicp-frontend/src/views/canvas/composables/useCanvasState.js`
- Create: `aicp-frontend/tests/canvas-route-state.test.js`

- [ ] **Step 1: Write failing state tests**

Extract `resolveCanvasProjectId(route)` and test that missing IDs return `null`, never `canvas_a1b2c3`; a valid route returns the exact UUID. Test that editor fetches source context and upstream diff on mount, and renders the upstream-change banner when diff is non-empty.

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/canvas-route-state.test.js`

Expected: FAIL because the composable still hard-codes a demo ID.

- [ ] **Step 3: Remove editor auto-creation**

When no ID exists, redirect to `/canvas-projects`. When the requested canvas is missing or forbidden, show a recoverable error and link to the center. Remove `ensureProject()` fallback creation and local `local_canvas` assignment. Display breadcrumb (`CanvasBreadcrumb`) and source snapshot metadata returned by `getProject`. On mount, fetch source diff; if diff is non-empty, display a dismissible banner: "上游版本已变更 — 查看差异" linking to the diff view. The banner does not block editing.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/canvas-route-state.test.js && npm run build`

Expected: PASS and build success.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/Canvas.vue aicp-frontend/src/views/canvas/composables/useCanvasState.js aicp-frontend/tests/canvas-route-state.test.js
git commit -m "fix: require explicit canvas editor identity with upstream awareness"
```

## Task 15: Verify the complete vertical flow

**Files:**
- No planned file changes; this task is verification-only.

- [ ] **Step 1: Run all backend tests**

Run: `cd aicp-backend && mvn test`

Expected: BUILD SUCCESS with all existing and new tests passing, including migration tests.

- [ ] **Step 2: Run all frontend tests**

Run: `cd aicp-frontend && node --test tests/*.test.js`

Expected: all tests PASS.

- [ ] **Step 3: Build the frontend**

Run: `cd aicp-frontend && npm run build`

Expected: Vite build succeeds without unresolved imports or duplicate route names.

- [ ] **Step 4: Run E2E smoke tests**

Add automated E2E checks (Playwright or Cypress) covering:

```text
1. / → renders three creation cards and continue-working section
2. /canvas → redirects to /canvas-projects
3. /canvas-projects → filterable list, empty state when no canvases
4. /canvas-projects → create canvas flow submits with all ownership fields
5. /canvas-projects → create official canvas with admission failure shows remediation
6. /script-gen/{id}/workspace?tab=canvas → only that project's canvases
7. /canvas/{uuid} → editor loads with breadcrumb and source context
8. /canvas/{uuid} → upstream change banner appears when diff non-empty
9. Cross-user canvas URL returns error, not editor content
```

Store E2E specs in `aicp-frontend/e2e/`.

- [ ] **Step 5: Generate API documentation**

Document all new endpoints with request/response examples in the project API docs. Endpoints to document:

```text
GET    /api/v1/canvas/projects
GET    /api/v1/canvas/projects/{id}
POST   /api/v1/canvas/projects
GET    /api/v1/canvas/production-admission
GET    /api/v1/canvas/projects/{id}/source-diff
POST   /api/v1/canvas/projects/{id}/copy
POST   /api/v1/canvas/projects/{id}/move
POST   /api/v1/canvas/projects/{id}/archive
POST   /api/v1/canvas/projects/{id}/restore
DELETE /api/v1/canvas/projects/{id}
GET    /api/v1/content-projects/{projectId}/canvas-projects
GET    /api/v1/home/continue-working
```

- [ ] **Step 6: Perform browser acceptance checks**

Verify these exact paths on the local app:

```text
/ → home with three creation cards
/canvas → redirects to /canvas-projects
/canvas-projects → filterable global center
/script-gen/{id}/workspace?tab=canvas → only that project's canvases
/canvas/{uuid} → the selected editor with source context and breadcrumb
```

Also verify: one sidebar "剧本创作"; no auto-created blank canvas; new canvas cannot submit without ownership; cross-user canvas URLs do not expose existence; upstream version changes do not overwrite an existing snapshot; empty states render per design spec section 11.

- [ ] **Step 7: Record the verification result**

Run: `git status --short`

Expected: no new uncommitted files from verification. If a command fails, return to the task that owns the failing behavior, add a focused failing test there, and make a separate commit that stages only the exact files changed for that fix.
