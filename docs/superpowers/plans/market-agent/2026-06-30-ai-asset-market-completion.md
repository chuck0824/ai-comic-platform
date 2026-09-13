# AI Asset Market Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mock AI asset market with a tenant-safe, persisted free-asset workflow covering public listing, personal publishing, enterprise approval, claiming, Workspace library reuse, project application, and undo.

**Architecture:** Keep asset-market business data in the Spring Boot `8080` modular monolith and use `3001` only as the authoritative Workspace membership and permission service. Separate private `WorkspaceAsset` records from public `MarketListing` snapshots; claiming creates an entitlement and an isolated buyer Workspace asset in one transaction. Split the Vue page into public-market, library, publishing, approval, detail, and application components.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Security, MyBatis-Plus, H2/MySQL, JUnit 5, Mockito, Go/Gin/GORM for the `3001` permission contract, Vue 3, Pinia-style composables, Axios, Element Plus, Node test runner.

---

## Scope and dependency lock

This is one vertical slice: every task contributes to the same publish → discover → claim → reuse workflow. Paid orders, reviews, reporting, audio playback, and recommendation algorithms remain excluded.

The first task adds the exact `3001` membership-read contract required by the approved design. It does not recreate account management in `8080`. Existing or future enterprise-management screens must write Workspace and membership data to these `3001` tables.

## File structure

### `3001` account-center contract

- `new-api/model/aicp_workspace.go`: Workspace and membership persistence plus permission lookup.
- `new-api/controller/aicp_workspace.go`: authenticated membership endpoint.
- `new-api/middleware/aicp_jwt.go`: accept the Spring JWT claim names actually emitted by `8080`.
- `new-api/router/api-router.go`: register the AICP Workspace endpoint.
- `new-api/model/main.go`: migrate Workspace tables.
- `new-api/controller/aicp_workspace_test.go`: contract and isolation tests.

### `8080` backend

- `aicp-backend/src/main/java/com/aicp/common/workspace/*`: request-scoped Workspace context, `3001` client, permission checks, and filter.
- `aicp-backend/src/main/java/com/aicp/module/asset/domain/AssetEnums.java`: all stable asset state enums.
- `aicp-backend/src/main/java/com/aicp/module/asset/entity/*`: one entity per asset-market table.
- `aicp-backend/src/main/java/com/aicp/module/asset/mapper/*`: tenant-aware persistence boundaries.
- `aicp-backend/src/main/java/com/aicp/module/asset/dto/AssetRequests.java`: validated command DTOs.
- `aicp-backend/src/main/java/com/aicp/module/asset/dto/AssetViews.java`: stable response DTOs.
- `aicp-backend/src/main/java/com/aicp/module/asset/service/*`: query, library, publishing, claiming, application, and undo services.
- `aicp-backend/src/main/java/com/aicp/module/asset/controller/*`: public market, Workspace library, and approval controllers.
- `aicp-backend/src/main/resources/db/schema-h2.sql`: dev/test schema and seed data.
- `aicp-backend/src/main/resources/db/schema-mysql.sql`: deployable MySQL schema.
- `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`: stable `48xxx` asset-market errors.
- `aicp-backend/src/main/java/com/aicp/common/exception/GlobalExceptionHandler.java`: map asset errors to HTTP statuses.
- `aicp-backend/src/main/java/com/aicp/module/canvas/entity/CanvasProject.java`: persist authoritative `workspace_id`.
- `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasService.java`: enforce Workspace-scoped project access.
- `aicp-backend/src/test/java/com/aicp/module/asset/*`: schema, service, security, and lifecycle tests.

### `8080` frontend

- `aicp-frontend/src/api/asset.js`: new listing, library, publishing, approval, application, and undo API.
- `aicp-frontend/src/api/request.js`: attach active Workspace context without navigating to `3001`.
- `aicp-frontend/src/views/asset-market/useAssetMarket.js`: market state and async actions.
- `aicp-frontend/src/views/asset-market/assetMarketState.js`: pure query and permission helpers.
- `aicp-frontend/src/views/asset-market/components/*`: focused UI components.
- `aicp-frontend/src/views/AssetMarket.vue`: page shell only.
- `aicp-frontend/src/views/canvas/components/WorkspaceAssetPicker.vue`: reuse claimed assets from the canvas.
- `aicp-frontend/src/views/Canvas.vue`: open the picker and apply the selected asset.
- `aicp-frontend/tests/asset-market-state.test.js`: pure UI-state tests.
- `aicp-frontend/tests/asset-market-api.test.js`: request-shape tests using an injected request function.

## Task 1: Add the authoritative `3001` Workspace permission contract

**Files:**
- Create: `new-api/model/aicp_workspace.go`
- Create: `new-api/controller/aicp_workspace.go`
- Create: `new-api/controller/aicp_workspace_test.go`
- Modify: `new-api/model/main.go`
- Modify: `new-api/middleware/aicp_jwt.go`
- Modify: `new-api/router/api-router.go`

