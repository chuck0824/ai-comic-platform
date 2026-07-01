# Script Creation and Warehouse Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate the script creation launchpad from the all-project warehouse, unify both on `content_projects`, and enforce version-bound content, production, commercial, and lifecycle transitions.

**Architecture:** Keep `content_projects` as the aggregate root. Add a project status projection that exposes the approved three-axis vocabulary without discarding the existing detailed production states, place all state changes behind a lifecycle service, and make the Vue pages consume only content-project APIs. Legacy `scripts` remain an idempotent compatibility source and never become a second frontend list.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, H2/MySQL, JUnit 5, Mockito, Vue 3, Vue Router, Element Plus, Node test runner, Vite.

---

## 0. Execution Preconditions and File Map

The current worktree contains unrelated user changes and an uncommitted warehouse draft. Before execution, run `git status --short` and preserve all unrelated changes. Do not stage generated assets or files outside the task being committed.

The current draft files `aicp-frontend/src/views/Warehouse.vue`, `aicp-frontend/src/views/warehouse/ScriptCard.vue`, and `aicp-frontend/src/views/warehouse/scriptWarehouseViewModel.js` use `/script/repo/scripts`; they must be converted rather than extended as a second warehouse implementation. The uncommitted changes in `ScriptRepoController.java` and `ScriptService.java` are not the basis of the new warehouse query path.

### Backend ownership

| File | Responsibility |
|---|---|
| `domain/ContentProjectEnums.java` | Canonical content, lifecycle, public production, and public commercial values |
| `entity/ContentProject.java` | Persistent project aggregate fields |
| `entity/ProjectAuditLog.java` | Immutable project action audit row |
| `mapper/ProjectAuditLogMapper.java` | Audit persistence |
| `dto/ContentProjectRequests.java` | Query and action request contracts |
| `dto/ContentProjectViews.java` | Warehouse, launchpad, detail, status, and next-action responses |
| `service/ProjectStatusProjection.java` | Pure mapping from stored detailed state to three public axes and one primary action |
| `service/ContentProjectService.java` | Search, pagination, recent projects, todos, detail summary, rename, duplicate |
| `service/ProjectLifecycleService.java` | Submit, approve, revise, lock, archive, and restore transitions |
| `service/LegacyProjectProjectionService.java` | Idempotent old-script resolution and migration |
| `controller/ContentProjectController.java` | HTTP query and action endpoints |

### Frontend ownership

| File | Responsibility |
|---|---|
| `views/content-project/ScriptCreationHome.vue` | Creation methods, recent projects, todos, failed jobs |
| `views/Warehouse.vue` | Warehouse filters, pagination, archive/restore actions |
| `views/warehouse/ProjectCard.vue` | Three-axis summary and explicit actions |
| `views/warehouse/projectWarehouseViewModel.js` | Pure query, labels, and primary-action routing |
| `views/content-project/ContentProjectDetail.vue` | Project hub and downstream links |
| `api/contentProject.js` | Single frontend API surface |
| `router/index.js` | New page routes and legacy redirects |
| `components/Sidebar.vue` | Correct active-navigation ownership |

## Task 1: Add the Canonical Lifecycle Schema and Status Projection

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V3__content_project_lifecycle.sql`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectStatusProjection.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/domain/ContentProjectEnums.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ContentProject.java`
- Modify: `aicp-backend/src/main/resources/db/schema.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ProjectStatusProjectionTest.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/schema/ContentProjectSchemaTest.java`

- [ ] **Step 1: Write failing projection tests**

```java
@Test
void mapsDetailedStatesToPublicAxesAndOnePrimaryAction() {
    ContentProject project = new ContentProject();
    project.setContentStatus("locked");
    project.setStoryboardIntentStatus("requested");
    project.setProductionStatus("not_started");
    project.setMarketStatus("private");
    project.setLifecycleStatus("active");

    ProjectStatusProjection.StatusView result = ProjectStatusProjection.from(project);

    assertThat(result.productionStatus()).isEqualTo("storyboarding");
    assertThat(result.commercialStatus()).isEqualTo("not_listed");
    assertThat(result.primaryAction()).isEqualTo("view_storyboard");
}

@Test
void archivedProjectAlwaysUsesRestoreAsPrimaryAction() {
    ContentProject project = new ContentProject();
    project.setContentStatus("draft");
    project.setProductionStatus("not_started");
    project.setMarketStatus("private");
    project.setLifecycleStatus("archived");

    assertThat(ProjectStatusProjection.from(project).primaryAction()).isEqualTo("restore");
}
```

- [ ] **Step 2: Run the tests and verify the missing type failure**

Run:

```bash
cd aicp-backend
mvn -Dtest=ProjectStatusProjectionTest,ContentProjectSchemaTest test
```

Expected: compilation fails because `ProjectStatusProjection` and `lifecycle_status` do not exist.

- [ ] **Step 3: Add the lifecycle field and public projection**

Add to `ContentProjectEnums.java`:

```java
public enum LifecycleStatus {
    ACTIVE, ARCHIVED;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
```

Add `private String lifecycleStatus;` to `ContentProject.java`. Preserve `isDeleted` for the existing MyBatis soft-delete contract.

Create `ProjectStatusProjection.java` with this public contract:

