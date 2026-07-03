# Asset Generation History Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the incomplete asset-history and task-monitor pages with a Workspace-safe workbench that organizes generated assets by content project and category, persists management actions, places assets on canvases, and reuses the existing market publication flow.

**Architecture:** Keep `generation_tasks` as the generation process record and converge generated outputs onto `workspace_assets + asset_versions`. Add a read projection that unions active tasks with successful assets, command services with optimistic locking and idempotency, and a Vue workbench driven by URL state. Migrate `platform_assets` through additive schema changes, dual write, shadow reconciliation, feature flags, and delayed retirement.

**Tech Stack:** Java 17, Spring Boot 3.2, MyBatis-Plus, H2/MySQL, Vue 3 Composition API, Vue Router, Element Plus, Axios, Node built-in test runner, Maven/JUnit 5.

---

## File map

### Backend files to create

- `aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetWorkbenchController.java` — project, record, and detail queries.
- `aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetCommandController.java` — edit, favorite, move, batch, trash, restore, download, publish.
- `aicp-backend/src/main/java/com/aicp/module/asset/dto/AssetWorkbenchRequests.java` — validated request records.
- `aicp-backend/src/main/java/com/aicp/module/asset/dto/AssetWorkbenchViews.java` — snake-case response records.
- `aicp-backend/src/main/java/com/aicp/module/asset/domain/AssetWorkbenchEnums.java` — media, status, collection, operation, and allowed-action enums.
- `aicp-backend/src/main/java/com/aicp/module/asset/entity/WorkspaceAssetFavorite.java` — personal favorite of a Workspace asset.
- `aicp-backend/src/main/java/com/aicp/module/asset/entity/AssetActivityLog.java` — append-only audit record.
- `aicp-backend/src/main/java/com/aicp/module/asset/entity/CanvasAssetPlacement.java` — idempotent asset-to-canvas placement.
- `aicp-backend/src/main/java/com/aicp/module/asset/entity/AssetCommandIdempotency.java` — command replay record.
- `aicp-backend/src/main/java/com/aicp/module/asset/mapper/*Mapper.java` — mapper per new entity.
- `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetHistoryQueryService.java` — task/asset projection, facets, allowed actions.
- `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetCommandService.java` — edit, favorite, move, tags, and batch.
- `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetLifecycleService.java` — trash, restore, purge eligibility.
- `aicp-backend/src/main/java/com/aicp/module/asset/service/CanvasPlacementService.java` — permission-safe node creation.
- `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetPublicationAdapter.java` — adapter to existing personal/team publication services.
- `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetMigrationService.java` — resumable platform-to-workspace migration.
- `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetPurgeScheduler.java` — 30-day cleanup with placement protection.
- `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationSettlementService.java` — validate output and settle task, asset, version, and node state.
- `aicp-backend/src/main/java/com/aicp/module/generation/entity/GenerationSettlementOutbox.java` — durable compensation event.
- `aicp-backend/src/main/java/com/aicp/module/generation/mapper/GenerationSettlementOutboxMapper.java`.
- `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationSettlementCompensator.java`.

### Backend files to modify

- `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`
- `aicp-backend/src/main/java/com/aicp/common/exception/GlobalExceptionHandler.java`
- `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceContextFilter.java`
- `aicp-backend/src/main/java/com/aicp/common/workspace/AccountCenterPermissionClient.java`
- `aicp-backend/src/main/java/com/aicp/module/asset/entity/WorkspaceAsset.java`
- `aicp-backend/src/main/java/com/aicp/module/asset/entity/AssetVersion.java`
- `aicp-backend/src/main/java/com/aicp/module/generation/entity/GenerationTask.java`
- `aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationExecutor.java`
- `aicp-backend/src/main/java/com/aicp/module/generation/controller/GenerationController.java`
- `aicp-backend/src/main/resources/db/schema-h2.sql`
- `aicp-backend/src/main/resources/db/schema-mysql.sql`
- `aicp-backend/src/main/resources/application.yml`

### Frontend files to create

- `aicp-frontend/src/api/assetHistory.js`
- `aicp-frontend/src/views/asset-history/assetHistoryState.js`
- `aicp-frontend/src/views/asset-history/useAssetWorkbench.js`
- `aicp-frontend/src/views/asset-history/components/AssetProjectTree.vue`
- `aicp-frontend/src/views/asset-history/components/AssetCategoryTabs.vue`
- `aicp-frontend/src/views/asset-history/components/AssetFilterBar.vue`
- `aicp-frontend/src/views/asset-history/components/AssetRecordGrid.vue`
- `aicp-frontend/src/views/asset-history/components/AssetRecordCard.vue`
- `aicp-frontend/src/views/asset-history/components/AssetMediaPreview.vue`
- `aicp-frontend/src/views/asset-history/components/AssetDetailDrawer.vue`
- `aicp-frontend/src/views/asset-history/components/AssetBatchBar.vue`
- `aicp-frontend/src/views/asset-history/components/CanvasTargetDialog.vue`
- `aicp-frontend/src/views/asset-history/components/AssetPublishDialog.vue`
- `aicp-frontend/src/views/asset-history/components/AssetTrashPanel.vue`
- `aicp-frontend/tests/asset-history-state.test.js`
- `aicp-frontend/tests/asset-history-contract.test.js`

### Frontend files to modify