- [ ] **Step 1: Write failing membership contract tests**

```go
func TestGetAicpWorkspaceMembership(t *testing.T) {
    db := initTestDB(t, &model.AicpWorkspace{}, &model.AicpWorkspaceMember{})
    db.Create(&model.AicpWorkspace{ID: "ent_100", Type: "enterprise", OwnerUserID: 7})
    db.Create(&model.AicpWorkspaceMember{WorkspaceID: "ent_100", UserID: 9, Status: "active", Permissions: `["asset.view","asset.use"]`})

    router := gin.New()
    router.GET("/api/aicp/workspaces/:id/membership", withAicpUser(9), GetAicpWorkspaceMembership)
    response := performRequest(router, "GET", "/api/aicp/workspaces/ent_100/membership")

    require.Equal(t, http.StatusOK, response.Code)
    require.JSONEq(t, `{"success":true,"data":{"workspace_id":"ent_100","workspace_type":"enterprise","user_id":9,"permissions":["asset.view","asset.use"]}}`, response.Body.String())
}

func TestGetAicpWorkspaceMembershipHidesOtherTenant(t *testing.T) {
    seedWorkspaceMember(t, "ent_100", 9, []string{"asset.view"})
    response := requestAsUser(10, "/api/aicp/workspaces/ent_100/membership")
    require.Equal(t, http.StatusNotFound, response.Code)
}
```

- [ ] **Step 2: Run the tests and verify failure**

Run: `cd new-api && go test ./controller -run TestGetAicpWorkspaceMembership -count=1`

Expected: FAIL because `AicpWorkspace`, `AicpWorkspaceMember`, and the handler do not exist.

- [ ] **Step 3: Implement the model and handler**

```go
type AicpWorkspace struct {
    ID          string `json:"id" gorm:"primaryKey;size:64"`
    Type        string `json:"type" gorm:"size:16;not null"`
    OwnerUserID int64  `json:"owner_user_id" gorm:"index;not null"`
}

type AicpWorkspaceMember struct {
    ID          uint   `json:"id" gorm:"primaryKey"`
    WorkspaceID string `json:"workspace_id" gorm:"uniqueIndex:uk_workspace_user;size:64;not null"`
    UserID      int64  `json:"user_id" gorm:"uniqueIndex:uk_workspace_user;index;not null"`
    Status      string `json:"status" gorm:"size:16;not null"`
    Permissions string `json:"permissions" gorm:"type:text;not null"`
}

func FindActiveWorkspaceMembership(workspaceID string, userID int64) (*AicpWorkspace, *AicpWorkspaceMember, error) {
    var workspace AicpWorkspace
    var member AicpWorkspaceMember
    if err := DB.Where("id = ?", workspaceID).First(&workspace).Error; err != nil { return nil, nil, err }
    if err := DB.Where("workspace_id = ? AND user_id = ? AND status = ?", workspaceID, userID, "active").First(&member).Error; err != nil { return nil, nil, err }
    return &workspace, &member, nil
}
```

The controller must decode `Permissions` as `[]string`, return `404` for absent/inactive membership, and never accept a user ID from query parameters. Register:

```go
aicpRoute := apiRouter.Group("/aicp")
aicpRoute.Use(middleware.AicpJwtAuth(), middleware.UserAuth())
aicpRoute.GET("/workspaces/:id/membership", controller.GetAicpWorkspaceMembership)
```

Update `validateAicpJWT` consumers to read `uid` and JWT subject `sub`, and add both Workspace models to normal and fast `AutoMigrate` lists.

- [ ] **Step 4: Run contract and middleware tests**

Run: `cd new-api && go test ./controller ./middleware ./model -count=1`

Expected: PASS, including rejection of inactive and cross-tenant memberships.

- [ ] **Step 5: Commit**

```bash
git add new-api/model/aicp_workspace.go new-api/controller/aicp_workspace.go new-api/controller/aicp_workspace_test.go new-api/model/main.go new-api/middleware/aicp_jwt.go new-api/router/api-router.go
git commit -m "feat: add workspace permission contract"
```

## Task 2: Resolve a trusted Workspace context in `8080`

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceContext.java`
- Create: `aicp-backend/src/main/java/com/aicp/common/workspace/AccountCenterPermissionClient.java`
- Create: `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceAccessService.java`
- Create: `aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceContextFilter.java`
- Create: `aicp-backend/src/test/java/com/aicp/common/workspace/WorkspaceAccessServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/config/SecurityConfig.java`
- Modify: `aicp-frontend/src/api/request.js`

- [ ] **Step 1: Write failing fail-closed and permission tests**

```java
@Test void rejectsWorkspaceWhenAccountCenterDoesNotConfirmMembership() {
    when(client.membership("ent_100", "Bearer token")).thenThrow(new UpstreamUnavailableException());
    assertThatThrownBy(() -> service.resolve("ent_100", "Bearer token", 9L))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("账户中心暂不可用");
}

