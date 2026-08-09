# Task 8 Report — Scene Asset Library UI and Cross-Stage Impact Handling

## Status

DONE_WITH_CONCERNS

## Changed files

- `aicp-frontend/src/views/content-project/components/SceneAssetLibrary.vue`
- `aicp-frontend/src/views/content-project/components/SceneAssetDetailDrawer.vue`
- `aicp-frontend/src/views/content-project/workbench/sceneAssetUiModel.js`
- `aicp-frontend/src/views/content-project/workbench/useSceneAssets.js`
- `aicp-frontend/src/views/content-project/stages/NovelAnalysisStage.vue`
- `aicp-frontend/src/views/content-project/stages/ScriptBodyStage.vue`
- `aicp-frontend/src/views/content-project/stages/TextStoryboardStage.vue`
- `aicp-frontend/tests/scene-asset-ui-contract.test.js`

`ContentProjectWorkspace.vue` was deliberately not modified; Task 9 owns native workspace mounting and adapter composition.

## Implementation

- Added the project scene-asset library with name/location/landmark/tag search; space, reuse, lifecycle and reference filters; cover fallback; master/version/variant/reference/lifecycle/sync card evidence; and revisitable action-result history.
- Added the approved five-tab detail drawer (`basic`, `visual`, `variants`, `continuity`, `references-versions`) with draft editing, save/cancel, field guidance, variant creation/update, impact loading, manual or supplied historical-version restore, stop/deactivate, and replacement creation/migration.
- Restore and reference migration both require a visible impact confirmation before persistence. Successful mutations store durable result evidence in project-scoped browser storage. If browser storage is unavailable, the current-session result remains available without making storage a mutation failure.
- Semantic changes reuse Task 4 classification and server impact. Unlocked consumers project to `STALE` with “查看差异 / 保留旧版 / 升级新版”; locked storyboard snapshots stay `PINNED`. Management-only changes remain `CURRENT`.
- The authoritative `useSceneAssets` composable remains the only asset store. It now owns filtered view state, mutation impact refresh, persistent results, reference replacement and consumer-resolution adapter calls. Missing persistence adapters return actionable guidance and never fake success.
- Post-persistence impact refresh failure is recorded as `impactRefresh.ok=false` with a retry action rather than incorrectly claiming the already-persisted asset mutation failed.
- Novel analysis locations, script-body scenes and storyboard shots now resolve status from the same scene-asset state, open the same detail flow, and reopen the same persistent action result. Locked storyboard shots display `PINNED` in both card and table views.

## TDD evidence

RED:

- `cd aicp-frontend && node --test tests/scene-asset-ui-contract.test.js`
- Failed before production implementation with `ERR_MODULE_NOT_FOUND` for `sceneAssetUiModel.js`; the library and drawer were also absent.

GREEN:

- `node --test tests/scene-asset-ui-contract.test.js` — 7/7 passed.
- `node --test --test-name-pattern='scene asset library|detail tabs|STALE' tests/*.test.js` — 20 test files/contracts passed, zero failures.
- Task 4/5/6/7 regression: `node --test tests/scene-asset-model.test.js tests/script-workbench-upstream-contract.test.js tests/script-workbench-downstream-contract.test.js tests/script-workbench-model.test.js` — 63/63 passed.
- `node --check` passed for the new UI model, composable and contract test.
- Vue SFC parse, `compileScript`, and `compileTemplate` passed for both new components and all three modified stages.
- `npm run build -- --outDir /tmp/aicp-task8-dist --emptyOutDir` passed; Vite transformed 1,963 modules.
- `git diff --check` passed.

## Remaining risks / Task 9 integration notes

- The current scene-asset backend has a restore endpoint but no historical-version list endpoint. The drawer consumes `asset.versions` when an integration adapter supplies it and otherwise exposes an explicit historical-version primary-key input; a native history-list API remains desirable.
- The current impact endpoint returns active asset applications and canvas placements. Modern storyboard snapshot rows are checked by the storyboard continuity/lock services but are not enumerated by this asset-impact endpoint. Task 9 should compose storyboard continuity/shot state into the drawer impact adapter so every unlocked storyboard is listed, while locked snapshots remain pinned.
- Reference replacement and the three per-consumer decisions require Task 9 to provide project-scoped persistence adapters. The controls remain clickable before integration and return explicit guidance instead of mutating local data or reporting success.