- `aicp-frontend/src/views/generation/AssetHistory.vue`
- `aicp-frontend/src/router/index.js`
- `aicp-frontend/src/components/Sidebar.vue`
- `aicp-frontend/tests/navigation-contract.test.js`

---

### Task 1: Lock the contract with enums, error codes, and schema tests

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/domain/AssetWorkbenchEnums.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/exception/GlobalExceptionHandler.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/domain/AssetWorkbenchContractTest.java`

- [ ] **Step 1: Write the failing enum and error mapping test**

```java
package com.aicp.module.asset.domain;

import com.aicp.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetWorkbenchContractTest {
    @Test
    void exposesStableAssetCategoriesAndStatuses() {
        assertThat(AssetWorkbenchEnums.AssetType.values()).extracting(Enum::name)
                .containsExactly("CHECKPOINT", "LORA", "STYLE_PACK", "PROMPT", "CHARACTER",
                        "SCENE", "PROP", "STORYBOARD", "VOICE", "MUSIC", "OTHER");
        assertThat(AssetWorkbenchEnums.RecordStatus.values()).extracting(Enum::name)
                .containsExactly("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELED");
    }

    @Test
    void reservesAssetWorkbenchErrors() {
        assertThat(ErrorCode.ASSET_FILE_MISSING.getCode()).isEqualTo(48008);
        assertThat(ErrorCode.ASSET_SETTLEMENT_FAILED.getCode()).isEqualTo(48016);
        assertThat(ErrorCode.GENERATION_TASK_STATE_CONFLICT.getCode()).isEqualTo(46021);
    }
}
```

- [ ] **Step 2: Run the test and confirm the contract is missing**

Run: `cd aicp-backend && mvn -Dtest=AssetWorkbenchContractTest test`  
Expected: FAIL because `AssetWorkbenchEnums` and the new error constants do not exist.

- [ ] **Step 3: Add the complete enum set**

```java
package com.aicp.module.asset.domain;

public final class AssetWorkbenchEnums {
    private AssetWorkbenchEnums() {}

    public enum AssetType { CHECKPOINT, LORA, STYLE_PACK, PROMPT, CHARACTER, SCENE, PROP, STORYBOARD, VOICE, MUSIC, OTHER }
    public enum MediaType { IMAGE, VIDEO, AUDIO, DATA, OTHER }
    public enum RecordKind { TASK, ASSET }
    public enum RecordStatus { PENDING, RUNNING, SUCCEEDED, FAILED, CANCELED }
    public enum Collection { UNFILED, FAVORITES, PUBLISHED, TRASH }
    public enum AssetStatus { ACTIVE, ARCHIVED, TRASHED }
    public enum BatchOperation { MOVE, SET_TYPE, ADD_TAGS, REMOVE_TAGS, TRASH, RESTORE }
    public enum AllowedAction { PREVIEW, EDIT, FAVORITE, DOWNLOAD, SEND_TO_CANVAS, REGENERATE, PUBLISH, TRASH, RESTORE, CANCEL_TASK, RETRY_TASK }
}
```

Add `ASSET_FILE_MISSING` through `ASSET_COMPENSATION_EXHAUSTED` with codes 48008–48017 and task codes 46020–46021 to `ErrorCode`. Map 46020/48001 to 404, 48002 to 403, 48004/48006/48009/48013/46021 to 409, 48005/48007/48008/48010/48014 to 422, and 48015–48017 to 503/500 in `GlobalExceptionHandler`.

- [ ] **Step 4: Run the contract test**

Run: `cd aicp-backend && mvn -Dtest=AssetWorkbenchContractTest test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset/domain/AssetWorkbenchEnums.java \
  aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java \
  aicp-backend/src/main/java/com/aicp/common/exception/GlobalExceptionHandler.java \
  aicp-backend/src/test/java/com/aicp/module/asset/domain/AssetWorkbenchContractTest.java
git commit -m "feat: define asset workbench contracts"
```

### Task 2: Add the canonical asset schema and synchronize H2/MySQL

**Files:**
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/java/com/aicp/module/asset/entity/WorkspaceAsset.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/asset/entity/AssetVersion.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/generation/entity/GenerationTask.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/schema/AssetWorkbenchSchemaTest.java`

- [ ] **Step 1: Write a failing schema parity test**

Create a Spring Boot integration test that queries `INFORMATION_SCHEMA.COLUMNS` and asserts that `workspace_assets` contains `content_project_id`, `source_canvas_project_id`, `source_node_id`, `source_task_id`, `media_type`, `deleted_at`, `deleted_by`, `purge_at`, `purge_blocked_reason`, and `legacy_platform_asset_id`; `asset_versions` contains storage and media columns; and `generation_tasks` contains Workspace, creator, content-project, type, retry, idempotency, and request columns.