```java
public final class ProjectStatusProjection {
    public record StatusView(
            String contentStatus,
            String productionStatus,
            String commercialStatus,
            String lifecycleStatus,
            String primaryAction,
            String blockedReason) {}

    public static StatusView from(ContentProject p) {
        String lifecycle = valueOr(p.getLifecycleStatus(), "active");
        if ("archived".equals(lifecycle)) {
            return new StatusView(p.getContentStatus(), publicProduction(p),
                    publicCommercial(p.getMarketStatus()), lifecycle, "restore", null);
        }
        String action = switch (valueOr(p.getContentStatus(), "draft")) {
            case "reviewing" -> "view_review";
            case "needs_revision" -> "resolve_review";
            case "approved" -> "lock_version";
            case "locked" -> lockedAction(p);
            default -> "continue_creation";
        };
        return new StatusView(p.getContentStatus(), publicProduction(p),
                publicCommercial(p.getMarketStatus()), lifecycle, action, null);
    }

    private static String publicProduction(ContentProject p) {
        if ("requested".equals(p.getStoryboardIntentStatus())
                || "in_progress".equals(p.getStoryboardIntentStatus())) return "storyboarding";
        return switch (valueOr(p.getProductionStatus(), "not_started")) {
            case "preflight", "canvas_ready", "generating", "quality_review" -> "canvas_producing";
            case "deliverable" -> "completed";
            default -> "not_started";
        };
    }

    private static String publicCommercial(String stored) {
        return switch (valueOr(stored, "private")) {
            case "pending_review" -> "listing_review";
            case "listed", "sold" -> "listed";
            case "delisted" -> "delisted";
            default -> "not_listed";
        };
    }

    private static String lockedAction(ContentProject p) {
        return switch (publicProduction(p)) {
            case "storyboarding", "canvas_producing" -> "view_production";
            case "completed" -> "view_result";
            default -> "create_storyboard";
        };
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private ProjectStatusProjection() {}
}
```

- [ ] **Step 4: Add the database migration and schema mirrors**

`V3__content_project_lifecycle.sql` must be executable on the configured production database:

```sql
ALTER TABLE content_projects
    ADD COLUMN lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'active';

CREATE INDEX idx_cp_owner_lifecycle_updated
    ON content_projects(owner_user_id, lifecycle_status, updated_at);

CREATE UNIQUE INDEX uk_cp_legacy_script
    ON content_projects(legacy_script_id);
```

Add the same column and indexes to all three canonical schema files. Extend `ContentProjectSchemaTest` to assert the column and unique legacy mapping index.

- [ ] **Step 5: Run focused tests and commit**

Run:

```bash
cd aicp-backend
mvn -Dtest=ProjectStatusProjectionTest,ContentProjectSchemaTest test
```

Expected: all focused tests pass.

Commit:

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject/domain/ContentProjectEnums.java \
  aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ContentProject.java \
  aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectStatusProjection.java \
  aicp-backend/src/main/resources/db \
  aicp-backend/src/test/java/com/aicp/module/contentproject
git commit -m "feat: add content project lifecycle projection"
```

## Task 2: Implement Warehouse, Recent, Todo, and Detail Query Contracts

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ContentProjectRequests.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ContentProjectViews.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ContentProjectService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ContentProjectServiceTest.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/ContentProjectM1IntegrationTest.java`

- [ ] **Step 1: Write failing service tests for owner scoping and filters**

```java
@Test
void warehouseQueryIsOwnerScopedAndExcludesArchivedByDefault() {
    ProjectQuery query = new ProjectQuery(1, 20, "账本", "short_drama", null,
            "needs_revision", null, null, null, "updated_desc");

    service.list(7L, query);

    verify(projectMapper).selectPage(any(), argThat(wrapper -> {
        String sql = wrapper.getSqlSegment();
        return sql.contains("name") && sql.contains("content_status")
                && sql.contains("lifecycle_status");
    }));
}
```

Add integration assertions that `recent` returns at most five active projects and `todos` contains only `reviewing`, `needs_revision`, or failed/running generation work owned by the current user.

- [ ] **Step 2: Run the focused tests and verify contract failures**

Run:

```bash
cd aicp-backend
mvn -Dtest=ContentProjectServiceTest,ContentProjectM1IntegrationTest test
```

Expected: compilation fails because `ProjectQuery`, `WarehouseProjectView`, and the new service methods do not exist.

- [ ] **Step 3: Define explicit request and response records**

Add these records using the existing snake-case Jackson convention:

```java
record ProjectQuery(
        int page,
        int pageSize,
        String keyword,
        String creationMode,
        String sourceMode,
        String contentStatus,
        String productionStatus,
        String commercialStatus,
        String lifecycleStatus,
        String sort) {}

record WarehouseProjectView(
        Long id,
        String uuid,
        String name,
        String creationMode,
        String sourceMode,
        String contentStatus,
        String productionStatus,
        String commercialStatus,
        String lifecycleStatus,
        String lastStageKey,
        Long adoptedVersionId,
        String primaryAction,
        String blockedReason,
        boolean migrationIssue,
        Integer revision,
        LocalDateTime updatedAt) {}

record ProjectTodoView(Long projectId, String projectName, String type,
                       String label, String route, LocalDateTime updatedAt) {}

record ProjectHubView(ProjectDetail project, WarehouseProjectView summary,
                      List<ContentVersionView> versions,
                      Map<String, Long> relationCounts) {}

record WarehouseProjectListResult(List<WarehouseProjectView> items,
                                  int page, int pageSize, long total) {}
```

Use `market_status` only as an internal persistence name; all new response records expose `commercial_status`.

- [ ] **Step 4: Implement query methods and endpoints**

Add service methods:

```java
public WarehouseProjectListResult list(Long userId, ProjectQuery query)
public List<WarehouseProjectView> recent(Long userId, int limit)
public List<ProjectTodoView> todos(Long userId)
public ProjectHubView hub(Long userId, Long projectId)
```

Build MyBatis predicates only from validated allowlisted fields. Clamp `pageSize` to `1..100`; map sort values with a switch and default to `updated_desc`. Map every result through `ProjectStatusProjection.from(project)`.

Expose:

```java
@GetMapping
public ApiResponse<PageResult<WarehouseProjectView>> list(...)

@GetMapping("/recent")
public ApiResponse<List<WarehouseProjectView>> recent(
        @RequestParam(defaultValue = "5") int limit)

@GetMapping("/todos")
public ApiResponse<List<ProjectTodoView>> todos()

@GetMapping("/{id}/summary")
public ApiResponse<ProjectHubView> summary(@PathVariable Long id)
```

