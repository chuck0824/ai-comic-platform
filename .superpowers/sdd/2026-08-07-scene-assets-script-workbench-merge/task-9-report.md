# Task 9 Report — Native Workspace Merge and Launchpad Routing

## Status

DONE_WITH_KNOWN_BASELINE_FAILURE

## Implementation

- Kept `/script-gen` as the four-card launchpad with recent projects and todos. Recent projects now resume through the validated authoritative stage rather than opening an unqualified workspace URL.
- Added pure routing and restore rules for quick, professional, upload, TVC, and resumed projects. Upload enters `creation_settings` with `next=novel_upload`; TVC retains its specialized product, campaign objective, and duration fields and enters the shared shell with `variant=tvc`.
- Project creation now waits for both project creation and a durable parameter-version write before navigating. A failed settings write leaves the user on the create page with a retryable error instead of reporting success.
- Replaced the legacy `story_seed/characters/synopsis/outline/content/review/destination/storyboard` workspace body with the Tasks 5–8 shell: authoritative rail, all eight stages, scene asset library/detail/result flow, model and point context, task/progress/result/guidance feedback, percentage transition dialog, transition footer, final completion, autosave, route reactivity, and canvas handoff.
- Restores project-scoped stage content from content-unit drafts and creation settings from parameter versions. Query stages are validated against `STAGES`; unknown or skipped forward navigation is restored to the latest allowed persisted stage. Revisiting completed stages does not regress the backend resume pointer.
- Added transition guards for required creation settings and positive paid-model points, novel upload, complete novel analysis, confirmed adaptation, structured beats, script scenes and asset decisions, review approval, and storyboard archive. Actions remain clickable and return actionable guidance when their prerequisites are missing.
- Connected real adapters for parameter versions, resume position, content-unit drafts, file/text upload, 3001-compatible model loading and point estimates, project scene assets, stable `SCRIPT_SCENE` applications, modern storyboard shots/split/merge/continuity/lock, and canvas snapshot jobs. The script-scene adapter now receives the stable scene ID as backward-compatible second-argument context.
- Generation controls keep the Task 5 task/result audit flow. Stage generation APIs that do not have a safe model-aware backend contract do not synthesize success; they produce an explicit failed result explaining that the generation adapter is unavailable.

## TDD Evidence

RED:

- `node --test tests/script-workbench-routing.test.js tests/content-project-workflow.test.js`
- 9 passed, 4 failed as expected: missing routing helper, legacy workspace conditionals, unqualified recent-project continuation, and create navigation without durable settings persistence.

GREEN:

- Route/entry/workspace: 14/14 passed.
- Task 4–8 focused regression: 82/82 passed.
- Combined workflow/model/upstream/downstream focused run: 60/60 passed after final transition guards and storyboard adapter normalization.
- All modified entry/workspace SFCs and all eight stage SFCs passed Vue parse, `compileScript`, and `compileTemplate`.
- `node --check` passed for routing, adapter, and API JavaScript; `git diff --check` passed.
- Final Vite production build passed after transforming 1,997 modules to `/tmp/aicp-task9-final-dist`.

## Full Frontend Suite

- `npm test`: 200 passed, 1 failed.
- The sole failure is the acknowledged pre-existing `agent-config-state.test.js` identity mismatch: production initializes `{ name: '', description: '' }`, while the old test expects `null`. No Task 9 file participates in that assertion.

## Integration Notes

- The current content-generation controller derives `projectId` from `target_id`, while the executor interprets the same `target_id` as a content-unit ID. Task 9 therefore does not claim a successful real model regeneration through that contradictory endpoint; the UI records an explicit failed task/result until a safe project/unit contract is available.
- Storyboard creation itself remains in the professional storyboard editor. If no editable storyboard version exists, text-storyboard persistence buttons remain clickable and explain the required next action.

## Fix Round 1 — Reviewer Findings

- Credit estimates now send the backend-required generation `type`, selected `model_id`, and operation parameters. A positive remote estimate is stored as `estimatedPoints`; missing or failed estimates keep the action clickable and show retry guidance.
- Batch generation now reads the real `{ total, jobs }` contract, selects the first returned job as the tracked job, preserves every returned job ID for audit, and reports a detailed `GENERATION_JOB_MISSING` failure when no task can be tracked.
- `SCRIPT_SCENE` applications now validate the referenced `ContentProject`, project workspace, and `EDIT_CONTENT` membership instead of treating a content-project ID as a `CanvasProject` ID. Legacy canvas targets retain their existing canvas validation and mutation path.
- Persisted legacy stage keys are mapped into the authoritative eight-stage sequence both when resolving the route and when restoring entered/completed rail state.
- Route project changes now invalidate prior loads and atomically clear project, stage, storyboard, scene-asset, task, result, artifact, and point state. All async project loaders check a project/generation token before mutating state; scene-asset loads also reject stale responses independently.

### Fix Round 1 Verification

- Focused frontend regression: 38/38 passed (`script-workbench-routing`, `script-workbench-upstream-contract`, `scene-asset-ui-contract`).
- Backend service compatibility: `AssetApplicationServiceTest` 5/5 passed.
- Backend scene-asset lifecycle E2E: `ProjectSceneAssetLifecycleE2ETest` 17/17 passed.
- Production frontend build passed after transforming 1,998 modules to `/tmp/aicp-task9-fix1-dist`.
- Full frontend suite: 204 passed, 1 failed. The only failure remains the pre-existing `agent-config-state.test.js` identity mismatch documented above; no Task 9 file participates in that assertion.
- `git diff --check` passed.

## Fix Round 2 — Reviewer Findings

- Generation actions now keep polling the authoritative server job through `pending`/`processing` to a terminal state. The workbench publishes completed artifacts only after the server reports `completed`, preserves terminal failure/cancellation codes, supports local abort and timeout guidance, and sends cancellation to the real `/generation-jobs/{id}/cancel` endpoint before aborting local tracking.
- Script-scene asset application requests now send the explicit content-project workspace header `X-Workspace-Id: project_<id>`. The global Axios interceptor preserves this explicit header instead of overwriting it with the user's default workspace.
- Launchpad resume targets normalize legacy `content`, `review`, and `import_review` values before URL serialization. A default `creation_settings` query no longer overwrites a later legal persisted legacy resume stage.
- Every public asynchronous scene-asset operation captures a project ID plus operation generation before awaiting. List/detail/lifecycle/variant/conversion/impact/Markdown/reference-replacement/consumer-resolution responses and failures refuse all state, cache, selection, or action-result writes after a project switch or reset.

### Fix Round 2 Verification

- Task 4–9 focused frontend regression: 109/109 passed, including deferred generation job states, project-workspace request headers, legacy resume precedence, stale successful/rejected scene-asset responses, and a 9→10→9 project switch-back token case.
- Full frontend suite: 213 passed, 1 failed. The only failure remains the acknowledged pre-existing `agent-config-state.test.js` identity mismatch (`null` expected versus an empty identity object); no Task 9 file participates in that assertion.
- All modified JavaScript files passed `node --check`. `ContentProjectWorkspace.vue`, `ActionResultDrawer.vue`, and all eight stage SFCs passed Vue parse, `compileScript`, and `compileTemplate`.
- Vite production build passed after transforming 2,000 modules to `/tmp/aicp-task9-fix2-dist`.
- Backend regression passed: `ProjectSceneAssetLifecycleE2ETest` 17/17 and `AssetApplicationServiceTest` 5/5.
- `git diff --check` passed.