```java
@SpringBootTest
class AssetWorkbenchSchemaTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void canonicalColumnsExist() {
        assertThat(columns("WORKSPACE_ASSETS")).contains(
                "CONTENT_PROJECT_ID", "SOURCE_CANVAS_PROJECT_ID", "SOURCE_NODE_ID",
                "SOURCE_TASK_ID", "MEDIA_TYPE", "PURGE_AT", "PURGE_BLOCKED_REASON");
        assertThat(columns("ASSET_VERSIONS")).contains(
                "SOURCE_TASK_ID", "STORAGE_PROVIDER", "STORAGE_BUCKET", "STORAGE_KEY",
                "MIME_TYPE", "FILE_SIZE", "WIDTH", "HEIGHT", "DURATION_MS", "GENERATION_SNAPSHOT");
        assertThat(columns("GENERATION_TASKS")).contains(
                "WORKSPACE_ID", "CREATED_BY", "CONTENT_PROJECT_ID", "ASSET_TYPE",
                "RETRY_OF_TASK_ID", "IDEMPOTENCY_KEY", "REQUEST_ID");
    }

    private Set<String> columns(String table) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?", String.class, table));
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=AssetWorkbenchSchemaTest test`  
Expected: FAIL listing the missing columns.

- [ ] **Step 3: Apply additive DDL in both schemas**

Add the exact columns from the design, the four relationship tables, `generation_settlement_outbox`, and the composite indexes. Use `VARCHAR` instead of new MySQL ENUMs for `workspace_type`, `asset_type`, `media_type`, and lifecycle status. Preserve every legacy column and table.

The outbox table must contain:

```sql
CREATE TABLE IF NOT EXISTS generation_settlement_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    payload TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    last_error VARCHAR(2000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (task_id, stage)
);
```

- [ ] **Step 4: Update entities to exactly match the schema**

Add matching Java fields and `@Version` where applicable. Keep `GenerationTask.status` lowercase string-compatible and retain its existing `projectId` field.

- [ ] **Step 5: Run schema and existing asset tests**

Run: `cd aicp-backend && mvn -Dtest=AssetWorkbenchSchemaTest,AssetMarketLifecycleE2ETest test`  
Expected: PASS with no H2 initialization error.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/resources/db/schema-h2.sql \
  aicp-backend/src/main/resources/db/schema-mysql.sql \
  aicp-backend/src/main/java/com/aicp/module/asset/entity/WorkspaceAsset.java \
  aicp-backend/src/main/java/com/aicp/module/asset/entity/AssetVersion.java \
  aicp-backend/src/main/java/com/aicp/module/generation/entity/GenerationTask.java \
  aicp-backend/src/test/java/com/aicp/module/asset/schema/AssetWorkbenchSchemaTest.java
git commit -m "feat: add canonical generated asset schema"
```

### Task 3: Normalize Workspace identity and protect all asset routes

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/common/workspace/AccountCenterPermissionClient.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceContextFilter.java`
- Modify: `aicp-frontend/src/stores/auth.js`
- Modify: `aicp-frontend/src/api/request.js`
- Modify: `aicp-backend/src/test/java/com/aicp/common/workspace/WorkspaceAccessServiceTest.java`

- [ ] **Step 1: Add failing identity normalization tests**

Test that `personal_7` resolves user 7, that arbitrary account-center enterprise IDs are preserved verbatim, and that `personal:7`/`ent:7` are not newly synthesized. Add route coverage assertions for `/api/v1/assets/**` and `/api/v1/generation/tasks/**`.

- [ ] **Step 2: Run the Workspace tests**

Run: `cd aicp-backend && mvn -Dtest=WorkspaceAccessServiceTest test`  
Expected: FAIL on legacy string formatting or missing path patterns.

- [ ] **Step 3: Normalize only the development fallback**

Use `personal_${userId}` in frontend fallback code. In the backend, preserve the exact active Workspace ID provided by account-center context; only parse `personal_` for the local fallback. Add asset and generation paths to `WorkspaceContextFilter`.

- [ ] **Step 4: Run backend and frontend identity tests**

Run: `cd aicp-backend && mvn -Dtest=WorkspaceAccessServiceTest test`  
Expected: PASS.  
Run: `cd aicp-frontend && node --test tests/login.spec.js`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/common/workspace/AccountCenterPermissionClient.java \
  aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceContextFilter.java \
  aicp-frontend/src/stores/auth.js aicp-frontend/src/api/request.js \
  aicp-backend/src/test/java/com/aicp/common/workspace/WorkspaceAccessServiceTest.java
git commit -m "fix: normalize asset workspace identity"
```

### Task 4: Add canonical entities, mappers, and a resumable migration service

**Files:**
- Create: new asset entities and mappers listed in the file map
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetMigrationService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/AssetMigrationServiceTest.java`

- [ ] **Step 1: Write failing migration tests**

Cover one platform asset with a content-project mapping, one without a mapping, one favorite, rerunning the same 500-row batch, and an invalid file reference. Assert preserved UUID, `PROJECT_GENERATED`, correct Workspace, `contentProjectId` or null, one version, one favorite, no duplicates, and an anomaly result for the invalid reference.

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-backend && mvn -Dtest=AssetMigrationServiceTest test`  
Expected: FAIL because migration service and new mappers do not exist.

- [ ] **Step 3: Implement the migration result contract**

```java
public record MigrationBatchResult(
        long scanned,
        long migrated,
        long skipped,
        long failed,
        Long lastPlatformAssetId,
        List<MigrationAnomaly> anomalies) {}