The `/{id}` route stays as the base project detail endpoint; `/{id}/summary` is the warehouse hub aggregate.

- [ ] **Step 5: Run service and integration tests, then commit**

Run:

```bash
cd aicp-backend
mvn -Dtest=ContentProjectServiceTest,ContentProjectM1IntegrationTest test
```

Expected: all query contract tests pass.

Commit:

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject/{controller,dto,service} \
  aicp-backend/src/test/java/com/aicp/module/contentproject
git commit -m "feat: add content project warehouse queries"
```

## Task 3: Put Review, Lock, Archive, Restore, and Duplicate Behind Actions

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/entity/ProjectAuditLog.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/mapper/ProjectAuditLogMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectLifecycleService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectProductionGate.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ContentProjectRequests.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentStoryboardController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ProductionController.java`
- Modify: `aicp-backend/src/main/resources/db/migration/V3__content_project_lifecycle.sql`
- Modify: `aicp-backend/src/main/resources/db/schema.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ProjectLifecycleServiceTest.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/ProjectProductionGateTest.java`

- [ ] **Step 1: Write the state-machine tests first**

```java
@Test
void submitReviewBindsAnOwnedDraftVersion() {
    when(projectMapper.selectById(1L)).thenReturn(project("draft", "active"));
    when(versionMapper.selectById(9L)).thenReturn(version(9L, 1L, "draft"));

    service.submitReview(7L, 1L, new VersionActionRequest(9L, 3, "idem-1", null));

    verify(projectMapper).updateById(argThat(p -> "reviewing".equals(p.getContentStatus())));
    verify(auditMapper).insert(argThat(a -> a.getTargetVersionId().equals(9L)
            && "submit_review".equals(a.getActionType())));
}

@Test
void cannotLockAProjectThatIsNotApproved() {
    when(projectMapper.selectById(1L)).thenReturn(project("reviewing", "active"));

    assertThatThrownBy(() -> service.lock(7L, 1L,
            new VersionActionRequest(9L, 3, "idem-2", null)))
            .isInstanceOf(BizException.class);
}

@Test
void listedProjectCannotBeArchived() {
    ContentProject p = project("locked", "active");
    p.setMarketStatus("listed");
    when(projectMapper.selectById(1L)).thenReturn(p);

    assertThatThrownBy(() -> service.archive(7L, 1L,
            new ProjectActionRequest(3, "idem-3", null)))
            .isInstanceOf(BizException.class);
}

@Test
void onlyArchivedAndUnlistedProjectCanMoveToTrash() {
    ContentProject p = project("locked", "archived");
    p.setMarketStatus("private");
    when(projectMapper.selectById(1L)).thenReturn(p);

    service.moveToTrash(7L, 1L, new ProjectActionRequest(3, "idem-4", null));

    verify(projectMapper).updateById(argThat(project -> project.getIsDeleted() == 1));
}
```

- [ ] **Step 2: Run the test and verify it fails because the lifecycle service is absent**

Run:

```bash
cd aicp-backend
mvn -Dtest=ProjectLifecycleServiceTest test
```

Expected: compilation fails for the missing service, requests, entity, and mapper.

- [ ] **Step 3: Add action requests and the immutable audit table**

```java
record VersionActionRequest(
        Long versionId,
        Integer revision,
        @NotBlank String idempotencyKey,
        @Size(max = 2000) String comment) {}

record ProjectActionRequest(
        Integer revision,
        @NotBlank String idempotencyKey,
        @Size(max = 2000) String comment) {}
```

Add this table to V3 and all schema mirrors:

```sql
CREATE TABLE project_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_version_id BIGINT,
    before_status VARCHAR(50),
    after_status VARCHAR(50),
    comment VARCHAR(2000),
    idempotency_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_audit_idempotency UNIQUE (project_id, idempotency_key)
);
```

- [ ] **Step 4: Implement transitions with optimistic locking**

`ProjectLifecycleService` must expose:

```java
ProjectDetail submitReview(Long userId, Long projectId, VersionActionRequest request)
ProjectDetail approve(Long userId, Long projectId, VersionActionRequest request)
ProjectDetail requestRevision(Long userId, Long projectId, VersionActionRequest request)
ProjectDetail lock(Long userId, Long projectId, VersionActionRequest request)
ProjectDetail archive(Long userId, Long projectId, ProjectActionRequest request)
ProjectDetail restore(Long userId, Long projectId, ProjectActionRequest request)
void moveToTrash(Long userId, Long projectId, ProjectActionRequest request)
ProjectDetail duplicate(Long userId, Long projectId, ProjectActionRequest request)
```

Each method must:

1. call `ProjectAccessService.require` with `EDIT_CONTENT`, `REVIEW`, `PRODUCE`, or `DELETE_PROJECT` as appropriate;
2. load and validate the target version belongs to the project when a version is required;
3. reject invalid source states;
4. update with `WHERE id = ? AND revision = ?`;
5. insert one audit row in the same transaction;
6. return the updated project view;
7. return the prior result for a repeated idempotency key.

The content version changes in the same transaction as the project: submit sets both to `reviewing`, approve sets both to `approved`, revision request sets both to `needs_revision`, and lock sets both to `locked`. Editing after approval or lock creates a new draft and does not mutate the adopted version.

Do not add a generic `updateStatus` method.

Add `ProjectProductionGate.requireLockedVersion(projectId, versionId)`. It must load the version, verify it belongs to the project, and require both the project and version to be `locked`. Call this gate before creating a formal storyboard and before creating a canvas snapshot in `ContentStoryboardController` and `ProductionController`. The project detail commercial tab uses the same returned blocked reason; the existing mock trade market is not expanded in this plan.

