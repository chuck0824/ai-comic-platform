# Scene Assets and Eight-Stage Script Workbench Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge the complete eight-stage script workflow into the native `/script-gen/:projectId/workspace` Vue page, add a project-scoped scene asset library with immutable version references, keep `/script-gen` as the creation launchpad, and synchronize the static acceptance prototype and product documentation.

**Architecture:** Reuse `workspace_assets` and `asset_versions` for project-scoped scene masters and immutable versions; store scene variants as versioned metadata owned by a master asset. Add project-scoped scene-asset endpoints that enforce content-project permissions, and lock scene asset/version/variant snapshots onto content-project storyboard shots. In the frontend, replace the legacy generic workspace body with a pure eight-stage state model, small stage components, shared action-feedback overlays, and a scene-asset composable. Keep the static HTML as an acceptance oracle, not as a second runtime implementation.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Flyway-style SQL migrations, H2/MySQL schemas, Vue 3 Composition API, Vue Router, Element Plus, Axios, Node.js built-in test runner, static HTML/CSS/JavaScript, Markdown/Obsidian.

## Repository Map and Constraints

- Work only in `/Users/apple/Desktop/漫剧/平台/.worktrees/script-action-feedback` on branch `codex/script-action-feedback-completion`.
- Preserve `/script-gen` as `ScriptCreationHome.vue`; it remains the four-entry launchpad.
- Mount the unified workbench at `/script-gen/:projectId/workspace`; do not use an iframe.
- Keep exactly eight stages: creation settings, novel upload, novel analysis, adaptation, structured script, script body, review, and text storyboard.
- Scene assets are a project-level shared capability, not a ninth stage and not a global marketplace in this delivery.
- Business actions remain clickable. Failed prerequisites open actionable guidance; successful actions create revisitable task/version/result records.
- Demo models cost 0 points. Models loaded from port 3001 use the same point-consumption and estimate/settlement rules already documented by the platform.
- Novel paste input is limited to 2,000 Chinese characters; file upload remains the path for longer source material.
- Every artifact mutation creates or updates the corresponding Markdown representation and marks dependent artifacts stale where the design specifies.
- Do not claim a real Obsidian file write, model call, canvas creation, or asset-market publication unless the corresponding API succeeds.

## Target File Structure

```text
aicp-backend/src/main/java/com/aicp/module/contentproject/
├── controller/ProjectSceneAssetController.java
├── dto/ProjectSceneAssetRequests.java
├── dto/ProjectSceneAssetViews.java
└── service/
    ├── ProjectSceneAssetService.java
    └── SceneAssetMarkdownProjector.java

aicp-backend/src/main/java/com/aicp/module/storyboard/
├── controller/StoryboardEditingController.java
├── dto/StoryboardRequests.java
├── dto/StoryboardViews.java
├── entity/StoryboardShot.java
└── service/StoryboardEditingService.java

aicp-frontend/src/views/content-project/
├── ContentProjectWorkspace.vue
├── components/
│   ├── ActionGuidanceDialog.vue
│   ├── ActionResultDrawer.vue
│   ├── GenerationProgressDialog.vue
│   ├── SceneAssetDetailDrawer.vue
│   ├── SceneAssetLibrary.vue
│   └── SceneAssetPicker.vue
├── stages/
│   ├── CreationSettingsStage.vue
│   ├── NovelUploadStage.vue
│   ├── NovelAnalysisStage.vue
│   ├── AdaptationStage.vue
│   ├── StructuredScriptStage.vue
│   ├── ScriptBodyStage.vue
│   ├── ReviewRevisionStage.vue
│   └── TextStoryboardStage.vue
└── workbench/
    ├── scriptWorkbenchModel.js
    ├── sceneAssetModel.js
    ├── sceneAssetMarkdown.js
    ├── useSceneAssets.js
    └── useScriptWorkbench.js
```

---

