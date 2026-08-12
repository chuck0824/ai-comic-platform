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

## Fix Round 3 — Generation Candidate Lifecycle

- Generation completion now persists an auditable `candidate` `ContentVersion` without changing the content unit's current-version pointer. Completed jobs expose the authoritative `result_version_id`, canonical artifact reference, candidate disposition, actual credits, and actionable error code/message.
- Added idempotent server-side accept/discard decisions. Accept atomically claims the candidate and switches the target unit's `current_version_id`; discard marks the candidate discarded and leaves the current version untouched. An accepted result cannot later be discarded, and a discarded result cannot later be accepted.
- The result drawer now persists accept/discard on the server before mutating local audit state. Accept refreshes the unit list and resolves stage content from the authoritative current named version (not the independent manual draft); discard keeps the visible current content unchanged. Server failures leave the local result available for retry.
- Paid tasks no longer substitute estimated points when the backend omits actual consumption. Terminal job errors preserve backend codes and messages through polling and workbench task/result state.
- Cancellation now supports both `pending` and `processing` jobs. Executor transitions are conditional and re-check cancellation before and after the model call and before persistence; a cancellation that wins after candidate insertion prevents completion and removes the uncommitted candidate.
- Resume-pointer persistence now normalizes legacy `content`, `review`, and `import_review` keys before comparing stage order, so revisiting an earlier stage cannot regress the authoritative resume position.

### Fix Round 3 TDD and Verification

- RED covered missing accept/discard APIs and lifecycle fields, automatic current-version switching, processing cancellation, estimated-credit fallback, missing legacy persistence comparison, missing server-first result persistence, and accepted-content refresh incorrectly reading the manual draft. Each focused failure was observed before its implementation.
- Focused frontend Task 4–9 regression: 114/114 passed, including server-first accept/discard fixtures, authoritative accepted-version refresh, actual-credit/error propagation, legacy resume persistence, generation polling, and scene-asset race isolation.
- Backend generation and content-unit regression passed: `ContentGenerationJobLifecycleServiceTest` 5/5, `GenerationJobDecisionE2ETest` 2/2, `ContentProjectM1IntegrationTest` 5/5, and `ContentProjectM2ScaleTest` 6/6. The new tests cover candidate creation without pointer mutation, accept/discard plus idempotency, truthful job views, and processing cancellation without a generated artifact.
- Backend scene-asset compatibility remained green: `ProjectSceneAssetLifecycleE2ETest` 17/17 and `AssetApplicationServiceTest` 5/5. Spring E2E classes were verified in isolated Maven invocations because the repository's fixed-name H2 test database is retained across dirtied contexts and otherwise re-runs the default-user seed.
- Full frontend suite: 218 passed, 1 failed. The sole failure remains the acknowledged pre-existing `agent-config-state.test.js` identity mismatch (`null` expected versus an empty identity object); no Task 9 file participates in that assertion.
- Modified JavaScript passed `node --check`; `ContentProjectWorkspace.vue`, `ActionResultDrawer.vue`, and all eight stage SFCs passed Vue parse, `compileScript`, and `compileTemplate`.
- Vite production build passed after transforming 2,001 modules to `/tmp/aicp-task9-fix3-dist`.
- `git diff --check` passed.

## Fix Round 4 — Candidate Security, Concurrency, and Reload Isolation

- Generation-job read, cancel, accept, and discard now derive the authenticated user in the controller and enforce project `VIEW` or `EDIT_CONTENT` access in the service. Every operation also verifies that the job target is a content unit belonging to the same project; the endpoint E2E runs the real Spring Security filter chain and covers missing authentication and cross-user denial.
- Job creation captures the target unit ID, revision, and current-version pointer inside the durable generation snapshot and recomputes the snapshot hash. Accept uses that generation-time baseline for an atomic unit CAS before claiming the candidate in the same transaction. Two candidates generated from one baseline can no longer sequentially replace one another: the winner becomes current/accepted while the loser remains candidate with a `409` edit conflict. Legacy jobs without target-baseline metadata keep backward-compatible acceptance semantics.
- Stale manual autosaves now claim the unit revision atomically and never write the current-version pointer. Candidate/discarded versions are excluded from public version lists and rejected by restore. Failed and cancelled jobs do not expose candidate IDs or artifact references.
- Executor cancellation/failure races physically remove an inserted candidate before returning or marking failure; a failed completion CAS also removes the candidate. Internal target-baseline metadata is excluded from the model prompt.
- Workspace reload resolves `unit.current_version_id` from accepted named versions before considering the independent manual draft, so an adopted result remains authoritative after leaving and reopening the project.
- Accept/discard UI actions now have a per-result in-flight guard. Double clicks share one in-flight promise, and an authoritative server response with the already-requested disposition is treated as idempotent success even when local audit state is already terminal.

### Fix Round 4 TDD and Verification