- [ ] **Step 5: Expose action endpoints**

```java
@PostMapping("/{id}/submit-review")
public ApiResponse<ProjectDetail> submitReview(@PathVariable Long id,
        @Valid @RequestBody VersionActionRequest request)

@PostMapping("/{id}/approve")
public ApiResponse<ProjectDetail> approve(@PathVariable Long id,
        @Valid @RequestBody VersionActionRequest request)

@PostMapping("/{id}/request-revision")
public ApiResponse<ProjectDetail> requestRevision(@PathVariable Long id,
        @Valid @RequestBody VersionActionRequest request)

@PostMapping("/{id}/lock")
public ApiResponse<ProjectDetail> lock(@PathVariable Long id,
        @Valid @RequestBody VersionActionRequest request)

@PostMapping("/{id}/archive")
public ApiResponse<ProjectDetail> archive(@PathVariable Long id,
        @Valid @RequestBody ProjectActionRequest request)

@PostMapping("/{id}/restore")
public ApiResponse<ProjectDetail> restore(@PathVariable Long id,
        @Valid @RequestBody ProjectActionRequest request)

@PostMapping("/{id}/duplicate")
public ApiResponse<ProjectDetail> duplicate(@PathVariable Long id,
        @Valid @RequestBody ProjectActionRequest request)

@PostMapping("/{id}/trash")
public ApiResponse<Void> moveToTrash(@PathVariable Long id,
        @Valid @RequestBody ProjectActionRequest request)
```

- [ ] **Step 6: Run tests and commit**

Run:

```bash
cd aicp-backend
mvn -Dtest=ProjectLifecycleServiceTest,ProjectProductionGateTest,ProjectAccessServiceTest test
```

Expected: all lifecycle and permission tests pass.

Commit:

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject \
  aicp-backend/src/main/resources/db \
  aicp-backend/src/test/java/com/aicp/module/contentproject/service/ProjectLifecycleServiceTest.java
git commit -m "feat: enforce content project lifecycle actions"
```

## Task 4: Make Legacy Script Mapping Idempotent and Frontend-Transparent

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/LegacyProjectProjectionService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ContentProjectController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/script/controller/ScriptRepoController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/script/service/ScriptService.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/service/LegacyProjectProjectionServiceTest.java`
- Test: `aicp-backend/src/test/java/com/aicp/module/contentproject/ContentProjectM1IntegrationTest.java`

- [ ] **Step 1: Write failing idempotency and ownership tests**

```java
@Test
void resolveOrCreateReturnsTheExistingProjectForTheSameLegacyScript() {
    ContentProject existing = new ContentProject();
    existing.setId(55L);
    existing.setLegacyScriptId(8L);
    when(projectMapper.selectOne(any())).thenReturn(existing);

    assertThat(service.resolveOrCreate(7L, 8L).getId()).isEqualTo(55L);
    verify(projectMapper, never()).insert(any());
}

@Test
void resolveOrCreateRejectsAnotherOwnersScript() {
    Script foreign = new Script();
    foreign.setId(8L);
    foreign.setOwnerUserId(99L);
    when(scriptMapper.selectById(8L)).thenReturn(foreign);

    assertThatThrownBy(() -> service.resolveOrCreate(7L, 8L))
            .isInstanceOf(BizException.class);
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
cd aicp-backend
mvn -Dtest=LegacyProjectProjectionServiceTest test
```

Expected: compilation fails because `resolveOrCreate` does not exist.

- [ ] **Step 3: Implement one resolver for backfill and legacy routes**

Add:

```java
@Transactional
public ContentProject resolveOrCreate(Long ownerId, Long scriptId) {
    ContentProject existing = projectMapper.selectOne(
            new LambdaQueryWrapper<ContentProject>()
                    .eq(ContentProject::getLegacyScriptId, scriptId));
    if (existing != null) return existing;

    Script script = scriptMapper.selectById(scriptId);
    if (script == null || !ownerId.equals(script.getOwnerUserId())) {
        throw new BizException(ErrorCode.PROJECT_NOT_FOUND);
    }
    return migrateOne(ownerId, script);
}
```

Refactor `backfill` to call the same `migrateOne` path. Normalize unsupported old statuses to `draft`; map `pending_review` to `reviewing`; preserve old values in an audit/comment field rather than writing invalid content states.

Expose:

```java
@GetMapping("/legacy-scripts/{scriptId}/resolve")
public ApiResponse<Map<String, Long>> resolveLegacy(@PathVariable Long scriptId) {
    ContentProject project = legacy.resolveOrCreate(
            SecurityUtil.requireCurrentUserId(), scriptId);
    return ApiResponse.success(Map.of("project_id", project.getId()));
}
```

Keep old script CRUD endpoints only for external compatibility. Remove any new warehouse behavior that depends on expanding `/script/repo/scripts`; new frontend code must not call it.

- [ ] **Step 4: Run migration tests and commit**

Run:

```bash
cd aicp-backend
mvn -Dtest=LegacyProjectProjectionServiceTest,ContentProjectM1IntegrationTest test
```

Expected: repeated resolution returns one project and foreign scripts are rejected.

Commit:

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject \
  aicp-backend/src/main/java/com/aicp/module/script \
  aicp-backend/src/test/java/com/aicp/module/contentproject