### Task 1: Project-Scoped Scene Asset Contracts and Lifecycle

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ProjectSceneAssetRequests.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ProjectSceneAssetViews.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ProjectSceneAssetController.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectSceneAssetService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/asset/dto/AssetViews.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/asset/service/AssetLibraryService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/ProjectSceneAssetLifecycleE2ETest.java`

**Interfaces:**
- `GET /api/v1/content-projects/{projectId}/scene-assets?keyword=&space_type=&reusability=&status=&referenced=`
- `POST /api/v1/content-projects/{projectId}/scene-assets`
- `GET /api/v1/content-projects/{projectId}/scene-assets/{assetId}`
- `PATCH /api/v1/content-projects/{projectId}/scene-assets/{assetId}`
- `POST /api/v1/content-projects/{projectId}/scene-assets/{assetId}/versions/{versionId}/restore`
- `POST /api/v1/content-projects/{projectId}/scene-assets/{assetId}/archive`
- `GET /api/v1/content-projects/{projectId}/scene-assets/{assetId}/impact`
- Writes `WorkspaceAsset.assetType = "SCENE"`, `sourceType = "PROJECT_GENERATED"`, and `contentProjectId = projectId`.
- Stores the normalized scene-master payload and `variants[]` in immutable `AssetVersion.metadata` JSON.

- [ ] **Step 1: Write the failing lifecycle test**

```java
@Test
void sceneAssetIsProjectScopedVersionedAndRestorable() throws Exception {
    long projectId = fixture.createProject(ownerId, "场景资产测试");
    String created = mvc.perform(post("/api/v1/content-projects/{id}/scene-assets", projectId)
            .with(user(ownerId))
            .contentType(APPLICATION_JSON)
            .content("""
                {"name":"青桥城中村出租屋","space_type":"INTERIOR",
                 "reusability":"PRIMARY","reality_type":"REALISTIC",
                 "layout":"一室一厅，门口正对客厅","continuity_rules":["窗在东墙"]}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content_project_id").value(projectId))
        .andExpect(jsonPath("$.data.asset_type").value("SCENE"))
        .andExpect(jsonPath("$.data.current_version_no").value(1))
        .andReturn().getResponse().getContentAsString();

    long assetId = JsonPath.read(created, "$.data.id");
    mvc.perform(patch("/api/v1/content-projects/{projectId}/scene-assets/{assetId}", projectId, assetId)
            .with(user(ownerId)).contentType(APPLICATION_JSON)
            .content("{\"lighting\":\"深夜冷色顶灯\",\"change_note\":\"补充夜景基础灯光\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.current_version_no").value(2));

    mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets", projectId)
            .with(user(otherUserId)))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn -Dtest=ProjectSceneAssetLifecycleE2ETest test`

Working directory: `aicp-backend`

Expected: FAIL because the controller, DTOs, and service do not exist.

- [ ] **Step 3: Implement DTO validation and normalized metadata**

Define `CreateSceneAssetRequest`, `UpdateSceneAssetRequest`, `CreateVariantRequest`, and `RestoreSceneAssetRequest`. Require `name`, `spaceType`, `reusability`, and `realityType`; accept layout, materials, palette, lighting, landmarks, fixed/movable props, entrances/exits, continuity rules, references, prompts, and world-location reference. Define response records with snake-case JSON properties matching the existing API style.

Use one metadata envelope on every version:

```json
{
  "schema_version": 1,
  "master": {
    "world_location_ref": "WORLD-LOC-003",
    "space_type": "INTERIOR",
    "reusability": "PRIMARY",
    "reality_type": "REALISTIC",
    "layout": "一室一厅，门口正对客厅",
    "continuity_rules": ["窗在东墙"]
  },
  "variants": [
    {"id":"VAR-001","version":1,"name":"深夜停电","time":"NIGHT","lighting_delta":"仅应急灯"}
  ]
}
```

- [ ] **Step 4: Implement project scoping, versioning, archive, restore, and impact summary**

Call `ProjectAccessService.require(projectId, userId, Action.VIEW|EDIT_CONTENT)` in every endpoint. Query by both `id` and `contentProjectId`; never trust an asset ID alone. An update appends an `AssetVersion`, advances `currentVersionId`, and increments the exposed version number. Archive is allowed only when impact reports zero locked references; otherwise return a business error instructing replacement or deactivation. Restore creates a new current version from the historical payload rather than mutating history.

- [ ] **Step 5: Run focused and asset regression tests**

Run: `mvn -Dtest=ProjectSceneAssetLifecycleE2ETest,AssetWorkbenchLifecycleE2ETest,AssetMarketLifecycleE2ETest test`

Expected: all selected tests PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject aicp-backend/src/main/java/com/aicp/module/asset aicp-backend/src/test/java/com/aicp/module/contentproject/ProjectSceneAssetLifecycleE2ETest.java
git commit -m "feat: add project scene asset lifecycle"
```

---

### Task 2: Scene Variants, World-Location Conversion, and Markdown Projection

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/controller/ProjectSceneAssetController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ProjectSceneAssetRequests.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/dto/ProjectSceneAssetViews.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectSceneAssetService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/SceneAssetMarkdownProjector.java`
- Modify: `aicp-backend/src/test/java/com/aicp/module/contentproject/ProjectSceneAssetLifecycleE2ETest.java`

**Interfaces:**
- `POST /api/v1/content-projects/{projectId}/scene-assets/from-location`
- `POST /api/v1/content-projects/{projectId}/scene-assets/{assetId}/variants`
- `PATCH /api/v1/content-projects/{projectId}/scene-assets/{assetId}/variants/{variantId}`
- `GET /api/v1/content-projects/{projectId}/scene-assets/{assetId}/markdown`
- Produces deterministic Obsidian paths under `04-场景资产/` and real `[[双链]]` references.

- [ ] **Step 1: Extend the failing test with conversion, variant, and Markdown assertions**

```java
@Test
void locationConversionCreatesVariantAndObsidianProjection() throws Exception {
    long projectId = fixture.createProject(ownerId, "地点转换测试");
    long assetId = convertLocation(projectId, "WORLD-LOC-003", "青桥城中村出租屋");

    mvc.perform(post("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/variants", projectId, assetId)
            .with(user(ownerId)).contentType(APPLICATION_JSON)
            .content("{\"name\":\"深夜停电\",\"time\":\"NIGHT\",\"lighting_delta\":\"仅应急灯\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.variants[0].id").value("VAR-001"))
        .andExpect(jsonPath("$.data.current_version_no").value(2));

    mvc.perform(get("/api/v1/content-projects/{projectId}/scene-assets/{assetId}/markdown", projectId, assetId)
            .with(user(ownerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.path").value("04-场景资产/SCENE-ASSET-001-青桥城中村出租屋.md"))
        .andExpect(jsonPath("$.data.content", containsString("[[03-小说分析/世界观/主要地点]]")))
        .andExpect(jsonPath("$.data.content", containsString("VAR-001")));
}
```

- [ ] **Step 2: Run the focused method and verify RED**

Run: `mvn -Dtest=ProjectSceneAssetLifecycleE2ETest#locationConversionCreatesVariantAndObsidianProjection test`

Expected: FAIL because conversion, variants, and Markdown projection are absent.

- [ ] **Step 3: Implement immutable variant mutations**

Generate project-stable IDs (`SCENE-ASSET-%03d`, `VAR-%03d`). A variant update copies the current metadata envelope, changes only the selected variant delta, increments that variant's version, and creates a new master asset version. Do not copy master fields into a variant.

- [ ] **Step 4: Implement deterministic Markdown projection**

`SceneAssetMarkdownProjector` returns path/content without claiming a filesystem write. Frontmatter includes project ID, asset stable ID, asset version, status, space type, reuse level, source location, and updated time. The body contains the complete master setting, variants, continuity rules, references, and links to known source/consumer Markdown paths. Escape YAML and filenames and normalize `/`, `\\`, `..`, `#`, `[` and `]` from user-controlled names.

- [ ] **Step 5: Run tests and commit**

Run: `mvn -Dtest=ProjectSceneAssetLifecycleE2ETest test`

```bash
git add aicp-backend/src/main/java/com/aicp/module/contentproject aicp-backend/src/test/java/com/aicp/module/contentproject/ProjectSceneAssetLifecycleE2ETest.java
git commit -m "feat: version scene variants and markdown projections"
```

---

### Task 3: Lock Scene Asset Snapshots onto Storyboard Shots

**Files:**
- Create: `aicp-backend/src/main/resources/db/migration/V16__scene_asset_storyboard_snapshots.sql`
- Create: `aicp-backend/src/main/resources/db/migration/V16_undo.sql`
- Modify: `aicp-backend/src/main/resources/db/schema.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/java/com/aicp/module/storyboard/entity/StoryboardShot.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/storyboard/dto/StoryboardRequests.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/storyboard/dto/StoryboardViews.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/storyboard/controller/StoryboardEditingController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/storyboard/service/StoryboardEditingService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/contentproject/service/ProjectSceneAssetService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/contentproject/StoryboardSceneAssetSnapshotE2ETest.java`

**Interfaces:**
- Adds `scene_asset_id BIGINT`, `scene_asset_version_id BIGINT`, `scene_variant_id VARCHAR(64)`, `scene_variant_version INT`, and `scene_asset_snapshot TEXT/JSON` to the active V2 table `storyboard_version_shots`.
- `PUT /api/v1/content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}/shots/{shotId}/scene-asset`
- `POST /api/v1/content-projects/{projectId}/storyboards/{storyboardId}/versions/{versionId}/continuity-check`
- Locking a storyboard requires every production shot to have an immutable scene-asset snapshot.

- [ ] **Step 1: Write the failing snapshot and gate tests**

```java
@Test
void shotKeepsOldSnapshotWhenSceneAssetAdvances() throws Exception {
    Fixture f = fixture.projectWithStoryboardAndSceneAsset();
    bindShot(f.projectId(), f.storyboardId(), f.versionId(), f.shotId(),
            f.assetId(), f.versionOneId(), "VAR-001", 1);
    updateSceneAssetToVersionTwo(f.projectId(), f.assetId());

    mvc.perform(get("/api/v1/content-projects/{p}/storyboards/{s}/versions/{v}/shots",
                    f.projectId(), f.storyboardId(), f.versionId())
            .with(user(ownerId)))
        .andExpect(jsonPath("$.data[0].sceneAssetVersionId").value(f.versionOneId()))
        .andExpect(jsonPath("$.data[0].sceneAssetSnapshot.master.version").value(1));
}

@Test
void storyboardLockExplainsMissingSceneAssetBindings() throws Exception {
    Fixture f = fixture.projectWithStoryboardOnly();
    mvc.perform(post("/api/v1/content-projects/{p}/storyboards/{s}/versions/{v}/lock",
                    f.projectId(), f.storyboardId(), f.versionId())
            .with(user(ownerId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("场景资产")));
}
```

- [ ] **Step 2: Run and verify RED**

Run: `mvn -Dtest=StoryboardSceneAssetSnapshotE2ETest test`

Expected: FAIL because schema columns and binding endpoint do not exist.

- [ ] **Step 3: Add compatible schema changes**

Use nullable columns so existing storyboards continue to load. Add indexes on `scene_asset_id` and `scene_asset_version_id`. Update all three schema baselines plus migration/undo. Map the fields in `StoryboardShot`; serialize the snapshot consistently with the database profile.

- [ ] **Step 4: Implement binding, continuity, and lock gate**

Binding validates project, storyboard, storyboard version, shot, asset, asset version, and variant ownership. Build a canonical snapshot containing scene-master ID/name/version/path, variant ID/name/version, per-scene override, continuity rules, and final prompt fragment. The continuity endpoint reports `MISSING_ASSET`, `STALE_ASSET`, `VARIANT_MISMATCH`, and `FIXED_PROP_CONFLICT`. Lock rejects missing or invalid bindings with IDs and repair actions; an asset update never mutates locked shot snapshots.

- [ ] **Step 5: Run database, storyboard, and lifecycle regressions**

Run: `mvn -Dtest=StoryboardSceneAssetSnapshotE2ETest,ProjectSceneAssetLifecycleE2ETest,ContentProjectIntegrationTest test`

Expected: all selected tests PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/resources aicp-backend/src/main/java/com/aicp/module/contentproject aicp-backend/src/main/java/com/aicp/module/storyboard aicp-backend/src/test/java/com/aicp/module/contentproject
git commit -m "feat: lock scene asset snapshots on storyboards"
```

---

### Task 4: Frontend Scene Asset Model, API, and Markdown Preview

**Files:**
- Create: `aicp-frontend/src/api/sceneAsset.js`
- Create: `aicp-frontend/src/views/content-project/workbench/sceneAssetModel.js`
- Create: `aicp-frontend/src/views/content-project/workbench/sceneAssetMarkdown.js`
- Create: `aicp-frontend/src/views/content-project/workbench/useSceneAssets.js`
- Create: `aicp-frontend/tests/scene-asset-model.test.js`

**Interfaces:**
- `sceneAssetApi.list/create/get/update/createFromLocation/createVariant/updateVariant/restore/archive/impact/markdown`
- `normalizeSceneAsset(raw)`, `validateSceneAssetDraft(draft)`, `mergeSceneAssetVariant(master, variant)`, `classifySceneAssetChange(before, after)`
- `classifySceneAssetChange` returns `{ visualChange, downstreamStatus, affectedScopes }` and only visual/continuity changes yield `STALE`.

- [ ] **Step 1: Write failing pure-model tests**

```js
test('variant keeps deltas while resolving a production snapshot', () => {
  const resolved = mergeSceneAssetVariant(
    { id: 7, version: 2, lighting: '自然光', palette: ['灰'], fixedProps: ['旧木桌'] },
    { id: 'VAR-001', version: 1, lightingDelta: '仅应急灯', eventState: '停电' }
  )
  assert.equal(resolved.masterVersion, 2)
  assert.equal(resolved.variantVersion, 1)
  assert.equal(resolved.lighting, '仅应急灯')
  assert.deepEqual(resolved.fixedProps, ['旧木桌'])
})

test('management-only changes do not stale downstream scenes', () => {
  const change = classifySceneAssetChange(
    { name: '出租屋', tags: ['主场景'], lighting: '自然光' },
    { name: '青桥出租屋', tags: ['主场景', '常驻'], lighting: '自然光' }
  )
  assert.equal(change.visualChange, false)
  assert.equal(change.downstreamStatus, 'CURRENT')
})
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test tests/scene-asset-model.test.js`

Working directory: `aicp-frontend`

Expected: FAIL because the modules do not exist.

- [ ] **Step 3: Implement API mapping and pure model**

Keep API snake_case conversion at the boundary and camelCase inside Vue. Validation returns field-level messages rather than booleans. `mergeSceneAssetVariant` produces the exact snapshot shape used by the storyboard binding endpoint. Do not infer an upload success from a local preview URL.

- [ ] **Step 4: Implement the composable state machine**

`useSceneAssets(projectId)` exposes `state = loading|ready|empty|error|readonly`, filters, selected asset, selected version, impact, methods, and action results. Archived projects remain searchable/viewable but mutation methods return `PROJECT_ARCHIVED` guidance. Cache only the latest successful list for degraded read-only display.

- [ ] **Step 5: Run tests and commit**

Run: `node --test --test-name-pattern='scene asset|variant|management-only' tests/*.test.js`

```bash
git add aicp-frontend/src/api/sceneAsset.js aicp-frontend/src/views/content-project/workbench aicp-frontend/tests/scene-asset-model.test.js
git commit -m "feat: add scene asset frontend model"
```

---

### Task 5: Pure Eight-Stage Workbench State and Action Feedback

**Files:**
- Create: `aicp-frontend/src/views/content-project/workbench/scriptWorkbenchModel.js`
- Create: `aicp-frontend/src/views/content-project/workbench/useScriptWorkbench.js`
- Create: `aicp-frontend/src/views/content-project/components/ActionGuidanceDialog.vue`
- Create: `aicp-frontend/src/views/content-project/components/GenerationProgressDialog.vue`
- Create: `aicp-frontend/src/views/content-project/components/ActionResultDrawer.vue`
- Modify: `aicp-frontend/src/views/content-project/components/WorkflowRail.vue`
- Create: `aicp-frontend/tests/script-workbench-model.test.js`

**Interfaces:**
- Authoritative stage keys: `creation_settings`, `novel_upload`, `novel_analysis`, `adaptation`, `structured_script`, `script_body`, `review_revision`, `text_storyboard`.
- `evaluateActionPrecondition(context, action)` returns `{ allowed, code, title, message, targetAction }`.
- `beginGeneration`, `updateGenerationProgress`, `finishGeneration`, `acceptGeneration`, and `discardGeneration` share one result/task/points flow.
- `requestStageTransition` shows percentage loading, confirms persistence, then activates the next stage.

- [ ] **Step 1: Write failing stage-order and guard tests**

```js
test('workbench uses the approved eight-stage creative order', () => {
  assert.deepEqual(STAGES.map(s => s.key), [
    'creation_settings','novel_upload','novel_analysis','adaptation',
    'structured_script','script_body','review_revision','text_storyboard'
  ])
})

test('all actions stay clickable and explain unmet conditions', () => {
  assert.deepEqual(
    evaluateActionPrecondition({ selectedBlockId: null }, 'ai_continue'),
    { allowed:false, code:'SCRIPT_BLOCK_REQUIRED', title:'请先选择正文块',
      message:'选择动作、对白或旁白正文块后才能执行此操作。', targetAction:'focus_script_blocks' }
  )
})

test('transition reaches next stage only after persistence succeeds', () => {
  const state = createWorkbenchState()
  requestStageTransition(state, 'novel_upload')
  assert.equal(state.activeStage, 'creation_settings')
  completeStageTransition(state, { persisted: true })
  assert.equal(state.activeStage, 'novel_upload')
})
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test tests/script-workbench-model.test.js`

Expected: FAIL because the model is absent.

- [ ] **Step 3: Implement stage state, guards, progress, results, and points records**

Never use native `disabled` for explainable business prerequisites. The generation dialog displays selected model, estimated points, 0–100 percentage, current subtask, cancelability, and failure details. Accepting a result records artifact path/version, task ID, estimated/actual points, and impact; discarding preserves the task record without changing the artifact.

- [ ] **Step 4: Update `WorkflowRail.vue`**

Render the approved labels, completed/current/pending/error states, overall completion, and direct navigation only to stages already entered. The stage transition footer owns “上一步 / 保存草稿 / 确认并进入下一步”; remove the obsolete per-stage top shortcut buttons.

- [ ] **Step 5: Run tests and commit**

Run: `node --test --test-name-pattern='workbench|eight-stage|transition|actions stay clickable' tests/*.test.js`

```bash
git add aicp-frontend/src/views/content-project/workbench aicp-frontend/src/views/content-project/components aicp-frontend/tests/script-workbench-model.test.js
git commit -m "feat: add eight stage workbench state"
```

---

### Task 6: Implement Stages 1–4 with Complete Editing Paths

**Files:**
- Create: `aicp-frontend/src/views/content-project/stages/CreationSettingsStage.vue`
- Create: `aicp-frontend/src/views/content-project/stages/NovelUploadStage.vue`
- Create: `aicp-frontend/src/views/content-project/stages/NovelAnalysisStage.vue`
- Create: `aicp-frontend/src/views/content-project/stages/AdaptationStage.vue`
- Create: `aicp-frontend/tests/script-workbench-upstream-contract.test.js`

**Interfaces:**
- Creation settings include creation type, category/genre, tone, audience, episode count/duration, adaptation strength, output format, model source/model, and point estimate.
- Novel upload supports file upload plus pasted text capped at 2,000 Chinese characters.
- Novel analysis exposes editable synopsis, event list, chapter outline, world type/time, locations, power/rules, factions, and detailed character biographies.
- Adaptation supports hook selection, new rule, confirmation, and regeneration with result comparison.

- [ ] **Step 1: Write failing source-contract tests**

```js
test('upstream stages expose every approved editor and action', () => {
  const sources = readStageSources(['CreationSettingsStage','NovelUploadStage','NovelAnalysisStage','AdaptationStage'])
  for (const marker of [
    'creation-type','genre-selector','model-selector','paste-char-counter',
    'edit-synopsis','add-event','edit-chapter-outline','edit-worldview','character-detail',
    'convert-location-to-scene-asset','select-high-pressure-hook','add-adaptation-rule',
    'confirm-adaptation','regenerate-current-artifact'
  ]) assert.match(sources, new RegExp(`data-action=["']${marker}["']`))
})

test('pasted novel enforces the 2000 Chinese-character product rule', () => {
  assert.equal(countChineseCharacters('中文 A1'), 2)
  assert.equal(validatePastedNovel('中'.repeat(2001)).code, 'NOVEL_TEXT_TOO_LONG')
})
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test tests/script-workbench-upstream-contract.test.js`

Expected: FAIL because stage components and helpers are absent.

- [ ] **Step 3: Implement creation settings and upload**

Fetch actual models through the existing 3001-compatible model source; show built-in demo models only when the service returns no usable models or is unreachable. Display source badges and point rules. Preserve longer-document upload. On paste overflow, keep the button clickable and show the exact excess plus “改用文件上传”.

- [ ] **Step 4: Implement novel-analysis editors and scene conversion**

Use drawers/dialogs with field validation and save/cancel. Each save creates a new artifact version and impact result. “主要地点 → 转为场景资产” calls `sceneAssetApi.createFromLocation`; an already converted location shows stable ID/version/status and opens its asset detail rather than duplicating it.

- [ ] **Step 5: Implement adaptation actions**

High-pressure hook selection persists the chosen hook before confirmation. New rules are editable/removable until confirmation. Regeneration uses the shared model/estimate/progress/diff/accept flow; confirmation remains guarded until one hook and all required settings exist.

- [ ] **Step 6: Run tests and commit**

Run: `node --test --test-name-pattern='upstream stages|pasted novel|analysis|adaptation' tests/*.test.js`

```bash
git add aicp-frontend/src/views/content-project/stages aicp-frontend/tests/script-workbench-upstream-contract.test.js
git commit -m "feat: complete upstream script stages"
```

---

### Task 7: Implement Stages 5–8 and Bind Scene Assets

**Files:**
- Create: `aicp-frontend/src/views/content-project/stages/StructuredScriptStage.vue`
- Create: `aicp-frontend/src/views/content-project/stages/ScriptBodyStage.vue`
- Create: `aicp-frontend/src/views/content-project/stages/ReviewRevisionStage.vue`
- Create: `aicp-frontend/src/views/content-project/stages/TextStoryboardStage.vue`
- Create: `aicp-frontend/src/views/content-project/components/SceneAssetPicker.vue`
- Modify: `aicp-frontend/src/api/contentProject.js`
- Create: `aicp-frontend/tests/script-workbench-downstream-contract.test.js`

**Interfaces:**
- Structured script: open episode structure, add/regenerate beats, regenerate artifact.
- Script body: select block, continue/conflict/condense/rewrite/character check, add scene/block, run check, export, regenerate.
- Review: filter issues, save local revision, before/after compare, approve episode, regenerate.
- Storyboard: add/split/merge shot, card/table switch, continuity check, archive, configure mind map, create canvas, regenerate.
- Script scenes bind a master/version and optional variant/version; storyboard shots persist immutable snapshots.

- [ ] **Step 1: Write failing action and binding contracts**

```js
test('downstream stages expose every approved business action', () => {
  const source = readDownstreamStageSources()
  for (const action of [
    'open-episode-structure','add-beat','regenerate-beat','continue-selected-block',
    'strengthen-conflict','condense-dialogue','rewrite-tone','check-character-consistency',
    'add-scene','add-script-block','run-script-check','export-script',
    'filter-review-issues','save-local-revision','compare-revision','approve-episode',
    'add-shot','split-shot','merge-shot','toggle-shot-view','run-continuity-check',
    'complete-and-archive','configure-mindmap','create-canvas-project','regenerate-current-artifact'
  ]) assert.match(source, new RegExp(`data-action=["']${action}["']`))
})

test('storyboard payload pins exact scene asset versions', () => {
  assert.deepEqual(buildShotAssetBinding({ assetId:7, versionId:12, variantId:'VAR-001', variantVersion:2 }), {
    scene_asset_id:7, scene_asset_version_id:12,
    scene_variant_id:'VAR-001', scene_variant_version:2
  })
})
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test tests/script-workbench-downstream-contract.test.js`

Expected: FAIL because components and binding helpers do not exist.

- [ ] **Step 3: Implement structured script, script body, and review**

Use stable IDs for episodes, beats, scenes, and blocks. AI actions require a selected block and route through guidance if missing. Adding a scene opens `SceneAssetPicker` with “引用已有资产 / 创建新资产 / 暂不绑定”; the unbound option is allowed in draft but produces a visible pre-storyboard warning. Space changes explicitly choose “仅当前场景” or “更新母资产”. Review approval blocks unresolved HIGH/BLOCKER issues and links to the filtered list.

- [ ] **Step 4: Implement text storyboard and production gates**

Add/split/merge operations preserve or explicitly resolve asset bindings. Continuity calls the backend and groups issues by scene, variant, fixed prop, character state, and axis. “完成并归档” requires continuity pass and locked snapshots. “创建画布项目” sends the locked content/storyboard versions and scene snapshot references; failures show guidance/result details rather than a false success toast.

- [ ] **Step 5: Run tests and commit**

Run: `node --test --test-name-pattern='downstream stages|storyboard payload|scene asset versions' tests/*.test.js`

```bash
git add aicp-frontend/src/views/content-project/stages aicp-frontend/src/views/content-project/components/SceneAssetPicker.vue aicp-frontend/src/api/contentProject.js aicp-frontend/tests/script-workbench-downstream-contract.test.js
git commit -m "feat: complete downstream script stages"
```

---

### Task 8: Scene Asset Library UI and Cross-Stage Impact Handling

**Files:**
- Create: `aicp-frontend/src/views/content-project/components/SceneAssetLibrary.vue`
- Create: `aicp-frontend/src/views/content-project/components/SceneAssetDetailDrawer.vue`
- Modify: `aicp-frontend/src/views/content-project/workbench/useSceneAssets.js`
- Modify: `aicp-frontend/src/views/content-project/stages/NovelAnalysisStage.vue`
- Modify: `aicp-frontend/src/views/content-project/stages/ScriptBodyStage.vue`
- Modify: `aicp-frontend/src/views/content-project/stages/TextStoryboardStage.vue`
- Create: `aicp-frontend/tests/scene-asset-ui-contract.test.js`

**Interfaces:**
- Library search: name, location, landmark, tag.
- Filters: space type, reuse level, status, referenced/unreferenced.
- Detail tabs: basic, visual, variants, continuity, references/versions.
- Mutations expose persistent action results and downstream impact; referenced assets cannot be deleted.

- [ ] **Step 1: Write failing UI contract tests**

```js
test('scene asset library exposes the five approved detail tabs', () => {
  const source = readSceneAssetComponents()
  for (const tab of ['basic','visual','variants','continuity','references-versions']) {
    assert.match(source, new RegExp(`name=["']${tab}["']`))
  }
  for (const action of ['new-scene-asset','create-from-location','view-impact','restore-version','replace-reference']) {
    assert.match(source, new RegExp(`data-action=["']${action}["']`))
  }
})
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test tests/scene-asset-ui-contract.test.js`

Expected: FAIL because the library and drawer are absent.

- [ ] **Step 3: Implement cards, filters, and detail editing**

Cards show cover fallback, stable ID, name, type, current version, variant count, episode references, and status. Every editor preserves unsaved changes on validation failure. Restore/version replacement opens an impact confirmation and creates a revisitable result. Referenced assets expose “停用 / 创建替代并迁移引用”, not destructive delete.

- [ ] **Step 4: Implement cross-stage `STALE` handling**

Visual or continuity edits refresh impact, mark affected script scenes and unlocked storyboards `STALE`, and offer “查看差异 / 保留旧版 / 升级新版”. Locked shots remain pinned. Management-only edits update labels without staling downstream content.

- [ ] **Step 5: Run tests and commit**

Run: `node --test --test-name-pattern='scene asset library|detail tabs|STALE' tests/*.test.js`

```bash
git add aicp-frontend/src/views/content-project/components aicp-frontend/src/views/content-project/workbench/useSceneAssets.js aicp-frontend/src/views/content-project/stages aicp-frontend/tests/scene-asset-ui-contract.test.js
git commit -m "feat: add scene asset library experience"
```

---

### Task 9: Native Workspace Merge and Launchpad Routing

**Files:**
- Modify: `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue`
- Modify: `aicp-frontend/src/views/content-project/ScriptCreationHome.vue`
- Modify: `aicp-frontend/src/views/content-project/ContentProjectCreate.vue`
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/tests/content-project-workflow.test.js`
- Create: `aicp-frontend/tests/script-workbench-routing.test.js`

**Interfaces:**
- `/script-gen` remains the four-card launchpad and recent-project list.
- New/continued project navigation resolves to `/script-gen/:projectId/workspace` with the correct starting stage for quick, professional, upload, and existing-project entry.
- `ContentProjectWorkspace.vue` composes rail, stage panel, shared scene library, model/points context, task/result drawers, and transition footer.

- [ ] **Step 1: Write failing route and entry-mode tests**

```js
test('creation entries resolve to the native project workbench', () => {
  assert.equal(workspaceTarget({ id:21, entryMode:'quick' }), '/script-gen/21/workspace?stage=creation_settings')
  assert.equal(workspaceTarget({ id:22, entryMode:'upload' }), '/script-gen/22/workspace?stage=creation_settings&next=novel_upload')
})

test('workspace no longer renders legacy story_seed destination stages', () => {
  const source = readFileSync(workspacePath, 'utf8')
  assert.doesNotMatch(source, /currentStageInfo\.key === 'story_seed'/)
  assert.doesNotMatch(source, /currentStageInfo\.key === 'destination'/)
  assert.match(source, /SceneAssetLibrary/)
  assert.match(source, /STAGES/)
})
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test tests/script-workbench-routing.test.js tests/content-project-workflow.test.js`

Expected: FAIL because the workspace still renders the legacy flow.

- [ ] **Step 3: Replace the legacy workspace body with the native shell**

Keep project loading, permissions, autosave, route, canvas handoff, and existing content APIs. Remove legacy `story_seed/characters/synopsis/outline/content/review/destination/storyboard` conditional blocks. Render the eight stage components through the authoritative stage model. Shared top actions are limited to project identity/status, model/points context, scene assets, task/result entry, and return to launchpad; stage actions live inside their stage or transition footer.

- [ ] **Step 4: Connect all launchpad paths**

Project creation must persist creation settings before navigation. Upload entry still visits settings first and then advances to upload after confirmation. Recent project cards resume saved stage. TVC keeps its existing specialized creation fields but lands in the same workbench where only applicable stage variants are rendered.

- [ ] **Step 5: Run frontend tests and production build**

Run: `npm test`

Run: `npm run build`

Expected: all frontend tests PASS and Vite build exits 0.

- [ ] **Step 6: Commit**

```bash
git add aicp-frontend/src/views/content-project aicp-frontend/src/router/index.js aicp-frontend/tests
git commit -m "feat: merge eight stage workbench into script gen"
```

---

### Task 10: Update the Static Acceptance Prototype

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Static demo mirrors the project-scoped scene library and four-layer scene model.
- It remains honest about demo-only persistence while matching production labels, navigation, guards, progress, results, and version rules.

- [ ] **Step 1: Write failing prototype contracts**

```js
test('prototype includes project scene assets without adding a ninth stage', () => {
  assert.equal((html.match(/data-stage-key=/g) || []).length, 8)
  for (const action of ['open-scene-assets','create-scene-asset','create-scene-variant','convert-location-to-scene-asset','bind-scene-asset','view-scene-asset-impact']) {
    assert.match(html, new RegExp(`data-action="${action}"`))
  }
  assert.match(html, /场景母资产/)
  assert.match(html, /场景变体/)
  assert.match(html, /分镜锁定快照/)
})

test('prototype documents the scene asset Obsidian directory', () => {
  assert.match(html, /04-场景资产\/00-场景资产索引\.md/)
  assert.match(html, /SCENE-ASSET-001/)
})
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test --test-name-pattern='project scene assets|scene asset Obsidian' tests/script-creation-prototype.test.cjs`

Expected: FAIL because scene-asset UI/state is absent.

- [ ] **Step 3: Add library, detail, picker, snapshot, and impact demo states**

Reuse the static prototype's existing action guards, percentage progress, action results, task center, version history, model selection, point ledger, and artifact graph. Add one project scene asset with two variants and real demo links across location → asset → script scene → shot snapshot. Every new button must either mutate visible demo state or open prerequisite guidance.

- [ ] **Step 4: Run full prototype tests**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html tests/script-creation-prototype.test.cjs
git commit -m "feat: add scene assets to workbench prototype"
```

---

### Task 11: Synchronize PRD and Obsidian/Model/Billing Documentation

**Files:**
- Modify: `漫剧视频创作平台_PRD.md`
- Modify: `剧本创作页面逻辑盘点与补充清单.md`
- Modify: `docs/superpowers/specs/2026-08-06-script-workbench-obsidian-model-billing-design.md`
- Modify: `docs/superpowers/specs/2026-07-02-script-creation-creative-bible-design.md`
- Modify: `docs/superpowers/specs/2026-07-01-script-creation-warehouse-flow-design.md`
- Create: `docs/剧本创作模块_场景资产与八阶段融合说明.md`
- Create: `tests/script-workbench-docs.test.cjs`

**Interfaces:**
- Documents the launchpad/workbench route split, eight-stage order, all stage actions, scene four-layer model, status/version/impact rules, Obsidian paths, 3001 model fallback, and point settlement.
- Traceability table maps every visible business action to prerequisites, success result, Markdown artifact, stale dependents, API, and acceptance test.

- [ ] **Step 1: Write failing documentation coverage tests**

```js
test('all product docs use the same eight-stage order and scene model', () => {
  const docs = loadRequiredDocs()
  for (const text of docs) {
    for (const term of ['创作设置','小说上传','小说分析','改编方案','结构化文字剧本','剧本正文','审核修订','文字分镜']) {
      assert.match(text, new RegExp(term))
    }
    for (const term of ['场景母资产','场景变体','剧本场景实例','分镜场景快照']) {
      assert.match(text, new RegExp(term))
    }
  }
})

test('billing documentation states demo fallback and 3001 point parity', () => {
  const text = readFileSync(billingDesignPath, 'utf8')
  assert.match(text, /内置演示模型.*0\s*积分/s)
  assert.match(text, /3001.*现有.*积分规则/s)
  assert.match(text, /预估.*冻结.*结算.*退回/s)
})
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test tests/script-workbench-docs.test.cjs`

Expected: FAIL because existing documents do not consistently contain the scene model and merged route behavior.

- [ ] **Step 3: Update the product documents**

Use the approved design spec as the source of truth. Add complete page inventory, roles, entry/exit conditions, empty/loading/error/archived states, button behavior, API contracts, point rules, Markdown dependency graph, and acceptance criteria. Mark completed vs deferred scope accurately. Do not describe the static prototype as production persistence.

- [ ] **Step 4: Add the developer-facing fusion document**

`docs/剧本创作模块_场景资产与八阶段融合说明.md` contains route map, component map, data contracts, database changes, API examples, Obsidian directory, stale propagation matrix, failure codes, and deployment/rollback notes for V16.

- [ ] **Step 5: Run documentation tests and commit**

Run: `node --test tests/script-workbench-docs.test.cjs`

```bash
git add 漫剧视频创作平台_PRD.md 剧本创作页面逻辑盘点与补充清单.md docs tests/script-workbench-docs.test.cjs
git commit -m "docs: synchronize script workbench scene assets"
```

---

### Task 12: Full Verification and Browser Acceptance

**Files:**
- Modify only if verification exposes a defect in files already listed above.

- [ ] **Step 1: Run all backend tests**

Run: `mvn test`

Working directory: `aicp-backend`

Expected: BUILD SUCCESS with no failing tests.

- [ ] **Step 2: Run all frontend and static-prototype tests**

Run: `npm test && npm run build`

Working directory: `aicp-frontend`

Run: `node --test tests/*.cjs`

Working directory: repository root.

Expected: all tests PASS and production build exits 0.

- [ ] **Step 3: Start or reuse local services and verify routes**

Open:

- `http://localhost:8080/script-gen`
- `http://localhost:8080/script-gen/{projectId}/workspace`
- `http://localhost:62096/eight-stage-workbench.html`

Verify at 1440×900 and 390×844:

1. Launchpad still has AI quick, AI professional, upload, and TVC.
2. Every entry reaches creation settings before upload or generation.
3. Exactly eight stages render in the approved order.
4. Every stage transition shows a percentage page and advances only after persistence succeeds.
5. Every named business action is clickable; missing conditions show repair guidance.
6. Novel analysis contains all base info, worldview, location, force/rules, factions, and detailed biography editors.
7. Scene asset library supports search/filter/create/edit/variant/version/impact.
8. Location converts to an asset, script scene binds it, storyboard locks the exact version, and a later asset edit does not mutate the locked snapshot.
9. Model selection shows built-in demo fallback when 3001 has no models and actual models/point estimates when it does.
10. Task, version, action result, Markdown path, and point ledger remain revisitable after success.

- [ ] **Step 4: Capture an acceptance matrix**

Record route, stage, action, prerequisite, actual result, artifact path/version, task ID, point result, and PASS/FAIL in `docs/剧本创作模块_场景资产与八阶段融合说明.md`. For demo-only actions, label the result “静态演示状态” rather than “已写入平台”.

- [ ] **Step 5: Inspect the final diff**

Run: `git status --short && git diff --check && git log --oneline -12`

Expected: no whitespace errors, no unrelated files, and one focused commit per completed task.

- [ ] **Step 6: Request code review and apply verified findings**

Use `superpowers:requesting-code-review`, address only reproduced findings, rerun the relevant focused tests, then rerun Steps 1–5.

## Definition of Done

- `/script-gen` is the launchpad and `/script-gen/:projectId/workspace` is the single native eight-stage runtime.
- The static 62096 page remains available and mirrors the production interaction contract.
- Scene masters, variants, script instances, and locked storyboard snapshots are all represented and versioned.
- Existing locked shots remain reproducible after asset changes.
- All requested buttons have observable success or actionable prerequisite feedback.
- Models and point consumption follow the 3001 rules with zero-cost demo fallback.
- Artifact changes update Markdown projections and downstream stale status.
- Backend tests, frontend tests, prototype tests, builds, browser acceptance, and documentation coverage all pass.