public record MigrationAnomaly(Long platformAssetId, String code, String message) {}
```

Implement `migrateAfter(long lastId, int limit)` with `limit` capped at 500. Resolve Workspace from project context first and owner fallback second; map content project through `canvas_projects.content_project_id`; map known subtypes to asset types; set unrecognized values to `OTHER`; preserve UUID; create exactly one version; migrate the creator's favorite; use `legacy_platform_asset_id` for idempotency.

- [ ] **Step 4: Run migration tests twice**

Run: `cd aicp-backend && mvn -Dtest=AssetMigrationServiceTest test && mvn -Dtest=AssetMigrationServiceTest test`  
Expected: both runs PASS and the second execution creates no duplicate rows.

- [ ] **Step 5: Commit**

Stage the new entities, mappers, migration service, and test, then commit:

```bash
git commit -m "feat: migrate generated assets to workspace library"
```

### Task 5: Settle successful generation atomically and compensate failures

**Files:**
- Create: `GenerationSettlementService.java`
- Create: `GenerationSettlementOutbox.java`
- Create: `GenerationSettlementOutboxMapper.java`
- Create: `GenerationSettlementCompensator.java`
- Modify: `GenerationExecutor.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/generation/service/GenerationSettlementServiceTest.java`

- [ ] **Step 1: Write failing settlement tests**

Test: valid output creates one asset/version and marks the task `succeeded`; missing storage key marks the task `failed` with 48008; asset insert failure leaves the task non-successful and creates one outbox event; retrying an outbox stage is idempotent; owner resolution never defaults to user 1.

- [ ] **Step 2: Run the failing tests**

Run: `cd aicp-backend && mvn -Dtest=GenerationSettlementServiceTest test`  
Expected: FAIL because settlement service does not exist.

- [ ] **Step 3: Implement settlement input and outcome**

```java
public record SettlementOutput(
        String storageProvider,
        String storageBucket,
        String storageKey,
        String mimeType,
        Long fileSize,
        Integer width,
        Integer height,
        Integer durationMs,
        String previewUrl,
        String checksum) {}

public record SettlementResult(Long assetId, Long versionId, String assetUuid) {}
```

`settle(taskId, output)` must validate ownership and output, insert/reuse the Workspace asset, append an immutable version, update `current_version_id`, write activity, write back node/shot, and mark the task succeeded last. Wrap database writes in a transaction and insert a unique outbox row when a cross-boundary write cannot complete.

- [ ] **Step 4: Replace `registerAssets` and remove user-1 fallback**

Make `GenerationExecutor` delegate to `GenerationSettlementService`. Delete `resolveOwnerId` fallback behavior; missing Workspace or creator is a settlement failure with a diagnostic outbox event.

- [ ] **Step 5: Implement compensation schedule**

Use retry delays 1 minute, 5 minutes, 30 minutes, and 2 hours. After four failures mark the outbox row `EXHAUSTED`, set error 48017, and leave the task non-successful.

- [ ] **Step 6: Run settlement and generation tests**

Run: `cd aicp-backend && mvn -Dtest=GenerationSettlementServiceTest test`  
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/generation \
  aicp-backend/src/test/java/com/aicp/module/generation/service/GenerationSettlementServiceTest.java
git commit -m "feat: settle generation outputs into canonical assets"
```

### Task 6: Define workbench DTOs and pure projection rules

**Files:**
- Create: `AssetWorkbenchRequests.java`
- Create: `AssetWorkbenchViews.java`
- Create: `AssetHistoryQueryService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/AssetHistoryQueryServiceTest.java`

- [ ] **Step 1: Write failing projection tests**

Cover lowercase API statuses, task/asset record IDs, project grouping, unfiled collection, facets, personal/team scope, stable pagination, allowed actions, and omission of cross-Workspace rows.

- [ ] **Step 2: Run tests**

Run: `cd aicp-backend && mvn -Dtest=AssetHistoryQueryServiceTest test`  
Expected: FAIL because DTOs and query service are missing.

- [ ] **Step 3: Define validated query and view records**

Use `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` on the DTO containers. Define `RecordQuery` with page 1 minimum, page size maximum 100, keyword maximum 100, date range maximum 366 days, and sort whitelist. Define `RecordSummary`, `RecordDetail`, `RecordFacets`, `ProjectSummary`, `CreatorView`, `ReferenceView`, and `ActivityView` with the exact fields from the design.

- [ ] **Step 4: Implement the read projection**

Query tasks in pending/running/failed/canceled plus canonical assets for succeeded records. Apply Workspace condition to every query before optional filters. Order by requested field plus UUID as a stable tie-breaker. Compute facets from the same base predicate. Return only actions granted by Workspace permissions and record state.

- [ ] **Step 5: Run tests**

Run: `cd aicp-backend && mvn -Dtest=AssetHistoryQueryServiceTest test`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset/dto \
  aicp-backend/src/main/java/com/aicp/module/asset/service/AssetHistoryQueryService.java \
  aicp-backend/src/test/java/com/aicp/module/asset/service/AssetHistoryQueryServiceTest.java