git commit -m "refactor: unify legacy scripts under content projects"
```

## Task 5: Add Frontend API and Pure Navigation Contracts

**Files:**
- Modify: `aicp-frontend/src/api/contentProject.js`
- Create: `aicp-frontend/src/views/warehouse/projectWarehouseViewModel.js`
- Delete: `aicp-frontend/src/views/warehouse/scriptWarehouseViewModel.js`
- Test: `aicp-frontend/tests/project-warehouse.test.js`

- [ ] **Step 1: Write failing pure-function tests**

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'
import { buildWarehouseQuery, primaryActionRoute } from '../src/views/warehouse/projectWarehouseViewModel.js'

test('serializes all three axes and omits empty values', () => {
  assert.deepEqual(buildWarehouseQuery({
    page: 2,
    pageSize: 20,
    keyword: '账本',
    contentStatus: 'needs_revision',
    productionStatus: '',
    commercialStatus: 'not_listed',
    lifecycleStatus: 'active',
    sort: 'updated_desc'
  }), {
    page: 2,
    page_size: 20,
    keyword: '账本',
    content_status: 'needs_revision',
    commercial_status: 'not_listed',
    lifecycle_status: 'active',
    sort: 'updated_desc'
  })
})

test('routes creation and production actions explicitly', () => {
  assert.equal(primaryActionRoute({ id: 3, primary_action: 'continue_creation' }), '/script-gen/3/workspace')
  assert.equal(primaryActionRoute({ id: 3, primary_action: 'view_review' }), '/warehouse/3?tab=review')
  assert.equal(primaryActionRoute({ id: 3, primary_action: 'create_storyboard' }), '/warehouse/3?tab=storyboard')
})
```

- [ ] **Step 2: Run tests and verify module-not-found failure**

Run:

```bash
cd aicp-frontend
node --test tests/project-warehouse.test.js
```

Expected: failure because `projectWarehouseViewModel.js` does not exist.

- [ ] **Step 3: Add the content-project API methods**

```javascript
list: params => request.get('/content-projects', { params }),
recent: (limit = 5) => request.get('/content-projects/recent', { params: { limit } }),
todos: () => request.get('/content-projects/todos'),
summary: id => request.get(`/content-projects/${id}/summary`),
resolveLegacy: scriptId => request.get(`/content-projects/legacy-scripts/${scriptId}/resolve`),
submitReview: (id, data) => request.post(`/content-projects/${id}/submit-review`, data),
approve: (id, data) => request.post(`/content-projects/${id}/approve`, data),
requestRevision: (id, data) => request.post(`/content-projects/${id}/request-revision`, data),
lock: (id, data) => request.post(`/content-projects/${id}/lock`, data),
archive: (id, data) => request.post(`/content-projects/${id}/archive`, data),
restore: (id, data) => request.post(`/content-projects/${id}/restore`, data),
duplicate: (id, data) => request.post(`/content-projects/${id}/duplicate`, data),
moveToTrash: (id, data) => request.post(`/content-projects/${id}/trash`, data)
```

- [ ] **Step 4: Implement the pure warehouse view model**

Export fixed label maps for content, production, commercial, and lifecycle statuses. Implement `buildWarehouseQuery` by copying only non-empty allowlisted fields. Implement `primaryActionRoute` with an exhaustive switch; unknown actions fall back to `/warehouse/{id}` rather than guessing.

```javascript
export function buildWarehouseQuery(filters = {}) {
  const mapping = {
    page: 'page', pageSize: 'page_size', keyword: 'keyword',
    creationMode: 'creation_mode', sourceMode: 'source_mode',
    contentStatus: 'content_status', productionStatus: 'production_status',
    commercialStatus: 'commercial_status', lifecycleStatus: 'lifecycle_status',
    sort: 'sort'
  }
  return Object.entries(mapping).reduce((query, [source, target]) => {
    const value = filters[source]
    if (value !== undefined && value !== null && value !== '') query[target] = value
    return query
  }, {})
}

export function primaryActionRoute(project) {
  switch (project.primary_action) {
    case 'continue_creation': return `/script-gen/${project.id}/workspace`
    case 'view_review':
    case 'resolve_review':
    case 'lock_version': return `/warehouse/${project.id}?tab=review`
    case 'create_storyboard': return `/warehouse/${project.id}?tab=storyboard`
    case 'view_production': return `/warehouse/${project.id}?tab=production`
    case 'view_result': return `/warehouse/${project.id}?tab=production`
    default: return `/warehouse/${project.id}`
  }
}
```

- [ ] **Step 5: Run tests and commit**

Run:

```bash
cd aicp-frontend
node --test tests/project-warehouse.test.js
```

Expected: all tests pass.

Commit:

```bash
git add aicp-frontend/src/api/contentProject.js \
  aicp-frontend/src/views/warehouse \
  aicp-frontend/tests/project-warehouse.test.js
git commit -m "feat: add content project warehouse client contracts"
```

## Task 6: Replace the Project List at `/script-gen` with a Creation Launchpad

**Files:**
- Create: `aicp-frontend/src/views/content-project/ScriptCreationHome.vue`
- Create: `aicp-frontend/src/views/content-project/scriptCreationHomeViewModel.js`
- Modify: `aicp-frontend/src/router/index.js`
- Test: `aicp-frontend/tests/script-creation-home.test.js`
- Test: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Write failing launchpad and route contract tests**

```javascript
test('launchpad exposes four creation methods and limits recent work', () => {
  const vm = createLaunchpadViewModel({ recent: [1, 2, 3, 4, 5, 6], todos: [] })
  assert.deepEqual(vm.methods.map(item => item.key), ['quick', 'professional', 'upload', 'tvc'])
  assert.equal(vm.recent.length, 5)
})

test('script-gen route loads the creation home instead of the project list', () => {
  const router = fs.readFileSync(routerPath, 'utf8')
  assert.match(router, /path:\s*['"]script-gen['"][\s\S]*ScriptCreationHome\.vue/)
})
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
cd aicp-frontend
node --test tests/script-creation-home.test.js tests/navigation-contract.test.js
```

Expected: failures because the launchpad and route do not exist.

- [ ] **Step 3: Implement the pure launchpad model**