@Test void requiresMappedPermission() {
    WorkspaceContext context = new WorkspaceContext("ent_100", "enterprise", 9L, Set.of("asset.view"));
    assertThatThrownBy(() -> context.require("asset.publish.approve"))
        .isInstanceOf(BizException.class);
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd aicp-backend && mvn -Dtest=WorkspaceAccessServiceTest test`

Expected: FAIL because the Workspace classes do not exist.

- [ ] **Step 3: Implement context resolution and filter**

```java
public record WorkspaceContext(String workspaceId, String workspaceType, Long userId, Set<String> permissions) {
    public void require(String permission) {
        if (!permissions.contains(permission)) throw new BizException(ErrorCode.ASSET_PERMISSION_DENIED);
    }
}
```

`WorkspaceContextFilter` must read `X-Workspace-Id`, forward the original bearer token to `GET {new-api.base-url}/api/aicp/workspaces/{id}/membership`, verify the returned `user_id` equals the authenticated user, and store the resulting context as a request attribute. Missing `X-Workspace-Id` on a protected path returns `400`; mismatched users, inactive memberships, and upstream failures must fail closed for `/api/v1/asset/library/**`, claims, publishing, approvals, applications, and undo. Public `GET /api/v1/asset/market/listings/**` remains readable without Workspace context.

Add the filter after JWT authentication. In `request.js` attach only the locally stored active context returned by login/account APIs:

```js
const workspaceId = localStorage.getItem('active_workspace_id')
if (workspaceId) config.headers['X-Workspace-Id'] = workspaceId
```

- [ ] **Step 4: Run backend tests and frontend build**

Run: `cd aicp-backend && mvn -Dtest=WorkspaceAccessServiceTest test && cd ../aicp-frontend && npm run build`

Expected: PASS and successful Vite build.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/common/workspace aicp-backend/src/test/java/com/aicp/common/workspace aicp-backend/src/main/java/com/aicp/common/config/SecurityConfig.java aicp-frontend/src/api/request.js
git commit -m "feat: enforce workspace permission context"
```

## Task 3: Create the unified asset-market schema and domain entities

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/domain/AssetEnums.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/entity/WorkspaceAsset.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/entity/AssetVersion.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/entity/MarketListing.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/entity/AssetEntitlement.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/entity/AssetFavorite.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/entity/AssetPublishRequest.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/entity/AssetApplication.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/mapper/*Mapper.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/schema/AssetMarketSchemaTest.java`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`

- [ ] **Step 1: Write the failing schema test**

```java
@Test void enforcesWorkspaceAndIdempotencyUniqueKeys() {
    assertUniqueConstraint("asset_entitlements", "beneficiary_workspace_id", "listing_id");
    assertUniqueConstraint("asset_applications", "workspace_id", "idempotency_key");
    assertColumnNotNull("workspace_assets", "workspace_id");
    assertColumnNotNull("market_listings", "public_snapshot");
}
```

- [ ] **Step 2: Verify schema test fails**

Run: `cd aicp-backend && mvn -Dtest=AssetMarketSchemaTest test`

Expected: FAIL because the unified tables do not exist.

- [ ] **Step 3: Add tables, enums, entities, and mappers**

Define the exact enums:

```java
public enum AssetType { CHECKPOINT, LORA, STYLE_PACK, CHARACTER, SCENE, PROMPT }
public enum AccessScope { PRIVATE, WORKSPACE }
public enum AssetSource { CREATED, MARKET_CLAIMED, PROJECT_GENERATED, IMPORTED }
public enum AssetStatus { ACTIVE, ARCHIVED }
public enum ListingStatus { LISTED, UNLISTED, REMOVED }
public enum PublishStatus { PENDING, APPROVED, REJECTED, CANCELLED }
```

Create the seven tables from the approved design. Use `VARCHAR(64)` Workspace IDs, JSON-as-text fields compatible with H2/MySQL, `row_version INT NOT NULL DEFAULT 0`, and the unique constraints named `uk_entitlement_workspace_listing`, `uk_application_workspace_key`, `uk_favorite_user_workspace_listing`, and `uk_pending_publish_asset_version`. Remove the obsolete `market_assets` runtime definition after migrating its four seed rows into the new tables.

Insert seed data in both schema files covering all four asset categories:
- **4 style models** — migrate existing Mock data (韩漫风格—都市言情, 日系唯美—校园青春, 美式写实—科幻冒险, 国风古装—仙侠奇幻) into `workspace_assets` + `asset_versions` + `market_listings` under a platform seed Workspace `platform_seed`.
- **≥2 characters** — 都市男主角—青年, 校园女主角—少女.
- **≥2 scenes** — 现代都市街道, 教室与走廊.
- **≥2 prompts** — 韩漫都市对话提示词模板, 日系校园氛围提示词模板.
- All seed listings are `LISTED`, `license_type=FREE`, `price=0`. Use idempotent INSERT (e.g. `MERGE` or `INSERT ... WHERE NOT EXISTS`) so migration scripts are repeatable.

- [ ] **Step 4: Run schema and application-context tests**

Run: `cd aicp-backend && mvn -Dtest=AssetMarketSchemaTest test`

Expected: PASS on H2 MySQL mode.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset/domain aicp-backend/src/main/java/com/aicp/module/asset/entity aicp-backend/src/main/java/com/aicp/module/asset/mapper aicp-backend/src/main/resources/db/schema-h2.sql aicp-backend/src/main/resources/db/schema-mysql.sql aicp-backend/src/test/java/com/aicp/module/asset/schema
git commit -m "feat: add workspace asset market schema"
```

## Task 4: Implement public listing search and detail

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/dto/AssetViews.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/service/MarketQueryService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetMarketController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/MarketQueryServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetController.java`

- [ ] **Step 1: Write failing query tests**

```java
@Test void publicSearchReturnsOnlyListedSnapshots() {
    seedListing(1L, "LISTED", "韩漫都市", "[\"韩漫\"]", 20);
    seedListing(2L, "UNLISTED", "私有测试", "[\"韩漫\"]", 999);
    PageView<ListingCard> result = service.search(new ListingQuery("韩漫", "CHECKPOINT", "popular", 1, 20), Optional.empty());
    assertThat(result.items()).extracting(ListingCard::id).containsExactly(1L);
}
```

- [ ] **Step 2: Verify the test fails**

Run: `cd aicp-backend && mvn -Dtest=MarketQueryServiceTest test`

Expected: FAIL because `MarketQueryService` does not exist.

- [ ] **Step 3: Implement paginated snapshot queries**

Use a `LambdaQueryWrapper<MarketListing>` with `status=LISTED`, escaped keyword matching over snapshot name/author/tags, type filtering, and allow-listed sort values: `latest`, `popular`, `rating`, `relevance`. Return `ListingCard` and `ListingDetail` records, never `source_asset_id`. If a trusted Workspace context is present, add `claimed` using an entitlement existence query.

Keep old `GET /market/models|characters|scenes|prompts` methods as deprecated adapters that call `MarketQueryService`; remove all hardcoded maps and empty-array responses.

- [ ] **Step 4: Run query and controller tests**

Run: `cd aicp-backend && mvn -Dtest=MarketQueryServiceTest test`

Expected: PASS with stable pagination metadata and no private IDs in JSON.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset/dto/AssetViews.java aicp-backend/src/main/java/com/aicp/module/asset/service/MarketQueryService.java aicp-backend/src/main/java/com/aicp/module/asset/controller aicp-backend/src/test/java/com/aicp/module/asset
git commit -m "feat: add public asset listing queries"
```

## Task 5: Implement Workspace library and publishing approval

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/dto/AssetRequests.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetLibraryService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetPublicationService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetLibraryController.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetPublishController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/AssetPublicationServiceTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/AssetLibrarySecurityTest.java`

- [ ] **Step 1: Write failing personal and enterprise publication tests**

```java
@Test void personalOwnerPublishesDirectly() {
    WorkspaceContext ctx = personal("personal_7", 7L, "asset.manage");
    ListingView listing = service.publishPersonal(ctx, assetId, new PublishAssetRequest(versionId, publicInfo()));
    assertThat(listing.status()).isEqualTo("LISTED");
}

@Test void enterpriseMemberCannotBypassApproval() {
    WorkspaceContext ctx = enterprise("ent_100", 9L, "asset.publish.request");
    assertThatThrownBy(() -> service.publishPersonal(ctx, assetId, request)).isInstanceOf(BizException.class);
    assertThat(service.requestEnterprisePublish(ctx, assetId, request).status()).isEqualTo("PENDING");
}

@Test void createAndEditAssetInWorkspaceScope() {
    WorkspaceContext ctx = personal("personal_7", 7L, "asset.manage");
    AssetView created = service.create(ctx, new CreateAssetRequest("测试角色", "CHARACTER", "测试描述", List.of("测试"), "PRIVATE"));
    assertThat(created.workspaceId()).isEqualTo("personal_7");
    AssetView edited = service.edit(ctx, created.id(), new EditAssetRequest("更新名称", null, null, 0));
    assertThat(edited.name()).isEqualTo("更新名称");
}

@Test void archiveAndUnlistPreserveEntitlements() {
    WorkspaceContext ctx = personal("personal_7", 7L, "asset.manage");
    service.archive(ctx, assetId, assetRowVersion);
    assertThat(readAsset(assetId).status()).isEqualTo("ARCHIVED");
    service.unlist(ctx, assetId, listingRowVersion);
    assertThat(readListing(listingId).status()).isEqualTo("UNLISTED");
}

@Test void createVersionSnapshotsImmutableCopy() {
    WorkspaceContext ctx = personal("personal_7", 7L, "asset.manage");
    VersionView v1 = readAsset(assetId).currentVersion();
    VersionView v2 = service.createVersion(ctx, assetId, new CreateVersionRequest(metadata, previewUrl));
    assertThat(v2.versionNumber()).isGreaterThan(v1.versionNumber());
    assertThat(v1.metadata()).isNotEqualTo(v2.metadata()); // v1 unchanged
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd aicp-backend && mvn -Dtest=AssetPublicationServiceTest test`

Expected: FAIL because publication services do not exist.

- [ ] **Step 3: Implement tenant-safe library and state transitions**

Every private lookup must use both `asset_id` and `workspace_id`. Implement create, edit, version, archive, personal publish, enterprise request, approve, reject, cancel, and unlist. Approval must execute:

```java
@Transactional
public ListingView approve(WorkspaceContext context, long requestId, ReviewRequest body) {
    context.require("asset.publish.approve");
    AssetPublishRequest request = requirePendingRequest(context.workspaceId(), requestId, body.rowVersion());
    MarketListing listing = upsertListingSnapshot(request);
    markApproved(request, context.userId());
    audit("ASSET_PUBLISH_APPROVED", context, request.getAssetId(), listing.getId());
    return toView(listing);
}
```

Reject requires a nonblank reason. Archive is forbidden while a request is pending. Unlisting preserves entitlements.

- [ ] **Step 4: Run publication and cross-tenant tests**

Run: `cd aicp-backend && mvn -Dtest=AssetPublicationServiceTest,AssetLibrarySecurityTest test`

Expected: PASS; cross-Workspace IDs return the same not-found error as absent IDs.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset/dto aicp-backend/src/main/java/com/aicp/module/asset/service aicp-backend/src/main/java/com/aicp/module/asset/controller aicp-backend/src/test/java/com/aicp/module/asset
git commit -m "feat: add asset library publishing workflow"
```

## Task 6: Implement idempotent free claiming and favorites

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetClaimService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/AssetClaimServiceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetMarketController.java`

- [ ] **Step 1: Write failing transaction and idempotency tests**

```java
@Test void repeatedClaimReturnsTheSameWorkspaceAsset() {
    ClaimView first = service.claim(context, listingId);
    ClaimView second = service.claim(context, listingId);
    assertThat(second.workspaceAssetId()).isEqualTo(first.workspaceAssetId());
    assertThat(entitlementCount(context.workspaceId(), listingId)).isOne();
}

@Test void unlistedAssetCannotBeClaimedButOldEntitlementSurvives() {
    ClaimView existing = service.claim(context, listingId);
    unlist(listingId);
    assertThat(service.claim(context, listingId).workspaceAssetId()).isEqualTo(existing.workspaceAssetId());
    assertThatThrownBy(() -> service.claim(otherContext, listingId)).isInstanceOf(BizException.class);
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd aicp-backend && mvn -Dtest=AssetClaimServiceTest test`

Expected: FAIL because claim behavior does not exist.

- [ ] **Step 3: Implement one-transaction claim and user-scoped favorites**

Within one transaction: look up existing entitlement first; otherwise lock/read a `LISTED` Listing, create entitlement, clone the immutable public snapshot into a `MARKET_CLAIMED` Workspace asset and version, then increment use count. Handle unique-key races by re-reading the winning entitlement. Favorite uniqueness is `(user_id, workspace_id, listing_id)`.

- [ ] **Step 4: Run claiming tests**

Run: `cd aicp-backend && mvn -Dtest=AssetClaimServiceTest test`

Expected: PASS, including concurrent duplicate claims.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset/service/AssetClaimService.java aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetMarketController.java aicp-backend/src/test/java/com/aicp/module/asset/service/AssetClaimServiceTest.java
git commit -m "feat: add idempotent free asset claims"
```

## Task 7: Apply assets to Workspace projects and support undo

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetApplicationService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/AssetApplicationServiceTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/service/CanvasServiceWorkspaceTest.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/entity/CanvasProject.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/canvas/service/CanvasService.java`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/java/com/aicp/module/asset/controller/AssetLibraryController.java`

- [ ] **Step 1: Write failing application tests**

```java
@Test void styleApplicationUpdatesAndRestoresStyleConfig() {
    ApplyView applied = service.apply(context, assetId, new ApplyAssetRequest(projectId, "PROJECT", null, "apply-1"));
    assertThat(readProject(projectId).getStyleConfig()).contains("korean-manhwa");
    service.undo(context, applied.applicationId(), applied.undoToken());
    assertThat(readProject(projectId).getStyleConfig()).isEqualTo(originalStyleJson);
}

@Test void refusesProjectFromAnotherWorkspace() {
    assertThatThrownBy(() -> service.apply(personalContext, assetId, requestForEnterpriseProject))
        .isInstanceOf(BizException.class).hasMessageContaining("资源不存在");
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd aicp-backend && mvn -Dtest=AssetApplicationServiceTest test`

Expected: FAIL because application service and project Workspace IDs do not exist.

- [ ] **Step 3: Add `workspace_id`, application strategies, and undo**

Backfill existing projects deterministically: `enterprise:{enterprise_id}` when present, otherwise `personal:{user_id}`. New projects always take the trusted context Workspace ID.

Use one strategy per asset family:

```java
return switch (asset.getAssetType()) {
    case "CHECKPOINT", "LORA", "STYLE_PACK" -> applyStyle(project, version);
    case "CHARACTER" -> appendProjectAssetRef(project, "character_asset_ids", asset.getId());
    case "SCENE" -> appendProjectAssetRef(project, "scene_asset_ids", asset.getId());
    case "PROMPT" -> appendProjectPrompt(project, version);
    default -> throw new BizException(ErrorCode.ASSET_TYPE_UNSUPPORTED);
};
```

Store `previous_state`, `change_summary`, and a random hashed undo token. Undo checks the same Workspace, permission, token, and project revision; conflicting later edits return `409` instead of overwriting.

- [ ] **Step 4: Run application and Canvas regression tests**

Run: `cd aicp-backend && mvn -Dtest=AssetApplicationServiceTest,CanvasServiceWorkspaceTest test`

Expected: PASS for four asset families, idempotent retry, undo, and cross-Workspace rejection.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/asset aicp-backend/src/main/java/com/aicp/module/canvas aicp-backend/src/main/resources/db/schema-h2.sql aicp-backend/src/main/resources/db/schema-mysql.sql aicp-backend/src/test/java/com/aicp/module/asset
git commit -m "feat: apply workspace assets to canvas projects"
```

## Task 8: Add stable asset-market errors and API compatibility

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/common/exception/ErrorCode.java`
- Modify: `aicp-backend/src/main/java/com/aicp/common/exception/GlobalExceptionHandler.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/controller/AssetMarketControllerTest.java`
- Modify: `aicp-frontend/src/api/asset.js`

- [ ] **Step 1: Write failing HTTP contract tests**

```java
@Test void crossTenantAssetIs404AndPermissionFailureIs403() throws Exception {
    mvc.perform(get("/api/v1/asset/library/999").header("X-Workspace-Id", "ent_other"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(48001));
    mvc.perform(post("/api/v1/asset/publish-requests/4/approve").header("X-Workspace-Id", "ent_100"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(48002));
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd aicp-backend && mvn -Dtest=AssetMarketControllerTest test`

Expected: FAIL because `48xxx` mappings are absent.

- [ ] **Step 3: Add codes and replace the frontend API**

Define `ASSET_NOT_FOUND(48001)`, `ASSET_PERMISSION_DENIED(48002)`, `LISTING_UNAVAILABLE(48003)`, `ASSET_VERSION_CONFLICT(48004)`, `ASSET_INCOMPATIBLE(48005)`, `PUBLISH_STATE_CONFLICT(48006)`, and `ASSET_TYPE_UNSUPPORTED(48007)`. Map `48001→404`, `48002→403`, `48003/48004/48006→409`, and `48005/48007→422`.

Export the following frontend methods from `aicp-frontend/src/api/asset.js`:

```js
// 公共市场
export function listMarket(params)           // GET  /market/listings
export function getListingDetail(id)        // GET  /market/listings/{id}
export function claimListing(id)            // POST /market/listings/{id}/claim
export function favoriteListing(id)         // PUT  /market/listings/{id}/favorite
export function unfavoriteListing(id)       // DELETE /market/listings/{id}/favorite

// Workspace 资产库
export function listLibrary(params)         // GET  /library
export function createAsset(body)           // POST /library
export function getLibraryAsset(id)         // GET  /library/{id}
export function editLibraryAsset(id, body)  // PUT  /library/{id}
export function createAssetVersion(id, body)// POST /library/{id}/versions
export function archiveAsset(id)            // POST /library/{id}/archive
export function publishAsset(id, body)      // POST /library/{id}/publish
export function unlistAsset(id)             // POST /library/{id}/unlist
export function applyAsset(id, body)        // POST /library/{id}/applications
export function undoApplication(id, body)   // POST /applications/{id}/undo

// 企业审批
export function requestPublish(id, body)    // POST /library/{id}/publish-requests
export function listPublishRequests(params) // GET  /publish-requests
export function getPublishRequest(id)       // GET  /publish-requests/{id}
export function approveRequest(id, body)    // POST /publish-requests/{id}/approve
export function rejectRequest(id, body)     // POST /publish-requests/{id}/reject
export function cancelRequest(id)           // POST /publish-requests/{id}/cancel
```

Preserve old method names as wrappers during migration, for example `getModels(params) => listMarket({ ...params, type: 'checkpoint' })`.

- [ ] **Step 4: Run controller tests and frontend build**

Run: `cd aicp-backend && mvn -Dtest=AssetMarketControllerTest test && cd ../aicp-frontend && npm run build`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/common/exception aicp-backend/src/test/java/com/aicp/module/asset/controller aicp-frontend/src/api/asset.js
git commit -m "feat: stabilize asset market API contracts"
```

## Task 9: Build frontend state helpers and composable

**Files:**
- Create: `aicp-frontend/src/views/asset-market/assetMarketState.js`
- Create: `aicp-frontend/src/views/asset-market/useAssetMarket.js`
- Create: `aicp-frontend/tests/asset-market-state.test.js`
- Create: `aicp-frontend/tests/asset-market-api.test.js`

- [ ] **Step 1: Write failing pure-state tests**

```js
test('audio categories are disabled in this release', () => {
  assert.equal(categoryState('voice').disabled, true)
  assert.equal(categoryState('bgm').label, '即将开放')
})

test('enterprise approval tab requires mapped permission', () => {
  assert.equal(canReview({ permissions: ['asset.view'] }), false)
  assert.equal(canReview({ permissions: ['asset.publish.approve'] }), true)
})

test('query serialization drops empty values and keeps page size', () => {
  assert.deepEqual(toListingParams({ keyword: '', type: 'character', page: 2 }), { type: 'character', page: 2, page_size: 20 })
})
```

- [ ] **Step 2: Verify tests fail**

Run: `cd aicp-frontend && node --test tests/asset-market-state.test.js tests/asset-market-api.test.js`

Expected: FAIL because the helpers do not exist.

- [ ] **Step 3: Implement pure helpers and async state**

`useAssetMarket` owns separate `{data, loading, error}` states for listings, detail, library, and publish requests. It synchronizes filters with route query, ignores stale responses using a request sequence, and exposes `claim`, `favorite`, `publish`, `review`, `apply`, and `undo` actions. It must not keep permissions in hardcoded role-name checks; use the permissions returned for the current 8080 context.

- [ ] **Step 4: Run Node tests**

Run: `cd aicp-frontend && node --test tests/asset-market-state.test.js tests/asset-market-api.test.js`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/asset-market aicp-frontend/tests/asset-market-state.test.js aicp-frontend/tests/asset-market-api.test.js
git commit -m "feat: add asset market frontend state"
```

## Task 10: Replace the mock market page with the complete UI

**Files:**
- Create: `aicp-frontend/src/views/asset-market/components/AssetFilterBar.vue`
- Create: `aicp-frontend/src/views/asset-market/components/AssetCard.vue`
- Create: `aicp-frontend/src/views/asset-market/components/PublicMarketPanel.vue`
- Create: `aicp-frontend/src/views/asset-market/components/WorkspaceAssetPanel.vue`
- Create: `aicp-frontend/src/views/asset-market/components/AssetDetailDrawer.vue`
- Create: `aicp-frontend/src/views/asset-market/components/AssetEditorDialog.vue`
- Create: `aicp-frontend/src/views/asset-market/components/PublishRequestPanel.vue`
- Create: `aicp-frontend/src/views/asset-market/components/PublishReviewDrawer.vue`
- Create: `aicp-frontend/src/views/asset-market/components/ApplyAssetDialog.vue`
- Create: `aicp-frontend/tests/asset-market-view.test.js`
- Modify: `aicp-frontend/src/views/AssetMarket.vue`

- [ ] **Step 1: Add a failing structural test**

```js
test('market shell contains the four approved channels and no hardcoded assets', () => {
  const source = readFileSync(new URL('../src/views/AssetMarket.vue', import.meta.url), 'utf8')
  for (const component of ['PublicMarketPanel', 'WorkspaceAssetPanel', 'PublishRequestPanel']) assert.match(source, new RegExp(component))
  assert.doesNotMatch(source, /韩漫风格 — 都市言情/)
})
```

- [ ] **Step 2: Verify the test fails**

Run: `cd aicp-frontend && node --test tests/asset-market-view.test.js`

Expected: FAIL because the page is still the hardcoded card grid.

- [ ] **Step 3: Build the approved page structure**

Keep `AssetMarket.vue` as the tab shell. Components must provide skeleton loading, empty results, retry actions, server pagination, public detail, free claim, favorite, Workspace library filters, personal direct publish, enterprise request status, permission-controlled approval, and the disabled voice/BGM categories. Use shared global styles and Element Plus; do not introduce a second design system.

- [ ] **Step 4: Run UI tests and production build**

Run: `cd aicp-frontend && node --test tests/asset-market-*.test.js && npm run build`

Expected: all tests PASS and Vite build succeeds without warnings from missing component imports.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/AssetMarket.vue aicp-frontend/src/views/asset-market/components aicp-frontend/tests/asset-market-view.test.js
git commit -m "feat: build complete AI asset market UI"
```

## Task 11: Add Workspace asset reuse to the canvas

**Files:**
- Create: `aicp-frontend/src/views/canvas/components/WorkspaceAssetPicker.vue`
- Modify: `aicp-frontend/src/views/Canvas.vue`
- Create: `aicp-frontend/tests/canvas-asset-picker.test.js`

- [ ] **Step 1: Write a failing Canvas integration test**

```js
test('canvas exposes the workspace asset picker and handles all supported types', () => {
  const source = readFileSync(new URL('../src/views/Canvas.vue', import.meta.url), 'utf8')
  assert.match(source, /WorkspaceAssetPicker/)
  for (const type of ['character', 'scene', 'prompt', 'style']) assert.match(source, new RegExp(type))
})
```

- [ ] **Step 2: Verify the test fails**

Run: `cd aicp-frontend && node --test tests/canvas-asset-picker.test.js`

Expected: FAIL because the picker is absent.

- [ ] **Step 3: Implement picker and application feedback**

Add “Workspace 资产” to the Canvas toolbar. The picker queries `/asset/library`, filters the four supported families, calls the application endpoint with `crypto.randomUUID()` as the idempotency key, refreshes Canvas project/nodes after success, and offers an undo toast using the returned `application_id` and `undo_token`.

- [ ] **Step 4: Run Canvas tests and build**

Run: `cd aicp-frontend && node --test tests/canvas-asset-picker.test.js tests/node-editor-data.test.js tests/floating-editor-position.test.js && npm run build`

Expected: PASS and successful build.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/views/canvas/components/WorkspaceAssetPicker.vue aicp-frontend/src/views/Canvas.vue aicp-frontend/tests/canvas-asset-picker.test.js
git commit -m "feat: reuse workspace assets from canvas"
```

## Task 12: Run tenant-security E2E, migration verification, and full regression

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/AssetMarketLifecycleE2ETest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/asset/AssetMarketSecurityIntegrationTest.java`
- Modify: `docs/01-core/API接口文档_V1.5.md`
- Modify: `docs/01-core/用户端PRD.md`
- Modify: `docs/01-core/后端产品功能设计_V1.5.md`

- [ ] **Step 1: Write the two end-to-end scenarios**

```java
@Test void personalPublishClaimApplyUndoLifecycle() {
    long listing = personalUserCreatesAndPublishesStyle();
    long buyerAsset = otherWorkspaceClaims(listing);
    long application = buyerAppliesToOwnProject(buyerAsset);
    buyerUndoes(application);
    assertProjectStyleRestored();
}

@Test void enterpriseApprovalAndCrossTenantIsolation() {
    long request = memberSubmitsEnterpriseAsset();
    assertApprovalDeniedForOrdinaryMember(request);
    adminApproves(request);
    assertPublicListingVisible();
    assertPrivateAssetHiddenFromOtherWorkspaceEvenWithTamperedHeader();
}
```

- [ ] **Step 2: Run E2E tests and verify any remaining failures**

Run: `cd aicp-backend && mvn -Dtest=AssetMarketLifecycleE2ETest,AssetMarketSecurityIntegrationTest test`

Expected before final fixes: failures identify only integration wiring, seed, or response-contract gaps; no test may be disabled.

- [ ] **Step 3: Fix wiring and update the canonical documents**

Update the following three documents to reflect the completed asset market:

**`docs/01-core/API接口文档_V1.5.md`:**
- Replace old `/market/models|characters|scenes|prompts` with the new endpoint table (design sections 8.1–8.3).
- Document `X-Workspace-Id` header requirement for protected endpoints.
- Add pagination request/response format.
- Add all business error codes (48001–48007) and their HTTP mappings.
- Mark old endpoints as deprecated with migration paths.

**`docs/01-core/用户端PRD.md`:**
- Add asset market channels: public market, Workspace library, publish management, approval center.
- Document personal vs enterprise publishing workflow differences.
- Add free claim → library → apply → undo user flow.
- Note voice/BGM as "即将开放" with disabled state.
- Remove any references to Mock/hardcoded data being production behavior.

**`docs/01-core/后端产品功能设计_V1.5.md`:**
- Document Workspace isolation rules (design section 4.3).
- Add permission mapping table (design section 4.2).
- Document the `WorkspaceContextFilter` and `3001` contract dependency.
- Add domain model with the seven tables.
- Document seed data strategy.
- Remove statements claiming that `AssetController` Mock responses are production behavior.

- [ ] **Step 4: Run the complete verification suite**

Run:

```bash
cd new-api && go test ./... -count=1
cd ../aicp-backend && mvn test
cd ../aicp-frontend && node --test tests/*.test.js && npm run build
git diff --check
```

Expected: all Go, Maven, and Node tests PASS; Vite build succeeds; `git diff --check` prints nothing.

- [ ] **Step 5: Commit final integration and docs**

```bash
git add aicp-backend/src/test/java/com/aicp/module/asset docs/01-core/API接口文档_V1.5.md docs/01-core/用户端PRD.md docs/01-core/后端产品功能设计_V1.5.md
git commit -m "test: verify tenant-safe asset market lifecycle"
```

## Completion checklist

- Public results are driven only by owner-controlled `LISTED` records.
- Enterprise assets cannot become public without `asset.publish.approve`.
- No private asset, entitlement, favorite, request, application, or project lookup omits Workspace scope.
- Claims and applications are idempotent.
- Unlisting blocks new claims but preserves existing entitlements.
- Style, character, scene, and prompt assets all reach real project state.
- Voice and BGM remain visibly disabled.
- All user operations stay in `8080`; permission truth comes from `3001` and fails closed.