git commit -m "feat: query unified asset history records"
```

### Task 7: Expose project, record, and detail query APIs

**Files:**
- Create: `AssetWorkbenchController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/AssetWorkbenchApiIntegrationTest.java`
- Modify: `GenerationController.java`

- [ ] **Step 1: Write failing MockMvc tests**

Test all three GET routes, snake_case output, 24-item default page, invalid sort 400/40002, missing record 404/48001, and cross-Workspace record 404/48001. Verify legacy `/assets/history` returns deprecation headers and delegates to canonical query when its feature flag is enabled.

- [ ] **Step 2: Run API tests**

Run: `cd aicp-backend && mvn -Dtest=AssetWorkbenchApiIntegrationTest test`  
Expected: FAIL with 404 routes.

- [ ] **Step 3: Implement controller routes**

Add:

```java
@GetMapping("/api/v1/assets/workbench/projects")
@GetMapping("/api/v1/assets/history/records")
@GetMapping("/api/v1/assets/history/records/{recordKind}/{recordUuid}")
```

Resolve `WorkspaceContext`, bind validated DTOs, return `ApiResponse<PageResult<RecordSummary>>` plus facets in a dedicated page view, and never convert entities with a generic `ObjectMapper`.

- [ ] **Step 4: Add legacy headers**

Return `Deprecation: true`, a configured `Sunset` date at least 60 days after rollout, and a `Link` header pointing at `/assets/history/records`.

- [ ] **Step 5: Run API tests**

Run: `cd aicp-backend && mvn -Dtest=AssetWorkbenchApiIntegrationTest test`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetWorkbenchController.java \
  aicp-backend/src/main/java/com/aicp/module/generation/controller/GenerationController.java \
  aicp-backend/src/test/java/com/aicp/module/asset/AssetWorkbenchApiIntegrationTest.java
git commit -m "feat: expose asset workbench query APIs"
```

### Task 8: Implement edit, favorite, move, batch, trash, and restore commands

**Files:**
- Create: `AssetCommandController.java`
- Create: `AssetCommandService.java`
- Create: `AssetLifecycleService.java`
- Create: `AssetPurgeScheduler.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/AssetCommandServiceTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/AssetLifecycleServiceTest.java`

- [ ] **Step 1: Write failing command tests**

Cover name/type/tags validation, ETag conflict, favorite idempotency, move to project, move to unfiled, batch partial success, trash, restore to original project, restore to unfiled when project is gone, purge without placement, and blocked purge with an active placement.

- [ ] **Step 2: Run tests**

Run: `cd aicp-backend && mvn -Dtest=AssetCommandServiceTest,AssetLifecycleServiceTest test`  
Expected: FAIL because services are missing.

- [ ] **Step 3: Implement command transactions**

Each command must load by `(workspace_id, asset_uuid)`, enforce `If-Match`, update `row_version`, append an activity row, and return the current ETag. Favorites use `(user_id, workspace_id, asset_id)` uniqueness. Batch operations cap at 100 and execute one item per transaction, returning `succeeded` and `failed` arrays.

- [ ] **Step 4: Implement lifecycle rules**

Trash sets status `TRASHED`, `deleted_at`, `deleted_by`, and `purge_at = deleted_at + 30 days`. Restore returns to ACTIVE and clears deletion fields. Purge deletes file and rows only with no active placement; otherwise keep minimal records and set `purge_blocked_reason=ACTIVE_CANVAS_PLACEMENT`.

- [ ] **Step 5: Expose command routes and HTTP semantics**

Add PATCH asset, PUT/DELETE favorite, POST move, POST batch, DELETE asset, and POST restore. Return 428 when `If-Match` is missing and 409/48004 on mismatch.

- [ ] **Step 6: Run command tests**

Run: `cd aicp-backend && mvn -Dtest=AssetCommandServiceTest,AssetLifecycleServiceTest test`  
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetCommandController.java \
  aicp-backend/src/main/java/com/aicp/module/asset/service/AssetCommandService.java \
  aicp-backend/src/main/java/com/aicp/module/asset/service/AssetLifecycleService.java \
  aicp-backend/src/main/java/com/aicp/module/asset/service/AssetPurgeScheduler.java \
  aicp-backend/src/test/java/com/aicp/module/asset/service
git commit -m "feat: manage workspace asset lifecycle"
```

### Task 9: Create idempotent asset-to-canvas placement

**Files:**
- Create: `CanvasPlacementService.java`
- Modify: `AssetCommandController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/CanvasPlacementServiceTest.java`

- [ ] **Step 1: Write failing placement tests**

Cover image/video/audio node mapping, project/canvas mismatch, no target permission, trashed asset, missing file, absolute placement without coordinates, ten repeated requests with one idempotency key, and same key with changed payload.

- [ ] **Step 2: Run tests**

Run: `cd aicp-backend && mvn -Dtest=CanvasPlacementServiceTest test`  
Expected: FAIL because placement service is missing.

- [ ] **Step 3: Implement placement request hashing and node creation**

Validate current Workspace, target project/canvas relationship, asset status, current version, and media compatibility. Hash the normalized request, insert/replay `asset_command_idempotencies`, create exactly one canvas node through the existing canvas service, and insert one `canvas_asset_placements` row.

- [ ] **Step 4: Return the exact response**

```java
public record CanvasPlacementView(
        Long placementId,
        String nodeUuid,
        String redirectUrl,
        boolean replayed) {}
```

- [ ] **Step 5: Replace the placeholder endpoint**

The legacy numeric-ID endpoint must resolve the asset and require target data; if target canvas is absent, return 422/48014 instead of a success message.

- [ ] **Step 6: Run tests**

Run: `cd aicp-backend && mvn -Dtest=CanvasPlacementServiceTest test`  
Expected: PASS and one node after ten identical calls.

- [ ] **Step 7: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset/service/CanvasPlacementService.java \
  aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetCommandController.java \
  aicp-backend/src/test/java/com/aicp/module/asset/service/CanvasPlacementServiceTest.java
git commit -m "feat: place assets on canvas idempotently"
```