---

## Review fix round 1/5 — authoritative impact, lifecycle, and persistent result context

### Changes

- Removed the backend name-only SQL keyword prefilter. Project scene search now evaluates the real current-version envelope across name, `world_location_ref`, landmarks, and tags. Tags are accepted and versioned by create/update requests.
- `SceneAssetView` now returns authoritative `reference_count`, deduplicated episode labels, and aggregate `sync_status`. The referenced/unreferenced filter uses the complete active-reference set rather than the old misnamed locked count.
- Impact references now carry consumer type/id/key/version, referenced asset version, episode information, lock flags, snapshot-lock state, and fingerprint. Modern `storyboard_version_shots` are enumerated through version/storyboard project ownership. Locked storyboard versions always emit `PINNED`; unlocked old versions emit `NEEDS_SYNC`.
- Corrected lifecycle semantics. Archive now requires zero active consumers. Referenced assets use reversible `DISABLED` and `ACTIVE` endpoints, preserving existing immutable references while rejecting every new storyboard binding. After consumers are migrated/released, archive remains available. Picker and script binding logic reject both disabled and archived assets.
- Drawer and all three stages now resolve status from the selected persistent result's Task 4 `change` plus exact `affectedConsumers`; management-only changes therefore remain `CURRENT`, even if some older aggregate impact result was stale. Locked shots remain `PINNED`.
- Reopening a stored result first loads/selects `result.assetId`; failure leaves the drawer closed and returns guidance rather than showing blank or wrong context.
- The visual editor now maintains `materials`, `palette`, and `lighting` as three independent draft/payload fields.
- The basic editor now round-trips `tags` as line-based metadata, so the UI can author the same tags that project scene search indexes.

### RED / GREEN evidence

- Frontend RED: Task 8 contract failed at module load because `sceneConsumerStatus` did not exist. Follow-up REDs independently reproduced disabled assets still appearing/binding and a current-version consumer being incorrectly marked stale.
- Backend RED: `ProjectSceneAssetLifecycleE2ETest` ran 13 tests with 3 failures: metadata search returned zero, impact returned no modern storyboard consumers, and disable/activate returned an unsupported-route error. A focused lifecycle RED then showed disabled binding returned asset-not-found semantics instead of lifecycle conflict.
- Frontend GREEN: Task 4/5/6/7/8 selected regressions passed 77/77. The Task 8 UI contract passed 14/14, including tag persistence. The requested name-pattern suite passed across 20 test files/contracts with zero failures. Six affected SFCs passed parse, script, and template compilation; `node --check` passed.
- Backend GREEN: `ProjectSceneAssetLifecycleE2ETest` passed 13/13; combined with `StoryboardSceneAssetSnapshotE2ETest`, 30/30 passed with zero failures/errors.
- Vite production build passed (1,963 modules) to `/tmp/aicp-task8-fix1-dist`; `git diff --check` passed.

### Remaining integration note

- The three downstream consumer decisions still intentionally require Task 9 persistence adapters. The backend now supplies sufficient consumer/lock/snapshot identity for those adapters; the Task 8 UI will not report success when an adapter is absent or does not return `persisted: true`.

---

## Review fix round 2/5 — active consumers, durable sync semantics, and bounded reads

### Changes

- Impact now treats only the storyboard's editable current-draft pointer and immutable current-locked pointer as authoritative consumers. Historical copies are omitted; a current locked `SUPERSEDED` version remains immutable and `PINNED` through the shared storyboard state machine.
- Scene-asset list projection batches all versions, active applications, canvas placements, project storyboards, authoritative storyboard versions/shots, and content units. Filters run before impact aggregation where their fields allow it, and the query-count regression remains bounded as the asset count grows.
- Sync state is derived from persisted version semantics. Management-only metadata such as tags remains `CURRENT` after API reload in the library, drawer, and all cross-stage fallbacks; semantic visual changes remain `STALE`. Restoring the same semantic version as a pinned consumer also resolves to `CURRENT`.
- `SCRIPT_SCENE` impact exposes its exact target as both `consumer_id` and stable string `consumer_key`; the script stage passes the same stable key into shared status resolution.
- Added documented `DISABLED` values to both shared asset lifecycle enums. No production status switch over these enums exists, so no exhaustive switch required expansion; existing new-binding guards continue to reject disabled assets.
- Disable and reference-replacement flows capture authoritative consumers before selection or adapter side effects can clear transient impact state. Empty adapter consumer results no longer erase the pre-mutation affected-consumer audit.

