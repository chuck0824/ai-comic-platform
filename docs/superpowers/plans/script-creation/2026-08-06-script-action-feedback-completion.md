# Script Action Feedback Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every displayed business button in the six downstream creation stages clickable, show actionable guidance when prerequisites are missing, and produce persistent, visible results when operations succeed.

**Architecture:** Add a pure action-precondition layer to `WorkflowModel`, route every click through one `guardAction` entry point, and standardize condition, generation, and result overlays. Keep the current single-file static prototype architecture, but express guard rules and result records as pure functions so Node tests can verify behavior while Browser verifies the rendered interaction matrix.

**Tech Stack:** Static HTML/CSS/JavaScript, Node.js built-in test runner, Codex in-app Browser, Obsidian Markdown artifact model.

## Global Constraints

- Every displayed business button remains clickable; missing prerequisites open a visible guidance overlay.
- Do not use native `disabled` for business actions whose missing prerequisite can be explained and resolved.
- Successful edit, generate, confirm, export, archive, and canvas actions produce content, status, version, task, billing, or result records that can be revisited.
- AI generation follows model selection → estimate → percentage progress → result diff → accept/discard.
- Demo models consume 0 points; real models use `1 quota = 1 积分` under 3001 pricing rules.
- Upstream artifact changes create new Markdown versions and mark dependent artifacts stale without deleting old versions.
- Preserve unrelated untracked files in the shared workspace.

---

### Task 1: Action Guard and Condition Guidance

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Produces: `WorkflowModel.evaluateActionPrecondition(context, action): { allowed:boolean, code:string, title:string, message:string, targetAction?:string }`
- Produces: `guardAction(action, element): boolean`; returns `true` only when dispatch may continue.
- Produces: overlay type `action-guidance` using `overlayState.payload`.

- [ ] **Step 1: Write failing action-precondition tests**

```js
test('business actions remain clickable and explain missing prerequisites', () => {
  const model = loadModel();
  const missingBlock = model.evaluateActionPrecondition({ selectedBlockId:null }, 'ai-continue');
  assert.equal(missingBlock.allowed, false);
  assert.equal(missingBlock.code, 'SCRIPT_BLOCK_REQUIRED');
  assert.equal(missingBlock.targetAction, 'focus-script-blocks');

  const missingShot = model.evaluateActionPrecondition({ selectedShotId:null }, 'split-shot');
  assert.equal(missingShot.code, 'SHOT_REQUIRED');

  const blockedReview = model.evaluateActionPrecondition({ issues:[{ severity:'HIGH', status:'OPEN' }] }, 'approve-review');
  assert.equal(blockedReview.code, 'REVIEW_BLOCKED');
});

test('AI business buttons are not natively disabled', () => {
  assert.doesNotMatch(html, /data-action="ai-continue"[^>]*disabled/);
  assert.match(html, /overlayFrame\('action-guidance'/);
});
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `node --test --test-name-pattern='business actions|AI business buttons' tests/script-creation-prototype.test.cjs`

Expected: FAIL because `evaluateActionPrecondition` and `action-guidance` do not exist and AI buttons still render `disabled`.

- [ ] **Step 3: Implement the pure guard rules**

Add to `WorkflowModel`:

```js
function evaluateActionPrecondition(context = {}, action = '') {
  if (['ai-continue','ai-conflict','ai-condense-dialogue','ai-rewrite-tone','ai-character-check'].includes(action) && !context.selectedBlockId) {
    return { allowed:false, code:'SCRIPT_BLOCK_REQUIRED', title:'请先选择正文块', message:'选择一个动作、对白或旁白正文块后才能执行此操作。', targetAction:'focus-script-blocks' };
  }
  if (['split-shot','merge-shot'].includes(action) && !context.selectedShotId) {
    return { allowed:false, code:'SHOT_REQUIRED', title:'请先选择镜头', message:'在表格行或镜头卡片中选择一个镜头后再继续。', targetAction:'focus-storyboard-shots' };
  }
  if (action === 'approve-review' && !canApproveReview(context.issues || [])) {
    return { allowed:false, code:'REVIEW_BLOCKED', title:'本集暂不能审核通过', message:'仍有未解决的高风险或阻断问题。', targetAction:'focus-review-blockers' };
  }
  if (action === 'confirm-adaptation' && !context.selectedHook) {
    return { allowed:false, code:'HOOK_REQUIRED', title:'请选择高压开场', message:'确认改编方案前必须选定一个高压开场。', targetAction:'open-hook-selector' };
  }
  return { allowed:true, code:'ALLOW', title:'', message:'' };
}
```

Remove `disabled` from the five script AI buttons. Add `guardAction` before handler dispatch and an `action-guidance` overlay with “返回当前页面” plus the returned `targetAction` button.

- [ ] **Step 4: Run the focused and full tests**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "fix: add actionable prerequisite guidance"
```

---

