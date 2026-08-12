# Task 9 Fix Round 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make native script-workbench generation, project-scoped scene assets, legacy resume URLs, and project switching truthful and race-safe.

**Architecture:** Keep business logic in small workbench helpers and let `ContentProjectWorkspace.vue` adapt them to the existing workbench state. Every project-scoped async operation captures a project identity plus generation token before awaiting and refuses all stale writes. Backend contracts remain unchanged.

**Tech Stack:** Vue 3, Node test runner, Axios request wrapper, Spring Boot content-generation APIs, Vite.

## Global Constraints

- Strict RED→GREEN TDD for every behavior change.
- Do not synthesize successful generation artifacts.
- Do not change legacy canvas asset behavior.
- Do not expand beyond the four reviewer findings.

---

### Task 1: Durable generation-job tracking

**Files:**
- Create: `aicp-frontend/src/views/content-project/workbench/generationJobTracker.js`
- Modify: `aicp-frontend/src/api/contentProject.js`
- Modify: `aicp-frontend/src/views/content-project/ContentProjectWorkspace.vue`
- Test: `aicp-frontend/tests/script-workbench-routing.test.js`

**Interfaces:**
- Produces `trackGenerationJob({ job, getJob, onProgress, signal, timeoutMs, pollIntervalMs })` returning a terminal completed record or throwing a coded failed/cancelled/timeout error.

- [ ] Add deferred queued→running→completed, failed, cancelled, cancellation-signal, and timeout tests.
- [ ] Run the focused test and verify failures are caused by the missing tracker.
- [ ] Implement status normalization, 0–100 progress/subtask callbacks, abortable polling, and terminal errors.
- [ ] Add real generation-job get/cancel API methods and wire workbench generation/cancel to them.
- [ ] Run focused tests to GREEN.

### Task 2: Explicit project workspace header

**Files:**
- Modify: `aicp-frontend/src/api/sceneAsset.js`
- Test: `aicp-frontend/tests/scene-asset-api-contract.test.js`

**Interfaces:**
- Produces `projectWorkspaceConfig(projectId)` with `{ headers: { 'X-Workspace-Id': 'project_<id>' } }` for the protected application endpoint.

- [ ] Add a request-spy test asserting the application POST receives the project workspace header.
- [ ] Run RED.
- [ ] Add the explicit Axios request config; verify other content-project scene APIs are not protected by the workspace filter.
- [ ] Run GREEN.

### Task 3: Legacy resume mapping and precedence

**Files:**
- Modify: `aicp-frontend/src/views/content-project/workbench/workspaceRouting.js`
- Test: `aicp-frontend/tests/script-workbench-routing.test.js`

**Interfaces:**
- `normalizePersistedStage(stage)` additionally maps `import_review`.
- `workspaceTarget()` normalizes legacy stage values.
- `resolveWorkspaceStage()` prevents a default `creation_settings` query from overwriting a later legal persisted legacy resume stage.

- [ ] Add URL and resolution tests for `content`, `review`, and `import_review`.
- [ ] Run RED.
- [ ] Apply normalization before URL serialization and implement persisted-vs-explicit-query precedence.
- [ ] Run GREEN.

### Task 4: Scene-asset async race isolation

**Files:**
- Modify: `aicp-frontend/src/views/content-project/workbench/useSceneAssets.js`
- Test: `aicp-frontend/tests/scene-asset-ui-contract.test.js`

**Interfaces:**
- Every async public operation captures a `{ projectId, token }` operation context and returns `STALE_PROJECT_RESPONSE` before changing refs, caches, or action-result storage when invalid.

- [ ] Add deferred tests for stale `loadAsset`, successful mutation, and rejected mutation after project switch.
- [ ] Run RED.
- [ ] Add one operation-generation guard and apply it at every await/write boundary in load, detail, lifecycle, conversion, impact/markdown, replace, and resolve flows.
- [ ] Run GREEN and existing scene-asset tests.

### Task 5: Verification and report

**Files:**
- Modify: `.superpowers/sdd/2026-08-07-scene-assets-script-workbench-merge/task-9-report.md`

- [ ] Run Task 4–9 focused frontend suites.
- [ ] Run the full frontend suite and record only observed baseline failures.
- [ ] Parse all modified Vue SFCs and run Vite production build.
- [ ] Run required backend scene-asset tests.
- [ ] Run `git diff --check`, append evidence to the report, and commit the round.