### RED / GREEN evidence

- Recovered RED: backend focused tests reported two failures and one test error: shared lifecycle enums omitted `DISABLED`; the legacy restore assertion conflicted with persisted semantic equality; and the new bounded-query test had strict-Mockito unused stubs.
- Added frontend REDs independently reproduced missing stable `SCRIPT_SCENE` key wiring and replacement results losing the pre-adapter impact snapshot.
- Backend GREEN: `ProjectSceneAssetLifecycleE2ETest` 16/16, `ProjectSceneAssetBatchQueryTest` 1/1, `StoryboardSceneAssetSnapshotE2ETest` 17/17, and `AssetWorkbenchContractTest` 19/19 (53/53 total).
- Frontend GREEN: Task 8 contract 17/17; Task 4/5/6/7 focused regression 63/63; requested name-pattern suite passed across 20 contracts with zero failures.
- Five affected Vue SFCs passed parse, script, and template compilation. `node --check` passed for the affected JavaScript files and contract test.
- Vite production build passed after transforming 1,963 modules to `/tmp/aicp-task8-fix2-dist`; `git diff --check` passed.

### Scope note

- `ContentProjectWorkspace.vue` remains untouched. Native mounting and real consumer persistence adapter composition remain Task 9 scope.

---

## Review fix round 3/5 — reviewing consumers, stable script IDs, and authoritative disable impact

### Changes

- A storyboard version referenced by `currentDraftVersionId` remains an active consumer while its state is either `DRAFT` or `REVIEWING`. Archive is therefore blocked throughout review. The immutable `currentLockedVersionId` path is unchanged and continues to report locked/superseded snapshots as `PINNED`.
- `asset_applications.target_key` now persists the real textual consumer identity. `SCRIPT_SCENE` impact returns that value end to end (for example `EP-007-SCENE-001`), while legacy rows with no text key continue to expose their numeric `target_id` as the compatibility key.
- Added the idempotent V17 schema migration and undo script, plus canonical H2/MySQL schema columns. A focused migration test applies V17 twice to a legacy table and verifies both the column and composite index.
- Disable now loads authoritative impact before mutation, captures the affected consumers before selection refresh can clear transient state, and records those consumers in the durable action result. If impact loading fails, disable is not called and the UI returns actionable failure guidance rather than fake success.
- The `WorkspaceAsset.status` entity contract now documents `DISABLED` alongside the other persisted lifecycle values.

### RED / GREEN evidence

- RED: the new frontend composable tests initially failed because `useSceneAssets` could not be imported directly and disable did not load impact first. The backend focused test initially failed to compile because `AssetApplication` had no textual target key.
- Backend GREEN (run independently to avoid the repository's known shared dev-seed collision): `ProjectSceneAssetLifecycleE2ETest` 17/17, `ProjectSceneAssetBatchQueryTest` 1/1, `StoryboardSceneAssetSnapshotE2ETest` 17/17, `AssetWorkbenchContractTest` 20/20, `AssetWorkbenchSchemaTest` 9/9, and `AssetApplicationServiceTest` 1/1.
- Frontend GREEN: Task 8 contract 19/19; Task 4–8 focused regressions 82/82; requested name-pattern suite passed 20/20. Five affected SFCs passed parse, script, and template compilation; all affected JavaScript files passed `node --check`.
- Vite production build passed after transforming 1,963 modules to `/tmp/aicp-task8-fix3-dist`; `git diff --check` passed.

### Known unrelated test baseline

- `AssetMarketLifecycleE2ETest` still fails before reaching this change because the test schema lacks the pre-existing `canvas_projects.canvas_mode` column expected by `CanvasProjectMapper`. No production or test-schema change was made for that unrelated baseline issue; textual-key persistence is covered by the focused service test and lifecycle/schema E2E coverage above.

### Scope note

- `ContentProjectWorkspace.vue` remains untouched.