### Task 2: Shared Generation Progress and Result Records

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Consumes: `WorkflowModel.estimatePoints(model, usage)` and `WorkflowModel.recordPoints(state, entry)`.
- Produces: `WorkflowModel.createActionResult(state, spec): ActionResult`.
- Produces: `startGenerationFlow(spec)`, `finishGenerationFlow()`, `acceptGenerationResult()`.
- Produces: overlays `generation-progress` and `generation-result`.

- [ ] **Step 1: Write failing generation-result tests**

```js
test('generation completion creates a revisitable result record', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const result = model.createActionResult(state, {
    action:'regenerate', status:'SUCCEEDED', artifactId:'ADAPT-001',
    path:'04-改编方案.md', version:2, taskId:'task-1', actualPoints:0
  });
  assert.equal(state.actionResults.length, 1);
  assert.equal(result.version, 2);
  assert.equal(result.path, '04-改编方案.md');
});

test('shared generation exposes progress result accept and discard actions', () => {
  for (const action of ['accept-generation-result','discard-generation-result','open-generation-task']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  assert.match(html, /overlayFrame\('generation-progress'/);
  assert.match(html, /overlayFrame\('generation-result'/);
});
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test --test-name-pattern='generation completion|shared generation' tests/script-creation-prototype.test.cjs`

Expected: FAIL because action result storage and generation overlays are missing.

- [ ] **Step 3: Implement generation state and overlays**

Initialize:

```js
actionResults: [],
generation: { visible:false, status:null, progress:0, spec:null, taskId:null, result:null }
```

Implement `createActionResult` as an append-only record with `id`, `createdAt`, action, artifact, path, version, task, and points fields. `startGenerationFlow` creates a task and opens percentage progress. Reuse `advanceTransitionProgress`; only `SUCCEEDED` reaches 100. On completion open a side-by-side result overlay. Accept invokes the stage-specific artifact writer and creates a billing/action result record; discard keeps only the task record.