- RED explicitly reproduced the sequential same-baseline candidate overwrite (`200` on the second accept); GREEN returns `409` and preserves the losing candidate. Additional RED covered missing security user propagation, public candidate visibility/restore, stale autosave mutation, current-version reload precedence, and duplicate UI decisions.
- Backend generation/content/security service regression: 33/33 passed across `ContentGenerationJobLifecycleServiceTest`, `ContentUnitCandidateIsolationServiceTest`, `ContentProjectM1IntegrationTest`, `ContentProjectM2ScaleTest`, `ContentProjectServiceTest`, and `ContentProjectEnumsTest`. The lifecycle suite includes post-insert failure cleanup and cancellation/completion races.
- Real-filter generation decision E2E: 4/4 passed, covering authentication/authorization, truthful failed-job exposure, pending/processing cancellation, accept/discard idempotency, same-baseline candidate conflict, and candidate/discarded list/restore isolation.
- Frontend Task 4–9 focused regression: 116/116 passed. Full frontend suite: 220 passed, 1 failed; the only failure remains the acknowledged pre-existing `agent-config-state.test.js` identity mismatch (`null` expected versus an empty identity object), outside Task 9 files.
- `ContentProjectWorkspace.vue`, `ActionResultDrawer.vue`, and all eight stage SFCs passed Vue parse, `compileScript`, and `compileTemplate`; modified JavaScript passed `node --check`.
- Vite production build passed after transforming 2,001 modules to `/tmp/aicp-task9-fix4-dist`.
- `git diff --check` passed.

## Fix Round 5 — Generic Job Compatibility and Unified Version Visibility

- Generation-job view and cancellation authorize by the job's project for generic and legacy targets such as `project`; they no longer reinterpret every target ID as a content-unit ID. Content-unit aliases still receive project/unit consistency validation, while accept/discard explicitly reject non-content-unit targets.
- Legacy candidate jobs without a complete, verifiable `_generation_target` unit/revision/current baseline now fail adoption with `GENERATION_BASELINE_REQUIRED` guidance to regenerate. Already accepted or discarded results remain idempotent because their matching terminal disposition is resolved before candidate adoption validation.
- Added one `ContentVersionSelector` policy for public visibility and downstream source selection. Explicit generation context rejects candidate/discarded versions. Storyboard, three-agent review, hook generation, and promotion use `ContentUnit.currentVersionId` as the authoritative source; only units without a current pointer fall back to the latest public draft/accepted version, never candidate/discarded content. Public version list and restore reuse the same predicate.
- Generation decision locking is keyed by task plus decision. Repeated accepts or repeated discards share one in-flight promise; an accept/discard race in either direction returns `IN_FLIGHT_CONFLICT` immediately and never invokes the second server action.

### Fix Round 5 TDD and Verification

- RED reproduced project-target GET/cancel rejection, legacy candidate adoption using accept-time unit state, candidate entry into generation context, all four downstream prompts consuming a higher candidate, and opposite UI decisions sharing the wrong in-flight promise. Each failure became GREEN after the scoped implementation.
- Backend generation/context/downstream regression: 35/35 passed across lifecycle, candidate isolation, context assembly, four downstream consumers, and M1/M2 compatibility. Real-filter generation decision E2E remained 4/4.
- Frontend Task 4–9 focused regression: 117/117 passed. Full frontend suite: 221 passed, 1 failed; the only failure remains the pre-existing `agent-config-state.test.js` identity mismatch outside Task 9 files.
- `ContentProjectWorkspace.vue`, `ActionResultDrawer.vue`, and all eight stage SFCs passed Vue parse, `compileScript`, and `compileTemplate`; modified JavaScript passed `node --check`.
- Vite production build passed after transforming 2,001 modules to `/tmp/aicp-task9-fix5-dist`.
- `git diff --check` passed.

## Fix Round 5 Final Subfix — Decision Guidance and Review Content Fallback

- The workspace now awaits the generation-decision guard itself and publishes every denied result through the existing actionable-guidance surface. If accept is already running and the result drawer fires discard without consuming its promise, the second server API is not called and the user still sees `IN_FLIGHT_CONFLICT` guidance.
- Three-agent review now prefers nonblank `plainText`, falls back to the authoritative public version's `contentJson`, and reports `no_content` only when both representations are empty. A current accepted JSON-only version therefore reaches all review prompts without a null dereference.

### Final Subfix TDD and Verification

- RED reproduced both defects: the frontend helper export was absent for the ignored-promise conflict scenario, and a current public version with `plainText = null` raised a `NullPointerException` before review. Both focused regressions passed after the minimal implementations.
- Backend generation/context/downstream regression: 36/36 passed across lifecycle, candidate isolation, context assembly, four downstream consumers, and M1/M2 compatibility.
- Frontend Task 4–9 focused regression: 118/118 passed. Full frontend suite: 222 passed, 1 failed; the only failure remains the acknowledged pre-existing `agent-config-state.test.js` identity mismatch outside Task 9 files.
- `ContentProjectWorkspace.vue`, `ActionResultDrawer.vue`, and all eight stage SFCs passed Vue parse, `compileScript`, and `compileTemplate`; modified JavaScript passed `node --check`.
- Vite production build passed after transforming 2,001 modules to `/tmp/aicp-task9-fix5-final-dist`.
- `git diff --check` passed.