### Task 10: Add signed download, regeneration, and market publication adapter

**Files:**
- Create: `AssetPublicationAdapter.java`
- Modify: `AssetCommandController.java`
- Modify: `GenerationService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/AssetPublicationAdapterTest.java`

- [ ] **Step 1: Write failing tests**

Test five-minute download expiry, missing file 48008, personal direct publication, enterprise PENDING request, duplicate PENDING conflict, regeneration copying snapshot parameters, model replacement, and retry lineage/idempotency.

- [ ] **Step 2: Run tests**

Run: `cd aicp-backend && mvn -Dtest=AssetPublicationAdapterTest test`  
Expected: FAIL because adapter endpoints are absent.

- [ ] **Step 3: Implement adapter behavior**

Resolve canonical Workspace asset and current version, then call existing `AssetPublicationService.publishPersonal` or `requestEnterprisePublish`. Do not create a second listing or approval implementation. Map unsupported production types to 48007.

- [ ] **Step 4: Implement signed download and regeneration**

Generate a storage URL that expires in 300 seconds. Regeneration reads `generation_snapshot`, applies allowed patches, calls credit estimation, creates a new pending task with `retry_of_task_id`, Workspace, creator, content project, asset type, and idempotency key.

- [ ] **Step 5: Run tests**

Run: `cd aicp-backend && mvn -Dtest=AssetPublicationAdapterTest,AssetMarketLifecycleE2ETest test`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset/service/AssetPublicationAdapter.java \
  aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetCommandController.java \
  aicp-backend/src/main/java/com/aicp/module/generation/service/GenerationService.java \
  aicp-backend/src/test/java/com/aicp/module/asset/service/AssetPublicationAdapterTest.java
git commit -m "feat: reuse generated assets across download and market"
```

### Task 11: Build frontend state, URL contract, and API client

**Files:**
- Create: `aicp-frontend/src/api/assetHistory.js`
- Create: `aicp-frontend/src/views/asset-history/assetHistoryState.js`
- Create: `aicp-frontend/src/views/asset-history/useAssetWorkbench.js`
- Create: `aicp-frontend/tests/asset-history-state.test.js`

- [ ] **Step 1: Write failing pure-state tests**

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'
import { parseAssetHistoryQuery, serializeAssetHistoryState, mapRecordCard } from '../src/views/asset-history/assetHistoryState.js'

test('restores project, category, statuses and page from URL', () => {
  const state = parseAssetHistoryQuery({ project_uuid: 'p1', asset_type: 'CHARACTER', status: 'running,failed', page: '2' })
  assert.deepEqual(state.statuses, ['running', 'failed'])
  assert.equal(state.projectUuid, 'p1')
  assert.equal(state.page, 2)
})

test('serializes only non-default workbench state', () => {
  assert.deepEqual(serializeAssetHistoryState({ scope: 'mine', page: 1, pageSize: 24, statuses: [] }), { scope: 'mine' })
})

test('maps failed task to retry card without asset actions', () => {
  const card = mapRecordCard({ record_kind: 'task', status: 'failed', allowed_actions: ['retry_task'] })
  assert.equal(card.canRetry, true)
  assert.equal(card.canDownload, false)
})
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `cd aicp-frontend && node --test tests/asset-history-state.test.js`  
Expected: FAIL because state functions do not exist.

- [ ] **Step 3: Implement pure state functions**

Implement default values, enum validation, comma-separated status/tags, numeric page guards, and projection exclusively from `allowed_actions`.

- [ ] **Step 4: Implement API functions and composable**

Expose one function per backend endpoint. In `useAssetWorkbench`, maintain separate project/list/detail loading and error states, increment a request sequence for every list request, ignore stale responses, debounce keyword by 300ms, and reset on active Workspace change.

- [ ] **Step 5: Run tests**

Run: `cd aicp-frontend && node --test tests/asset-history-state.test.js`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-frontend/src/api/assetHistory.js \
  aicp-frontend/src/views/asset-history/assetHistoryState.js \
  aicp-frontend/src/views/asset-history/useAssetWorkbench.js \
  aicp-frontend/tests/asset-history-state.test.js
git commit -m "feat: add asset workbench state and API client"
```

### Task 12: Build the project tree, filters, task/asset cards, and workbench shell

**Files:**
- Create: `AssetProjectTree.vue`, `AssetCategoryTabs.vue`, `AssetFilterBar.vue`, `AssetRecordGrid.vue`, `AssetRecordCard.vue`
- Modify: `aicp-frontend/src/views/generation/AssetHistory.vue`
- Create: `aicp-frontend/tests/asset-history-contract.test.js`

- [ ] **Step 1: Write failing component contract tests**

Read the Vue source files and assert the expected imports, props/emits, loading/error/empty branches, category labels, server pagination event, and absence of `asset.favorite = !asset.favorite` and client-side full-array filtering.

- [ ] **Step 2: Run tests**

Run: `cd aicp-frontend && node --test tests/asset-history-contract.test.js`  
Expected: FAIL because components do not exist.

- [ ] **Step 3: Implement navigation and filter components**