- [ ] **Step 4: Run full tests**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: add shared generation progress and results"
```

---

### Task 3: Novel Analysis and Adaptation Completion

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Consumes: `guardAction`, `startGenerationFlow`, `upsertArtifact`, `openAnalysisImpactReview`.
- Produces: overlay `hook-selector` and handlers `open-hook-selector`, `choose-hook-and-close`, `view-action-result`.

- [ ] **Step 1: Write failing behavior-contract tests**

```js
test('analysis and adaptation actions have editors guards and persistent results', () => {
  for (const id of ['summary-editor','event-editor','character-library','world-editor','hook-selector','action-result']) {
    assert.match(html, new RegExp(`overlayFrame\\('${id}'`));
  }
  for (const action of ['save-summary','save-event','save-character','save-world','choose-hook-and-close','view-action-result']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  assert.match(html, /03-小说分析\/故事梗概\.md/);
  assert.match(html, /04-改编方案\.md/);
});
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test --test-name-pattern='analysis and adaptation actions' tests/script-creation-prototype.test.cjs`

Expected: FAIL because `hook-selector`, `choose-hook-and-close`, and `action-result` are missing.

- [ ] **Step 3: Implement visible completion behavior**

Keep the four existing analysis editors, but after save show `action-result` rather than immediately replacing it with an impact overlay. The result lists the changed Markdown path/version and offers “查看关联影响”. Change “选择高压开场” to open `hook-selector`; selection closes the selector, updates the chosen card, and records an action result. Confirm adaptation routes through guard rules and displays the confirmed artifact result. Route “重新生成当前产物” through Task 2 generation flow.

- [ ] **Step 4: Run full tests and browser-check analysis/adaptation**

Run: `node --test tests/script-creation-prototype.test.cjs`

Browser assertions: editor/selector visible; saved summary text changes; selected hook card has `aria-pressed="true"`; confirmed adaptation result shows `04-改编方案.md` and a version.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "fix: complete analysis and adaptation action feedback"
```

---

### Task 4: Structure and Script Body Completion

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Consumes: action guard, shared generation flow, `saveScriptArtifact`.
- Produces: visible result records for structure save, beat save/regeneration, scene/block save, script checks and export.

- [ ] **Step 1: Write failing tests for non-silent script actions**

```js
test('structure and script operations expose visible outputs', () => {
  for (const action of ['view-structure-result','view-script-result','focus-script-blocks','open-script-check','open-script-export']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  assert.doesNotMatch(html, /data-action="ai-(?:continue|conflict|condense-dialogue|rewrite-tone|character-check)"[^>]*disabled/);
});
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test --test-name-pattern='structure and script operations' tests/script-creation-prototype.test.cjs`

Expected: FAIL because result and focus actions are missing.

- [ ] **Step 3: Implement complete behavior**

Make structure editor and beat editor saves show path/version results. Route beat/current regeneration through shared generation. `focus-script-blocks` closes guidance and applies a temporary highlight class to selectable blocks. AI operations open diff once a block is selected. Scene and block saves show the new IDs and `06-剧本正文/EP-001-剧本正文.md` version. Script check and export retain their dedicated result surfaces and also create action result records.

- [ ] **Step 4: Run full tests and browser-check stage 5–6**

Run: `node --test tests/script-creation-prototype.test.cjs`

Browser assertions: clicking AI without selection opens guidance; choosing a block enables the diff flow; accepted AI text appears; scene/block counts change; structure/script Markdown versions increment.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "fix: complete structure and script body actions"
```

---

### Task 5: Review, Storyboard, and Delivery Completion

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Consumes: action guard, shared generation, `saveReviewArtifact`, `saveStoryboardArtifact`.
- Produces: result overlays for review approval, archive, export, and canvas handoff.

- [ ] **Step 1: Write failing delivery-result tests**

```js
test('review storyboard and delivery operations never end with toast only', () => {
  for (const id of ['review-filter','review-editor','review-diff','review-approval','shot-editor','continuity-check','archive-result','export-result','canvas-result']) {
    assert.match(html, new RegExp(`overlayFrame\\('${id}'`));
  }
  for (const action of ['focus-review-blockers','focus-storyboard-shots','view-archive-result','view-export-result','view-canvas-result']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test --test-name-pattern='review storyboard and delivery operations' tests/script-creation-prototype.test.cjs`

Expected: FAIL because result overlays and focus actions are missing.

- [ ] **Step 3: Implement review and storyboard feedback**

`focus-review-blockers` closes guidance, resets filters to open HIGH/BLOCKER, and highlights results. Review save/diff/approval each create or show persistent records. `focus-storyboard-shots` closes guidance and highlights selectable rows/cards. Add/split/merge/save/undo show a result row with shot count and version. Continuity check persists its three checks. Current-product regeneration uses shared generation.

- [ ] **Step 4: Implement delivery result pages**

After archive confirmation set the project read-only and open `archive-result` with “查看项目详情”. Export completion opens `export-result` with format, scope, file name, Vault file count, and task ID. Canvas confirmation opens `canvas-result` with handed-off shot/asset counts and a generated demo project ID. Keep static-demo wording explicit; do not claim an external backend mutation.

- [ ] **Step 5: Run tests and browser-check stages 7–8**

Run: `node --test tests/script-creation-prototype.test.cjs`

Browser assertions: blocked review opens guidance; fixing HIGH enables approval; split/merge without selection opens guidance; selected operations change shot count; archive/export/canvas show result pages.

- [ ] **Step 6: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "fix: complete review storyboard and delivery results"
```

---

### Task 6: Behavior Matrix, PRD Sync, and Preview Recovery

**Files:**
- Modify: `tests/script-creation-prototype.test.cjs`
- Modify: `漫剧视频创作平台_PRD.md`
- Create: `artifacts/script-action-feedback-2026-08-06/behavior-matrix.md`
- Create: `artifacts/script-action-feedback-2026-08-06/final-action-results.png`

**Interfaces:**
- Consumes: all action handlers and result overlays from Tasks 1–5.
- Produces: reviewed browser behavior matrix for every user-listed action.

- [ ] **Step 1: Add the PRD acceptance contract test**

```js
test('PRD requires clickable actions condition guidance and persistent results', () => {
  for (const term of ['所有业务按钮可点击','条件不足','操作前需要完成','结果弹层','不能仅以 Toast','行为结果测试']) {
    assert.ok(prd.includes(term), `missing ${term}`);
  }
});
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test --test-name-pattern='PRD requires clickable actions' tests/script-creation-prototype.test.cjs`

Expected: FAIL until the PRD contains the approved interaction contract.

- [ ] **Step 3: Update the PRD**

Add the Action Guard rules, condition guidance fields, generation/result state flow, six-stage operation matrix, and behavior-level acceptance criteria from the approved spec. State explicitly that Toast is supplementary feedback only.

- [ ] **Step 4: Restore the preview server**

Check `.superpowers/brainstorm/.last-port` and the current server process. If port `62096` is not listening, restart the existing prototype preview command from the same workspace and verify:

```bash
curl -fsS http://localhost:62096/ | rg '八阶段剧本创作工作台'
```

- [ ] **Step 5: Execute the browser behavior matrix**

For each action named by the user, record stage, prerequisites, click result, saved result, and pass/fail in `artifacts/script-action-feedback-2026-08-06/behavior-matrix.md`. Test both missing-prerequisite and allowed paths for AI edit, review approval, split shot, and merge shot. Capture the final visible result page.

- [ ] **Step 6: Run final verification**

Run:

```bash
node --test tests/script-creation-prototype.test.cjs
git diff --check
curl -fsS http://localhost:62096/ | rg 'action-guidance|generation-result|archive-result|canvas-result'
```

Expected: all Node tests PASS, no whitespace errors, served HTML contains the completed interaction surfaces, and Browser console has no unhandled errors.

- [ ] **Step 7: Commit**

```bash
git add tests/script-creation-prototype.test.cjs 漫剧视频创作平台_PRD.md artifacts/script-action-feedback-2026-08-06
git commit -m "test: verify complete script action feedback matrix"
```