```javascript
export const CREATION_METHODS = [
  { key: 'quick', label: 'AI 快速创作', route: '/script-gen/new?mode=quick' },
  { key: 'professional', label: 'AI 专业创作', route: '/script-gen/new?mode=professional' },
  { key: 'upload', label: '上传已有文稿', route: '/script-gen/new?mode=upload' },
  { key: 'tvc', label: 'TVC 创作', route: '/script-gen/new?mode=tvc' }
]

export function createLaunchpadViewModel({ recent = [], todos = [] }) {
  return { methods: CREATION_METHODS, recent: recent.slice(0, 5), todos }
}
```

- [ ] **Step 4: Implement `ScriptCreationHome.vue`**

On mount, request `recent(5)` and `todos()` in parallel. Render four creation cards, up to five recent projects, and grouped todos. A recent card has only “继续创作” and “查看详情”; it must not expose archive, delete, or commercial actions. Show independent loading, empty, and retry states so a todo failure does not hide creation methods.

Update `/script-gen` to load `ScriptCreationHome.vue`. Leave `/script-gen/new` and `/script-gen/:projectId/workspace` unchanged.

- [ ] **Step 5: Run tests and build, then commit**

Run:

```bash
cd aicp-frontend
node --test tests/script-creation-home.test.js tests/navigation-contract.test.js
npm run build
```

Expected: tests pass and Vite builds successfully.

Commit:

```bash
git add aicp-frontend/src/views/content-project/ScriptCreationHome.vue \
  aicp-frontend/src/views/content-project/scriptCreationHomeViewModel.js \
  aicp-frontend/src/router/index.js \
  aicp-frontend/tests
git commit -m "feat: add script creation launchpad"
```

## Task 7: Convert the Warehouse Draft to the Unified Project List

**Files:**
- Modify: `aicp-frontend/src/views/Warehouse.vue`
- Create: `aicp-frontend/src/views/warehouse/ProjectCard.vue`
- Delete: `aicp-frontend/src/views/warehouse/ScriptCard.vue`
- Test: `aicp-frontend/tests/project-warehouse.test.js`

- [ ] **Step 1: Extend failing tests for card actions and labels**

```javascript
test('locked project shows production action without exposing commerce as primary', () => {
  const project = {
    id: 8,
    content_status: 'locked',
    production_status: 'not_started',
    commercial_status: 'not_listed',
    primary_action: 'create_storyboard'
  }
  const vm = projectCardViewModel(project)
  assert.equal(vm.primaryLabel, '制作分镜')
  assert.equal(vm.primaryRoute, '/warehouse/8?tab=storyboard')
  assert.equal(vm.statuses.length, 3)
})
```

- [ ] **Step 2: Run the test and verify missing export failure**

Run:

```bash
cd aicp-frontend
node --test tests/project-warehouse.test.js
```

Expected: failure because `projectCardViewModel` is not implemented.

- [ ] **Step 3: Implement `ProjectCard.vue` and finish the pure mapper**

The card must display project name, creation mode, source mode, content status, production status, commercial status, and update time. Clicking the card emits `open-detail`; the visible primary button emits `primary`; the menu contains rename, duplicate, export, archive/restore, and “移入回收站” only for archived, unlisted projects.

Do not carry forward old status checks such as `sold`, `purchased`, or a direct “生成漫剧” button. Those belong to commercial/detail views, not the warehouse primary flow.

Add this mapper to `projectWarehouseViewModel.js`:

```javascript
const PRIMARY_LABELS = {
  continue_creation: '继续创作', view_review: '查看审核进度',
  resolve_review: '处理审核意见', lock_version: '确认锁稿',
  create_storyboard: '制作分镜', view_production: '查看生产进度',
  view_result: '查看成果', restore: '恢复项目'
}

export function projectCardViewModel(project) {
  return {
    statuses: [
      { axis: 'content', value: project.content_status },
      { axis: 'production', value: project.production_status },
      { axis: 'commercial', value: project.commercial_status }
    ],
    primaryLabel: PRIMARY_LABELS[project.primary_action] || '查看详情',
    primaryRoute: primaryActionRoute(project),
    archived: project.lifecycle_status === 'archived'
  }
}
```

- [ ] **Step 4: Convert `Warehouse.vue` to `contentProjectApi`**

Use `buildWarehouseQuery` and `contentProjectApi.list`. Filters must include all three axes, lifecycle, creation mode, source mode, keyword, and sort. Default lifecycle is `active`; the “已归档” tab sends `archived`.

Interaction rules:

```javascript
function openDetail(project) {
  router.push(`/warehouse/${project.id}`)
}

function runPrimary(project) {
  router.push(primaryActionRoute(project))
}

async function archiveProject(project) {
  await contentProjectApi.archive(project.id, {
    revision: project.revision,
    idempotency_key: crypto.randomUUID(),
    comment: 'warehouse archive'
  })
  await search()
}
```

Use the existing confirmation component before archive, restore, or move-to-trash. Moving to trash calls `contentProjectApi.moveToTrash`; there is no physical-delete endpoint.

- [ ] **Step 5: Run tests and build, then commit**

Run:

```bash
cd aicp-frontend
node --test tests/project-warehouse.test.js
npm run build
```

Expected: tests pass and Vite builds without imports from `scriptApi` in warehouse files.

Commit:

```bash
git add aicp-frontend/src/views/Warehouse.vue aicp-frontend/src/views/warehouse \
  aicp-frontend/tests/project-warehouse.test.js
git commit -m "feat: make warehouse manage content projects"
```

## Task 8: Add the Project Hub and Correct Legacy Navigation