Use Element Plus controls and existing global design tokens. Show counts for projects, unfiled, favorites, published, and trash. Keep category and media type separate. Every component receives data through props and emits one explicit event; none calls APIs directly.

- [ ] **Step 4: Implement record cards and grid**

Render pending/running progress, failed/canceled diagnostics, and successful media metadata. Render actions only when present in `allowed_actions`. Add skeleton, retryable error, no-data, no-match, and no-permission states. Use server pagination with 24/48/96 page sizes.

- [ ] **Step 5: Replace the old AssetHistory shell**

Compose the new components with `useAssetWorkbench`. Preserve route query, scroll position when opening detail, and responsive collapse of the project tree.

- [ ] **Step 6: Run contract tests and build**

Run: `cd aicp-frontend && node --test tests/asset-history-contract.test.js tests/asset-history-state.test.js && npm run build`  
Expected: all tests PASS and Vite build succeeds.

- [ ] **Step 7: Commit**

```bash
git add aicp-frontend/src/views/generation/AssetHistory.vue \
  aicp-frontend/src/views/asset-history/components \
  aicp-frontend/tests/asset-history-contract.test.js
git commit -m "feat: build project-organized asset workbench"
```

### Task 13: Add detail, media preview, batch, canvas, publish, and trash interactions

**Files:**
- Create: remaining Vue components listed in the frontend file map
- Modify: `AssetHistory.vue`
- Modify: `asset-history-contract.test.js`

- [ ] **Step 1: Extend failing component tests**

Assert image/video/audio branches, signed-download usage, four detail tabs, URL `record_kind/record_uuid`, batch partial failures, canvas target fields, personal/team publication branches, and purge date/reference display.

- [ ] **Step 2: Run and confirm failure**

Run: `cd aicp-frontend && node --test tests/asset-history-contract.test.js`  
Expected: FAIL on missing detail and command components.

- [ ] **Step 3: Implement media and detail components**

Use `<img>`, `<video controls>`, and `<audio controls>` by media type. Fetch short-lived URLs only when preview/download is requested. Stop playback on unmount. Display file-unavailable state without removing metadata.

- [ ] **Step 4: Implement command dialogs**

Canvas dialog submits target project/canvas, placement, coordinates, and a generated idempotency key. Batch bar keeps failed rows selected. Publish dialog chooses personal direct or enterprise request from Workspace type. Trash panel displays reference count, `purge_at`, and blocked reason.

- [ ] **Step 5: Run tests and build**

Run: `cd aicp-frontend && node --test tests/asset-history-contract.test.js tests/asset-history-state.test.js && npm run build`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-frontend/src/views/asset-history/components \
  aicp-frontend/src/views/generation/AssetHistory.vue \
  aicp-frontend/tests/asset-history-contract.test.js
git commit -m "feat: complete asset workbench interactions"
```

### Task 14: Merge task-monitor navigation and add feature flags

**Files:**
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/src/components/Sidebar.vue`
- Modify: `aicp-frontend/tests/navigation-contract.test.js`
- Modify: `aicp-backend/src/main/resources/application.yml`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/config/AssetFeatureProperties.java`

- [ ] **Step 1: Write failing navigation and property tests**

Assert there is one asset-history menu, no task-monitor menu, `/task-monitor` redirects with active statuses, and six properties exist: canonical write/read, workbench UI, lifecycle manage, market publish, and legacy write.

- [ ] **Step 2: Run tests**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js`  
Expected: FAIL because TaskMonitor is still a menu route.

- [ ] **Step 3: Implement route compatibility**

Remove the sidebar item. Retain the route as a redirect function returning `/asset-history?status=pending,running,failed`. Do not delete `TaskMonitor.vue` until the compatibility window ends.

- [ ] **Step 4: Add typed feature properties**

Use prefix `features.asset` and booleans `canonicalWrite`, `canonicalRead`, `workbenchUi`, `lifecycleManage`, `marketPublish`, and `legacyWrite`. Default new read/UI/manage/publish flags to false; default legacy write to true.

- [ ] **Step 5: Run navigation and configuration tests**

Run: `cd aicp-frontend && node --test tests/navigation-contract.test.js && npm run build`  
Expected: PASS.  
Run: `cd aicp-backend && mvn -DskipTests compile`  
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add aicp-frontend/src/router/index.js aicp-frontend/src/components/Sidebar.vue \
  aicp-frontend/tests/navigation-contract.test.js \
  aicp-backend/src/main/resources/application.yml \
  aicp-backend/src/main/java/com/aicp/module/asset/config/AssetFeatureProperties.java
git commit -m "feat: gate asset workbench rollout"
```

### Task 15: Add security, performance, migration, and end-to-end release gates

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/AssetWorkbenchSecurityIntegrationTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/AssetWorkbenchScaleTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/AssetWorkbenchLifecycleE2ETest.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/observability/AssetMetrics.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/observability/AssetMetricsTest.java`
- Modify: `aicp-backend/pom.xml`
- Modify: `aicp-frontend/package.json`
- Create: `docs/02-derived/asset-workbench-rollout-runbook.md`

- [ ] **Step 1: Write the tenant-isolation matrix**

Test personal A/B and enterprise A/B for list, detail, download, batch, canvas, and publish. Cross-Workspace IDs must return 404/48001; current-Workspace insufficient permissions must return 403/48002. A mixed-Workspace batch must fail foreign rows without affecting local rows.

