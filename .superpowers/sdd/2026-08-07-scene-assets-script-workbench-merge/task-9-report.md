## Task 9 report — Native Workspace Merge and Launchpad Routing

### Outcome

- Kept `/script-gen` as the four-card launchpad with recent-project continuation.
- Routed quick/professional/upload/TVC creation through the native `/script-gen/:projectId/workspace` shell.
- Project creation now persists its initial parameter version before navigation; TVC retains product, campaign objective, and duration fields.
- Replaced the legacy `story_seed/characters/synopsis/...` workspace body with the authoritative eight-stage Tasks 5–8 components.
- Mounted shared workflow, scene asset library, model/point context, guidance, generation progress, result drawer, transition progress, autosave, route validation, and final completion.
- Restores per-stage drafts from content units and restores the active stage without allowing unknown or skipped direct navigation.
- Injected real content-project, generation, review, storyboard, scene-asset application, and canvas-snapshot APIs where endpoints exist. Missing prerequisites return actionable guidance instead of false success.
- `SCRIPT_SCENE` application preserves legacy numeric `target_id` when possible and always sends stable `target_key`/`sceneId` plus a deterministic idempotency key.

### TDD / verification

- RED added for launch routes, stage validation, native-shell mounting, draft restoration, real adapter injection, and stable scene identity.
- Focused Task 4–9 suite: **86/86 passed**.
- Route/entry suite: **14/14 passed**.
- Full frontend suite: **200/201 passed**. The sole failure is the acknowledged pre-existing `agent-config-state.test.js` expectation (`identity: null` versus the existing `{ name: '', description: '' }` implementation); Task 9 does not touch those files.
- Vue SFC production build to isolated `/tmp/aicp-task9-build`: **passed** (1997 modules transformed). Only the existing Rollup annotation and mixed static/dynamic scene API chunk warnings were emitted.
- `git diff --check`: **passed**.

### Main files

- `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue`
- `aicp-frontend/src/views/content-project/ContentProjectCreate.vue`
- `aicp-frontend/src/views/content-project/ScriptCreationHome.vue`
- `aicp-frontend/src/views/content-project/workbench/workspaceRouting.js`
- `aicp-frontend/src/views/content-project/workbench/workspaceAdapters.js`
- `aicp-frontend/src/api/contentProject.js`
- `aicp-frontend/src/api/sceneAsset.js`
- `aicp-frontend/tests/script-workbench-routing.test.js`
- `aicp-frontend/tests/content-project-workflow.test.js`

### Known boundary

- Generation endpoints return accepted task identifiers. The shared drawer records the real task path; downstream generated artifact bodies continue to arrive through the existing generation/content-unit backend lifecycle.
- Canvas creation uses the existing locked storyboard snapshot job. If the job does not immediately expose a canvas project ID, the UI reports that the durable job was created instead of fabricating a redirect.