**Files:**
- Create: `aicp-frontend/src/views/content-project/ContentProjectDetail.vue`
- Create: `aicp-frontend/src/views/content-project/projectDetailViewModel.js`
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/src/components/Sidebar.vue`
- Modify: `aicp-frontend/src/views/TagEditor.vue`
- Test: `aicp-frontend/tests/project-detail.test.js`
- Test: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Write failing detail and legacy-route tests**

```javascript
test('detail keeps one primary action and seven stable tabs', () => {
  const vm = projectDetailViewModel({
    project: { id: 4 },
    summary: { primary_action: 'continue_creation' }
  })
  assert.equal(vm.primary.route, '/script-gen/4/workspace')
  assert.deepEqual(vm.tabs.map(tab => tab.key),
    ['overview', 'versions', 'settings', 'review', 'storyboard', 'production', 'commerce'])
})

test('warehouse detail route exists and legacy route resolves instead of rendering ScriptGen', () => {
  const router = fs.readFileSync(routerPath, 'utf8')
  assert.match(router, /path:\s*['"]warehouse\/:projectId['"]/)
  assert.doesNotMatch(router, /path:\s*['"]script-gen-legacy['"][\s\S]{0,180}views\/ScriptGen\.vue/)
})
```

- [ ] **Step 2: Run tests and verify failures**

Run:

```bash
cd aicp-frontend
node --test tests/project-detail.test.js tests/navigation-contract.test.js
```

Expected: failures for the missing detail model and old legacy route.

- [ ] **Step 3: Implement the detail view model and page**

Create seven fixed tabs. Derive exactly one primary action from `summary.primary_action`; keep commercial actions inside the commerce tab. Load `contentProjectApi.summary(projectId)` and display current adopted version, three axes, lifecycle, relation counts, blocked reason, and the creation-to-delivery timeline.

For a disabled action, render the server-provided `blocked_reason` next to the control. Do not infer missing prerequisites solely in Vue.

Implement the pure page model as:

```javascript
import { primaryActionRoute } from '../warehouse/projectWarehouseViewModel.js'

export const PROJECT_DETAIL_TABS = [
  ['overview', '概览'], ['versions', '正文与版本'], ['settings', '设定资料'],
  ['review', '审核记录'], ['storyboard', '分镜'], ['production', '生产关联'],
  ['commerce', '商业记录']
].map(([key, label]) => ({ key, label }))

export function projectDetailViewModel({ project, summary }) {
  return {
    project,
    summary,
    tabs: PROJECT_DETAIL_TABS,
    primary: {
      action: summary.primary_action,
      route: primaryActionRoute({ id: project.id, primary_action: summary.primary_action }),
      disabled: Boolean(summary.blocked_reason),
      blockedReason: summary.blocked_reason || ''
    }
  }
}
```

- [ ] **Step 4: Add routes and compatibility redirects**

Add:

```javascript
{
  path: 'warehouse/:projectId',
  name: 'WarehouseProjectDetail',
  component: () => import('@/views/content-project/ContentProjectDetail.vue'),
  meta: { title: '剧本详情' }
}
```

Replace `/script-gen-legacy` with a redirect to `/script-gen`. In `TagEditor.vue`, when entered with `route.params.scriptId`, call `resolveLegacy`, then `router.replace(`/script-gen/${projectId}/edit/tags`)`; do not maintain a second data path after resolution.

Update Sidebar active matching so `/script-gen/**` activates “剧本创作” and `/warehouse/**` activates “剧本仓库”; remove `/content-projects` from the creation entry condition.

- [ ] **Step 5: Run tests and build, then commit**

Run:

```bash
cd aicp-frontend
node --test tests/project-detail.test.js tests/navigation-contract.test.js
npm run build
```

Expected: tests pass and all new route chunks build.

Commit:

```bash
git add aicp-frontend/src/views/content-project \
  aicp-frontend/src/views/TagEditor.vue \
  aicp-frontend/src/router/index.js \
  aicp-frontend/src/components/Sidebar.vue \
  aicp-frontend/tests
git commit -m "feat: add script project hub and unified navigation"
```

## Task 9: Verify the Full Flow and Deployment Artifact

**Files:**
- Modify: `aicp-frontend/tests/navigation-contract.test.js`
- Create: `aicp-frontend/tests/script-warehouse-flow.spec.js`
- Modify: `aicp-backend/src/test/java/com/aicp/module/contentproject/ContentProjectM1IntegrationTest.java`
- Modify: `aicp-backend/src/test/java/com/aicp/module/contentproject/ContentProjectM2ScaleTest.java`
- Update after successful build only: `aicp-backend/src/main/resources/static/**`

- [ ] **Step 1: Add an integration test for the complete backend lifecycle**

```java
@Test
void projectCanMoveFromCreationToWarehouseAndLockedProductionEntry() {
    CreateProjectRequest request = new CreateProjectRequest(
            "账本迷局", "short_drama", "ai_manual",
            "林夏发现账本被篡改", "追更", "personal", null);
    ProjectDetail created = projectService.create(7L, request);
    ContentUnitView unit = unitService.createUnit(7L, created.id(), "episode", 1, "第一集");
    unitService.saveDraft(7L, unit.id(),
            new SaveDraftRequest(0, "{\"content\":\"正文\"}", "正文"));
    ContentVersionView version = unitService.createVersion(7L, unit.id(),
            new CreateVersionRequest("draft"));

    lifecycle.submitReview(7L, created.id(),
            new VersionActionRequest(version.id(), created.revision(), "flow-1", null));
    lifecycle.approve(7L, created.id(),
            new VersionActionRequest(version.id(), created.revision() + 1, "flow-2", null));
    ProjectDetail locked = lifecycle.lock(7L, created.id(),
            new VersionActionRequest(version.id(), created.revision() + 2, "flow-3", null));

    assertThat(locked.contentStatus()).isEqualTo("locked");
    ProjectQuery query = new ProjectQuery(1, 20, null, null, null,
            null, null, null, "active", "updated_desc");
    assertThat(projectService.list(7L, query).items())
            .extracting(WarehouseProjectView::id)
            .contains(created.id());
}

@Test
void newDraftDoesNotOverwriteStoryboardSourceVersion() {
    ContentProject project = persistedProject(7L, "locked");
    ContentUnit unit = persistedUnit(project.getId());
    ContentVersion locked = persistedVersion(project.getId(), unit.getId(), "locked");
    unit.setCurrentVersionId(locked.getId());
    unitMapper.updateById(unit);
    StoryboardMaster master = persistedStoryboard(project.getId(), unit.getId(), locked.getId());

    DraftView newDraft = unitService.saveDraft(7L, unit.getId(),
            new SaveDraftRequest(unit.getRevision(), "{\"content\":\"修订正文\"}", "修订正文"));

    assertThat(newDraft.id()).isNotEqualTo(locked.getId());
    assertThat(storyboardMapper.selectById(master.getId()).getSourceVersionId())
            .isEqualTo(locked.getId());
}
```

Add these fixture helpers to the integration test and use the real H2 mappers:

```java
private ContentProject persistedProject(Long ownerId, String status) {
    ContentProject p = new ContentProject();
    p.setUuid("CP_" + UUID.randomUUID().toString().replace("-", ""));
    p.setTenantType("personal");
    p.setTenantId(ownerId);
    p.setOwnerUserId(ownerId);
    p.setName("版本隔离测试");
    p.setCreationMode("short_drama");
    p.setSourceMode("ai_manual");
    p.setStoryboardIntentStatus("requested");
    p.setContentStatus(status);
    p.setProductionStatus("not_started");
    p.setMarketStatus("private");
    p.setLifecycleStatus("active");
    p.setRevision(0);
    p.setIsDeleted(0);
    projectMapper.insert(p);
    return p;
}

private ContentUnit persistedUnit(Long projectId) {
    ContentUnit unit = new ContentUnit();
    unit.setStableKey("CU_" + UUID.randomUUID().toString().replace("-", ""));
    unit.setProjectId(projectId);
    unit.setUnitType("episode");
    unit.setDisplayNo(1);
    unit.setTitle("第一集");
    unit.setStatus("locked");
    unit.setRevision(0);
    unit.setIsDeleted(0);
    unitMapper.insert(unit);
    return unit;
}

private ContentVersion persistedVersion(Long projectId, Long unitId, String status) {
    ContentVersion version = new ContentVersion();
    version.setProjectId(projectId);
    version.setContentUnitId(unitId);
    version.setVersionNo(1);
    version.setStatus(status);
    version.setContentJson("{\"content\":\"锁稿正文\"}");
    version.setPlainText("锁稿正文");
    version.setSource("manual_edit");
    version.setContentHash("locked-hash");
    version.setCreatedBy(7L);
    versionMapper.insert(version);
    return version;
}

private StoryboardMaster persistedStoryboard(Long projectId, Long unitId, Long versionId) {
    StoryboardMaster master = new StoryboardMaster();
    master.setUuid("SB_" + UUID.randomUUID().toString().replace("-", ""));
    master.setProjectId(projectId);
    master.setContentUnitId(unitId);
    master.setTier("A");
    master.setStatus("draft");
    master.setSourceVersionId(versionId);
    master.setRevision(0);
    master.setIsDeleted(0);
    storyboardMapper.insert(master);
    return master;
}
```

- [ ] **Step 2: Add a browser E2E covering route ownership**

`script-warehouse-flow.spec.js` must authenticate with the existing local test helper, then assert:

1. `/script-gen` shows four creation methods and no complete warehouse filter bar;
2. creating a project redirects into its workspace;
3. `/warehouse` finds the new project;
4. clicking the card opens `/warehouse/{id}`;
5. the explicit “继续创作” action opens `/script-gen/{id}/workspace`;
6. archived projects disappear from active results and appear in the archived view;
7. the legacy resolve route redirects to the same project, not a second page.

- [ ] **Step 3: Run the complete backend suite**

Run:

```bash
cd aicp-backend
mvn test
```

Expected: all backend tests pass with zero failures and zero errors.

- [ ] **Step 4: Run all frontend contracts and the production build**

Run:

```bash
cd aicp-frontend
node --test tests/*.test.js
npm run build
```

Expected: all Node tests pass and Vite completes successfully.

- [ ] **Step 5: Run the focused E2E against the local stack**

Run with the existing Playwright test setup after starting backend and frontend:

```bash
cd aicp-frontend
npx playwright test tests/script-warehouse-flow.spec.js
```

Expected: the complete creation-to-warehouse flow passes.

- [ ] **Step 6: Refresh backend static assets only after all tests pass**

Use the repository's existing frontend-to-backend packaging command or copy the successful `aicp-frontend/dist` output into `aicp-backend/src/main/resources/static`. Confirm `static/index.html` references only files that exist. Do not stage stale hashed assets.

- [ ] **Step 7: Review the final diff and commit verification changes**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; only intended source, test, migration, and refreshed static files are present.

Commit:

```bash
git add aicp-frontend/tests aicp-backend/src/test \
  aicp-backend/src/main/resources/static
git commit -m "test: verify script creation and warehouse lifecycle"
```

## Final Acceptance Checklist

- [ ] `/script-gen` is a launchpad, not a full warehouse.
- [ ] `/warehouse` reads only `content_projects` APIs.
- [ ] New projects are immediately searchable in the warehouse.
- [ ] Project card clicks always open `/warehouse/{id}`.
- [ ] Continuing creation is always an explicit action.
- [ ] Content, production, and commercial states are independently visible.
- [ ] Submit, approve, revise, lock, archive, and restore use action endpoints with optimistic locking and audit rows.
- [ ] Locked versions are required before formal storyboard, canvas, or listing entry.
- [ ] Legacy scripts resolve idempotently and appear once.
- [ ] Upstream changes do not overwrite downstream versions.
- [ ] The UI no longer exposes “V7 project” or “legacy creation mode” as user concepts.
- [ ] Backend, frontend contract, production build, and focused E2E tests pass.