- [ ] **Step 2: Write the 12 E2E scenarios**

Use the exact scenarios from the design: project classification, unfiled, successful settlement, retry lineage, cancellation, persistent favorite, batch partial failure, canvas idempotency, personal publish, team approval, trash/restore, and cross-Workspace isolation.

- [ ] **Step 3: Write the scale test**

Seed 50,000 rows in one Workspace and 500,000 total across Workspaces. Assert records p95 under 500ms in repeated local integration queries, stable pagination, correct facets, batch 100 behavior, and no query lacking `workspace_id` in captured SQL.

- [ ] **Step 4: Instrument the approved metrics and coverage gates**

Create `AssetMetrics` with counters/timers named `asset_success_without_version_total`, `workspace_isolation_violation_total`, `asset_settlement_success_rate`, `asset_compensation_oldest_seconds`, `asset_api_availability`, `records_latency`, `detail_latency`, `command_latency`, `idempotency_duplicate_side_effect_total`, `migration_unexplained_diff_total`, `frontend_asset_error_rate`, and `asset_first_usable_ms`. Tag backend operations with request, Workspace, project, task, asset, operation, status, error, provider, and model identifiers without putting prompts or file URLs into metric labels.

Add the JaCoCo Maven plugin and restrict its check rule to the new workbench controllers/services/policies: line coverage 0.85, branch coverage 0.80, and branch coverage 1.00 for lifecycle, permission, and settlement classes. Add frontend scripts:

```json
{
  "test": "node --test tests/*.test.js tests/*.spec.js",
  "test:coverage": "node --experimental-test-coverage --test-coverage-lines=85 --test-coverage-branches=80 --test tests/asset-history-state.test.js"
}
```

Write `AssetMetricsTest` to assert the metric names and mandatory low-cardinality tags. Run `mvn verify` and `npm run test:coverage`; both must fail if a configured threshold is missed.

- [ ] **Step 5: Write the rollout runbook**

Document M0–M6 entry/exit gates, the six named feature flags, rollout percentages 5/25/50/100, 24/48-hour observation windows, every metric and alert window from the design, rollback commands, reconciliation fields, the 14-day zero-call requirement, and the 60-day minimum legacy compatibility period.

- [ ] **Step 6: Run backend release gates**

Run: `cd aicp-backend && mvn -Dtest=AssetWorkbenchSecurityIntegrationTest,AssetWorkbenchScaleTest,AssetWorkbenchLifecycleE2ETest,AssetMetricsTest verify`  
Expected: PASS with zero isolation failures, zero duplicate placements, and zero succeeded-without-version rows.

- [ ] **Step 7: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset/observability \
  aicp-backend/src/test/java/com/aicp/module/asset \
  aicp-backend/pom.xml aicp-frontend/package.json \
  docs/02-derived/asset-workbench-rollout-runbook.md
git commit -m "test: gate asset workbench rollout"
```

### Task 16: Run complete verification and prepare M0 rollout

**Files:**
- Verify all files changed by Tasks 1–15

- [ ] **Step 1: Run the complete backend suite**

Run: `cd aicp-backend && mvn test`  
Expected: BUILD SUCCESS with no failing or skipped asset-workbench gate.

- [ ] **Step 2: Run all frontend contract tests**

Run: `cd aicp-frontend && node --test tests/*.test.js tests/*.spec.js`  
Expected: all tests PASS.

- [ ] **Step 3: Build the frontend**

Run: `cd aicp-frontend && npm run build`  
Expected: Vite build succeeds with the AssetHistory chunk emitted and no unresolved import.

- [ ] **Step 4: Verify migration and schema consistency**

Run: `cd aicp-backend && mvn -Dtest=AssetWorkbenchSchemaTest,AssetMigrationServiceTest test`  
Expected: PASS on H2.

Run the MySQL preflight against an isolated database:

```bash
cd aicp-backend
docker compose up -d mysql
docker compose exec -T mysql mysql -uroot -proot123 -e \
  "DROP DATABASE IF EXISTS aicp_asset_workbench_test; CREATE DATABASE aicp_asset_workbench_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3307/aicp_asset_workbench_test?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai' \
  mvn -Dspring.profiles.active=mysql -Dtest=AssetWorkbenchSchemaTest,AssetMigrationServiceTest test
```

Expected: BUILD SUCCESS and migration reconciliation reports zero unexplained differences.

- [ ] **Step 5: Verify the working tree and commit history**

Run: `git status --short && git log --oneline -16`  
Expected: only intentionally generated build artifacts are untracked/modified; Tasks 1–15 appear as focused commits.

- [ ] **Step 6: Create the M0 baseline report**

Run the migration service in audit-only mode and save counts for platform assets, canonical assets, Workspace formats, empty file references, succeeded-without-version tasks, favorites, and orphan project links in the rollout ticket. Do not enable `asset.canonical.write` until the report has zero unexplained ownership ambiguity.

---

## Execution checkpoints

- After Task 3: review Workspace normalization before any canonical write.
- After Task 5: require `succeeded_without_version = 0` in tests before query/UI work proceeds.
- After Task 10: review the complete backend API and security matrix.
- After Task 13: conduct product acceptance of project separation, task cards, media preview, and canvas placement.
- After Task 15: approve M0/M1 rollout only; production feature flags remain off until the runbook gates pass.
