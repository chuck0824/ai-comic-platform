# Script Creation Interactive Prototype Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current eight-stage script-creation static prototype into a complete, demonstrable interaction flow and update the module PRD to match the implemented pages, states, fields, dependencies, and acceptance rules.

**Architecture:** Preserve the current visual shell and single HTML delivery, but reorganize its inline JavaScript into a testable workflow-model block, render functions, and one delegated action dispatcher. Use in-memory demo state plus short timers for task and autosave simulation; no backend, persistence, or framework dependencies are added.

**Tech Stack:** HTML5, CSS3, browser JavaScript, Node.js built-in `node:test`, in-app Browser validation, Markdown PRD.

## Global Constraints

- Keep the stage order exactly: 创作设置 → 小说上传 → 小说分析 → 改编方案 → 结构化文字剧本 → 剧本正文 → 审核修订 → 文字分镜.
- Keep delivery outside the eight-stage navigation.
- Preserve the current dark global navigation, white workflow rail, light workspace, white cards, and purple primary-action language.
- Do not add a framework, bundler, package dependency, backend call, persistent browser storage, or real file download.
- Every main action must produce visible feedback: page, panel, state, validation message, task, version, notification, or result.
- Stages 1–7 must use a shared transition footer and a visible transition-progress page before entering the next stage.
- Stage 8 must expose lock, export, and canvas handoff actions instead of a next-stage action.
- Stage completion must come from workflow state, never from the current navigation index.
- Status must include text and cannot rely only on color.
- Update `漫剧视频创作平台_PRD.md` in place; do not create a competing PRD.

---

### Task 1: Establish testable workflow state and contract tests

**Files:**
- Create: `tests/script-creation-prototype.test.cjs`
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`

**Interfaces:**
- Produces: `WorkflowModel.createInitialState()`, `WorkflowModel.canEnterStage(state, index)`, `WorkflowModel.computeProgress(state)`, `WorkflowModel.markDownstreamStale(state, sourceStage, episodeNo)`, `WorkflowModel.createTask(state, spec)`, and `WorkflowModel.completeTask(state, taskId)`.
- Consumes: existing eight-stage labels and sample project data.

- [ ] **Step 1: Write failing workflow-model tests**

```js
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const htmlPath = '.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html';
const html = fs.readFileSync(htmlPath, 'utf8');

function loadModel() {
  const match = html.match(/\/\* WORKFLOW_MODEL_START \*\/([\s\S]*?)\/\* WORKFLOW_MODEL_END \*\//);
  assert.ok(match, 'workflow model block must exist');
  const sandbox = {};
  vm.runInNewContext(`${match[1]};globalThis.__model=WorkflowModel`, sandbox);
  return sandbox.__model;
}

test('uses the approved eight-stage order', () => {
  const expected = ['创作设置','小说上传','小说分析','改编方案','结构化文字剧本','剧本正文','审核修订','文字分镜'];
  for (let index = 0; index < expected.length; index += 1) {
    assert.ok(html.indexOf(`name: '${expected[index]}'`) > -1);
    if (index) assert.ok(html.indexOf(`name: '${expected[index]}'`) > html.indexOf(`name: '${expected[index - 1]}'`));
  }
});

test('locks analysis until source is confirmed', () => {
  const model = loadModel();
  const state = model.createInitialState();
  assert.equal(model.canEnterStage(state, 2).allowed, false);
  state.source.state = 'CONFIRMED';
  assert.equal(model.canEnterStage(state, 2).allowed, true);
});

test('progress is based on completed stages instead of active index', () => {
  const model = loadModel();
  const state = model.createInitialState();
  state.project.activeStage = 7;
  assert.equal(model.computeProgress(state), 0);
  state.stages[0].status = 'COMPLETED';
  state.stages[1].status = 'COMPLETED';
  assert.equal(model.computeProgress(state), 25);
});

test('changing adaptation marks only downstream stages stale', () => {
  const model = loadModel();
  const state = model.createInitialState();
  state.stages.forEach(stage => { stage.status = 'COMPLETED'; });
  model.markDownstreamStale(state, 3);
  assert.deepEqual(state.stages.map(stage => stage.status), ['COMPLETED','COMPLETED','COMPLETED','COMPLETED','STALE','STALE','STALE','STALE']);
});

test('task transitions create a new version on success', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const task = model.createTask(state, { type: 'ANALYSIS', stage: 2, scope: '全部模块' });
  assert.equal(task.status, 'QUEUED');
  model.completeTask(state, task.id);
  assert.equal(state.tasks[0].status, 'SUCCEEDED');
  assert.equal(state.versions.analysis.length, 1);
});

test('stage transition requires confirmation and opens the next stage only after success', () => {
  const model = loadModel();
  const state = model.createInitialState();
  state.stages[0].status = 'NEEDS_CONFIRMATION';
  const transition = model.startStageTransition(state, 0);
  assert.equal(transition.status, 'QUEUED');
  assert.equal(model.canEnterStage(state, 1).allowed, false);
  model.completeStageTransition(state, transition.taskId);
  assert.equal(state.stages[0].status, 'COMPLETED');
  assert.equal(state.stages[1].status, 'READY');
  assert.equal(model.canEnterStage(state, 1).allowed, true);
});
```

- [ ] **Step 2: Run tests and verify the model tests fail**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL with `workflow model block must exist`.

- [ ] **Step 3: Add the workflow-model block and replace index-based progress**

Insert this shape in the inline script and fill the initial demo state with the existing sample data:

```js
/* WORKFLOW_MODEL_START */
const WorkflowModel = (() => {
  const STAGE_STATUS = ['LOCKED','READY','RUNNING','NEEDS_CONFIRMATION','COMPLETED','STALE','FAILED'];
  const TASK_STATUS = ['QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELED'];

  function createInitialState() {
    return {
      screen: 'home',
      project: { id:'demo-project-1', name:'重生后，我在三集内揭开命运系统', activeStage:0, status:'IN_PROGRESS', archived:false },
      source: { mode:'text', state:'EMPTY', file:null, chapters:[], selectedRange:[1,50], warnings:[] },
      stages: Array.from({ length:8 }, (_, index) => ({ status:index === 0 ? 'READY' : 'LOCKED' })),
      tasks: [],
      versions: { settings:[], source:[], analysis:[], adaptation:[], structure:[], script:[], review:[], storyboard:[] },
      staleImpacts: [],
      notifications: []
    };
  }

  function canEnterStage(state, index) {
    const checks = [
      () => true,
      () => state.stages[0].status === 'COMPLETED',
      () => state.source.state === 'CONFIRMED',
      () => state.stages[2].status === 'COMPLETED',
      () => state.stages[3].status === 'COMPLETED',
      () => state.stages[4].status === 'COMPLETED',
      () => state.stages[5].status === 'COMPLETED',
      () => state.stages[6].status === 'COMPLETED'
    ];
    const allowed = checks[index]();
    return { allowed, reason: allowed ? '' : '请先完成前置阶段并确认有效产物' };
  }

  function computeProgress(state) {
    return Math.round(state.stages.filter(stage => stage.status === 'COMPLETED').length / state.stages.length * 100);
  }

  function markDownstreamStale(state, sourceStage, episodeNo = null) {
    for (let index = sourceStage + 1; index < state.stages.length; index += 1) state.stages[index].status = 'STALE';
    state.staleImpacts.push({ sourceStage, episodeNo, affectedStages:[sourceStage + 1, state.stages.length - 1] });
  }

  function createTask(state, spec) {
    const task = { id:`task-${state.tasks.length + 1}`, status:'QUEUED', createdAt:Date.now(), ...spec };
    state.tasks.unshift(task);
    return task;
  }

  function completeTask(state, taskId) {
    const task = state.tasks.find(item => item.id === taskId);
    task.status = 'SUCCEEDED';
    const key = ['settings','source','analysis','adaptation','structure','script','review','storyboard'][task.stage];
    state.versions[key].push({ version:`V${state.versions[key].length + 1}`, source:'AI 生成', createdAt:Date.now() });
    return task;
  }

  function startStageTransition(state, fromStage) {
    const task = createTask(state, { type:'STAGE_TRANSITION', stage:fromStage, scope:`阶段 ${fromStage + 1} → ${fromStage + 2}` });
    state.transition = { visible:true, fromStage, toStage:fromStage + 1, status:'QUEUED', taskId:task.id, error:null };
    return state.transition;
  }

  function completeStageTransition(state, taskId) {
    completeTask(state, taskId);
    const transition = state.transition;
    transition.status = 'SUCCEEDED';
    state.stages[transition.fromStage].status = 'COMPLETED';
    state.stages[transition.toStage].status = 'READY';
    return transition;
  }

  return { STAGE_STATUS, TASK_STATUS, createInitialState, canEnterStage, computeProgress, markDownstreamStale, createTask, completeTask, startStageTransition, completeStageTransition };
})();
/* WORKFLOW_MODEL_END */
```

- [ ] **Step 4: Run the model tests and verify they pass**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: 5 tests pass, 0 fail.

- [ ] **Step 5: Commit the state foundation**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "test: define script creation workflow state"
```

---

### Task 2: Add project management and global workflow overlays

**Files:**
- Modify: `tests/script-creation-prototype.test.cjs`
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`

**Interfaces:**
- Consumes: `appState`, `WorkflowModel.createTask()`, and existing `showHome()`/`render()`.
- Produces: `openOverlay(type, payload)`, `closeOverlay()`, `renderProjectList()`, `renderOverlay()`, and a document-level `dispatchAction(action, element)`.
- Produces: shared `renderStageFooter()`, `renderTransitionPage()`, `startTransition()`, `cancelTransition()`, `retryTransition()`, and `enterTransitionTarget()` behavior.

- [ ] **Step 1: Add failing global-surface contract tests**

```js
test('includes every P0 global surface and delegated action dispatcher', () => {
  for (const id of ['project-list-view','workflow-overlay','task-center','version-history','version-diff','regenerate-dialog','stale-impact-dialog','export-dialog','archive-dialog']) {
    assert.match(html, new RegExp(`id=["']${id}["']`));
  }
  assert.match(html, /function dispatchAction\(action, element\)/);
  assert.match(html, /data-action="open-project-detail"/);
  assert.match(html, /data-action="open-task-center"/);
  assert.match(html, /data-action="open-version-history"/);
});

test('every stage has a shared confirmation footer and transition page', () => {
  assert.match(html, /id="stage-transition-view"/);
  assert.match(html, /function renderStageFooter\(\)/);
  assert.match(html, /function renderTransitionPage\(\)/);
  for (const action of ['save-stage-draft','confirm-stage-transition','cancel-stage-transition','retry-stage-transition','enter-next-stage','lock-storyboard']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  assert.ok(html.includes('确认当前阶段并进入下一步'));
  assert.ok(html.includes('确认并锁定文字分镜'));
});
```

- [ ] **Step 2: Run the test and verify it fails on missing surfaces**

Run: `node --test tests/script-creation-prototype.test.cjs --test-name-pattern="global surface"`

Expected: FAIL because `project-list-view` is absent.

- [ ] **Step 3: Implement project list, project detail, overlays, and dispatcher**

Add a hidden project-list page, one reusable overlay root, overlay renderers, toolbar buttons, and this delegated event shape:

```js
document.addEventListener('click', event => {
  const element = event.target.closest('[data-action]');
  if (!element) return;
  dispatchAction(element.dataset.action, element);
});

function dispatchAction(action, element) {
  const actions = {
    'open-project-list': () => showProjectList(),
    'open-project-detail': () => openOverlay('project-detail', { id:element.dataset.projectId }),
    'open-task-center': () => openOverlay('task-center'),
    'open-version-history': () => openOverlay('version-history', { stage:activeIndex }),
    'open-version-diff': () => openOverlay('version-diff', { stage:activeIndex }),
    'open-regenerate': () => openOverlay('regenerate', { stage:activeIndex }),
    'open-stale-impact': () => openOverlay('stale-impact', { stage:activeIndex }),
    'open-export': () => openOverlay('export', { stage:activeIndex }),
    'close-overlay': () => closeOverlay()
  };
  if (actions[action]) actions[action]();
}
```

The project list must expose search, status/type filters, sort, continue, details, copy, archive, delete, and undo feedback. The task center must show queued/running/succeeded/failed/canceled sample tasks with cancel and retry actions.

Add a shared footer after every stage render. For stages 1–7 it contains previous, save draft, and confirm/next actions plus validation reasons. For stage 8 it contains lock, export, and canvas handoff. The transition page must replace the normal workspace content while active and render queued, running, succeeded, and failed states with cancel, retry, return, and enter-next-stage actions.

- [ ] **Step 4: Run all tests and verify the global contract passes**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all tests pass.

- [ ] **Step 5: Commit global workflow surfaces**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: add script project and workflow overlays"
```

---

### Task 3: Complete creation settings and novel upload states

**Files:**
- Modify: `tests/script-creation-prototype.test.cjs`
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`

**Interfaces:**
- Consumes: `appState.config`, `appState.source`, `WorkflowModel.canEnterStage()`, overlay renderer, and dispatcher.
- Produces: `validateSettings(config)`, `startUploadDemo()`, `startParseDemo()`, `confirmSource()`, `renderSourceState()`, and `showSourceError(code)`.

- [ ] **Step 1: Add failing tests for settings and source-state coverage**

```js
test('settings include development-ready project fields', () => {
  for (const key of ['projectName','episodeDuration','language','aspectRatio','safetyLevel']) {
    assert.match(html, new RegExp(`data-model="config\\.${key}"`));
  }
  assert.match(html, /function validateSettings\(config\)/);
});

test('upload covers the full state and error inventory', () => {
  for (const state of ['EMPTY','SELECTED','UPLOADING','PARSING','PARSED','CONFIRMED','FAILED']) assert.match(html, new RegExp(`['"]${state}['"]`));
  for (const code of ['FILE_TYPE_INVALID','FILE_TOO_LARGE','FILE_CORRUPTED','CONTENT_TOO_SHORT','PARSE_FAILED']) assert.match(html, new RegExp(code));
  for (const action of ['select-demo-file','cancel-upload','retry-upload','confirm-source','replace-source','preview-chapter']) assert.match(html, new RegExp(`data-action="${action}"`));
});
```

- [ ] **Step 2: Run the new tests and verify they fail**

Run: `node --test tests/script-creation-prototype.test.cjs --test-name-pattern="settings|upload"`

Expected: FAIL on missing `episodeDuration` and `UPLOADING`.

- [ ] **Step 3: Implement settings validation and upload/parse state views**

Use required validation and explicit source rendering:

```js
function validateSettings(config) {
  const required = ['projectName','creationType','generationMode','targetType','platform','audience','episodes','episodeDuration','language','aspectRatio'];
  return required.filter(key => !String(config[key] || '').trim());
}

function confirmSource() {
  if (appState.source.state !== 'PARSED') return notify('请先完成小说解析', 'warning');
  appState.source.state = 'CONFIRMED';
  appState.stages[1].status = 'COMPLETED';
  appState.stages[2].status = 'READY';
  addVersion('source', '人工确认');
  render();
}
```

`renderSourceState()` must produce distinct content for empty, selected, uploading, parsing, parsed, confirmed, and failed states. Parsed state must include file information, chapter range controls, warning list, chapter directory, source preview, replace/delete, and confirm actions.

- [ ] **Step 4: Run all tests and verify they pass**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all tests pass.

- [ ] **Step 5: Commit settings and upload closure**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: complete script settings and source import"
```

---

### Task 4: Complete novel analysis and adaptation workbenches

**Files:**
- Modify: `tests/script-creation-prototype.test.cjs`
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`

**Interfaces:**
- Consumes: task/version APIs, `markDownstreamStale()`, existing analysis data, and character detail drawer.
- Produces: analysis edit actions, event CRUD/reorder, chapter search/fold, character list/editor, world edit actions, `validateAnalysis()`, high-pressure hook selection, strategy/rule editing, and `confirmAdaptation()`.

- [ ] **Step 1: Add failing analysis/adaptation contract tests**

```js
test('analysis exposes editable operations and partial task recovery', () => {
  for (const action of ['edit-summary','regenerate-analysis-module','add-event','edit-event','delete-event','move-event-up','search-chapter','open-all-characters','edit-character','edit-world','confirm-analysis','retry-analysis-module']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  assert.match(html, /function validateAnalysis\(analysis\)/);
});

test('adaptation includes sell points hooks strategy and custom rules', () => {
  for (const label of ['核心机制','人物卖点','情绪基调','视觉奇观','高压场景候选','整体改编重点','开场规则','机制呈现规则','角色与情绪规则','禁忌事项','更多规则']) assert.ok(html.includes(label));
  for (const action of ['select-hook','add-adaptation-rule','delete-adaptation-rule','move-adaptation-rule','confirm-adaptation']) assert.match(html, new RegExp(`data-action="${action}"`));
});
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `node --test tests/script-creation-prototype.test.cjs --test-name-pattern="analysis|adaptation"`

Expected: FAIL on missing `add-event` and `高压场景候选`.

- [ ] **Step 3: Implement editable analysis and validation**

Add edit drawers/forms and validation:

```js
function validateAnalysis(analysis) {
  const missing = [];
  if (!analysis.summary || analysis.summary.trim().length < 100) missing.push('故事梗概');
  if (!analysis.characters.some(item => item.role === '主角')) missing.push('主角');
  if (analysis.events.length < 2) missing.push('至少 2 个主要事件');
  if (!analysis.world.type) missing.push('整体世界类型');
  return missing;
}
```

Module status cards must independently show success, running, or failed state, with successful content remaining visible when another module fails.

- [ ] **Step 4: Implement the full adaptation workbench**

Render three coordinated regions: sell-point tabs, high-pressure candidate cards with episode selection, and strategy/rules editor. Confirm only when episode 1 has a hook and every required strategy field is non-empty:

```js
function confirmAdaptation() {
  const missing = [];
  if (!appState.adaptation.hooksByEpisode[1]) missing.push('第 1 集高压开场');
  for (const key of ['focus','openingRule','mechanismRule','characterRule','taboos']) if (!appState.adaptation.strategy[key].trim()) missing.push(key);
  if (missing.length) return showValidation(missing);
  appState.stages[3].status = 'COMPLETED';
  appState.stages[4].status = 'READY';
  addVersion('adaptation', '确认快照');
  render();
}
```

- [ ] **Step 5: Run all tests and verify they pass**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all tests pass.

- [ ] **Step 6: Commit analysis and adaptation workbenches**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: complete analysis and adaptation workbenches"
```

---

### Task 5: Build detailed structured-script and script-body editors

**Files:**
- Modify: `tests/script-creation-prototype.test.cjs`
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`

**Interfaces:**
- Consumes: episode data, task/version APIs, stale dependency handling, and overlay system.
- Produces: `validateEpisodeStructure(episode)`, beat CRUD/reorder, per-episode status, scene/block CRUD/reorder, association panel, temporary generation instructions, and `runScriptCheck(episode)`.

- [ ] **Step 1: Add failing editor contract tests**

```js
test('structured script exposes episode detail and beat editing', () => {
  for (const label of ['本集故事梗概','本集核心内容','结尾钩子','钩子要点','节拍时间表']) assert.ok(html.includes(label));
  for (const action of ['open-episode-structure','add-beat','delete-beat','move-beat','regenerate-episode','regenerate-beats','confirm-episode-structure']) assert.match(html, new RegExp(`data-action="${action}"`));
  assert.match(html, /function validateEpisodeStructure\(episode\)/);
});

test('script editor exposes scenes blocks references and checks', () => {
  for (const block of ['ACTION','DIALOGUE','VOICE_OVER','OFF_SCREEN','SUBTITLE','SFX','VFX','TRANSITION']) assert.ok(html.includes(block));
  for (const action of ['add-scene','copy-scene','delete-scene','move-scene','add-script-block','regenerate-scene','open-generation-instruction','run-script-check','export-script']) assert.match(html, new RegExp(`data-action="${action}"`));
  assert.match(html, /function runScriptCheck\(episode\)/);
});
```

- [ ] **Step 2: Run the editor tests and verify they fail**

Run: `node --test tests/script-creation-prototype.test.cjs --test-name-pattern="structured script|script editor"`

Expected: FAIL on missing `节拍时间表` and `add-scene`.

- [ ] **Step 3: Implement the structured-script single-episode editor**

Validate contiguous beats and target duration:

```js
function validateEpisodeStructure(episode) {
  const errors = [];
  if (!episode.summary.trim()) errors.push('本集故事梗概不能为空');
  if (!episode.coreContent.trim()) errors.push('本集核心内容不能为空');
  if (!episode.endingHook.trim()) errors.push('结尾钩子不能为空');
  episode.beats.forEach((beat, index) => {
    if (beat.end <= beat.start) errors.push(`${beat.id} 结束时间必须大于开始时间`);
    if (index && beat.start !== episode.beats[index - 1].end) errors.push(`${beat.id} 与上一节拍不连续`);
  });
  if (episode.beats.at(-1)?.end !== episode.targetDuration) errors.push('最后节拍结束时间必须等于目标时长');
  return errors;
}
```

Render per-episode generation statuses and keep incomplete episodes locked independently.

- [ ] **Step 4: Implement structured scenes and content blocks**

Render episode/scene navigation, editable scene metadata, block list, `/`-style block menu, right association tabs, and script-check result drawer. `runScriptCheck()` must return located error/warning/suggestion records containing `episodeNo`, `sceneId`, `blockId`, `severity`, `title`, and `message`.

- [ ] **Step 5: Run all tests and verify they pass**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all tests pass.

- [ ] **Step 6: Commit the structure and body editors**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: add structured script and scene editors"
```

---

### Task 6: Complete review, storyboard, continuity, and delivery

**Files:**
- Modify: `tests/script-creation-prototype.test.cjs`
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`

**Interfaces:**
- Consumes: script-check results, episode versions, asset references, overlay/export system, and task state.
- Produces: review filters/details/revisions, `canApproveEpisode(issues, episodeNo)`, full storyboard editor, shot CRUD/split/merge/reorder, `runContinuityCheck(shots)`, export configuration/result, archive confirmation, and canvas handoff summary.

- [ ] **Step 1: Add failing review/storyboard contract tests**

```js
test('review supports issue location revision comparison and approval', () => {
  for (const action of ['filter-review-issues','open-review-issue','locate-review-issue','accept-review-suggestion','save-review-revision','ignore-review-issue','batch-review-action','approve-episode','open-version-diff']) assert.match(html, new RegExp(`data-action="${action}"`));
  assert.match(html, /function canApproveEpisode\(issues, episodeNo\)/);
});

test('storyboard contains the complete field and operation inventory', () => {
  for (const field of ['shot_size','camera_angle','camera_movement','composition','visual_description','characters','dialogue_vo','emotion','sfx_music','vfx','asset_refs','generation_prompt','negative_prompt','continuity_note']) assert.ok(html.includes(field));
  for (const action of ['add-shot','copy-shot','delete-shot','move-shot','split-shot','merge-shot','toggle-storyboard-view','batch-edit-shots','run-continuity-check','open-export','handoff-canvas']) assert.match(html, new RegExp(`data-action="${action}"`));
  assert.match(html, /function runContinuityCheck\(shots\)/);
});
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `node --test tests/script-creation-prototype.test.cjs --test-name-pattern="review|storyboard"`

Expected: FAIL on missing `open-review-issue` and `negative_prompt`.

- [ ] **Step 3: Implement review issue workflow**

Add issue filters, detail panel, original/suggestion/revised comparison, and approval guard:

```js
function canApproveEpisode(issues, episodeNo) {
  const blockers = issues.filter(issue => issue.episodeNo === episodeNo && issue.severity === 'ERROR' && issue.status !== 'RESOLVED');
  return { allowed:blockers.length === 0, blockers };
}
```

Accepting or saving a revision resolves the issue, adds a new script version, and marks the episode storyboard `READY`. Ignoring is available only for warning/suggestion issues.

- [ ] **Step 4: Implement the complete storyboard editor and checks**

Render directory, table/card toggle, detail editing panel, asset/source tabs, and all shot actions. Split halves duration and clones references; merge is permitted only for adjacent shots. `runContinuityCheck()` must detect duration outside 0.5–15 seconds, total-duration mismatch, missing prompt categories, repeated shot size, and asset continuity changes.

- [ ] **Step 5: Implement export and post-stage delivery feedback**

The export overlay must choose episode range, version, and XLSX/JSON/Markdown, then show queued/running/succeeded states and the exact filename preview. Archive requires confirmation and makes the project read-only. Canvas handoff shows locked storyboard, character, world, and asset versions before confirmation.

- [ ] **Step 6: Run all tests and verify they pass**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all tests pass.

- [ ] **Step 7: Commit review, storyboard, and delivery**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: complete review storyboard and delivery flow"
```

---

### Task 7: Upgrade the script-creation PRD to the implemented eight-stage module

**Files:**
- Modify: `tests/script-creation-prototype.test.cjs`
- Modify: `漫剧视频创作平台_PRD.md`

**Interfaces:**
- Consumes: final implemented page/action/state inventory from Tasks 1–6.
- Produces: one authoritative eight-stage module PRD whose names, state rules, fields, acceptance criteria, priorities, tracking, and test coverage match the prototype.

- [ ] **Step 1: Add failing PRD contract tests**

```js
const prd = fs.readFileSync('漫剧视频创作平台_PRD.md', 'utf8');

test('PRD defines the authoritative eight-stage workflow', () => {
  assert.ok(prd.includes('八阶段主流程'));
  for (const stage of ['创作设置','小说上传','小说分析','改编方案','结构化文字剧本','剧本正文','审核修订','文字分镜']) assert.ok(prd.includes(stage));
  assert.doesNotMatch(prd, /六步主流程/);
});

test('PRD covers every shared workflow capability', () => {
  for (const section of ['项目详情','任务中心','阶段准入','自动保存','历史版本','版本差异','重新生成','下游影响','空、加载与错误状态','导出配置']) assert.ok(prd.includes(section));
});

test('PRD acceptance covers stage-specific editors', () => {
  for (const item of ['高压场景候选','节拍时间校验','结构化内容块','审核问题定位','镜头拆分','连续性检查','XLSX','JSON','Markdown']) assert.ok(prd.includes(item));
});
```

- [ ] **Step 2: Run PRD tests and verify they fail**

Run: `node --test tests/script-creation-prototype.test.cjs --test-name-pattern="PRD"`

Expected: FAIL because the current document still says `六步主流程`.

- [ ] **Step 3: Update document metadata, scope, and eight-stage global flow**

Set the document version to V2.0, document status to static-prototype confirmed / development review, add the change summary, replace the six-step flow with the approved eight-stage flow, and update completion metrics from six-step to eight-stage terminology.

- [ ] **Step 4: Add creation-settings and review-revision requirement chapters**

Creation settings must document fields, mode branches, validation, defaults, downstream influence, and acceptance. Review revision must document issue model, severity, filters, location anchors, revision methods, approval guard, version creation, audit record, and acceptance.

- [ ] **Step 5: Synchronize all existing stage chapters with final prototype behavior**

For each stage include: purpose, page structure, fields, editable operations, generation/task behavior, validation, dependency impact, error states, and acceptance criteria. Update storyboard delivery to describe export/archive/canvas actions outside the stage count.

- [ ] **Step 6: Update data model, API mapping, priority, analytics, tests, traceability, and launch checklist**

Add project configuration, stage status, review issue, export task, and handoff summary entities. Extend API recommendations for project detail/list, task cancel/retry, version diff, impact preview, review issues, export status, and archive. Add matching events and P0/P1 split.

- [ ] **Step 7: Run all tests and verify they pass**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all prototype and PRD tests pass.

- [ ] **Step 8: Commit the PRD upgrade**

```bash
git add tests/script-creation-prototype.test.cjs 漫剧视频创作平台_PRD.md
git commit -m "docs: upgrade script creation module PRD to eight stages"
```

---

### Task 8: Run full browser acceptance and produce evidence

**Files:**
- Create: `artifacts/script-creation-completion-2026-08-05/01-home-projects.png`
- Create: `artifacts/script-creation-completion-2026-08-05/02-settings-validation.png`
- Create: `artifacts/script-creation-completion-2026-08-05/03-upload-parsed.png`
- Create: `artifacts/script-creation-completion-2026-08-05/04-analysis-edit.png`
- Create: `artifacts/script-creation-completion-2026-08-05/05-adaptation-hooks.png`
- Create: `artifacts/script-creation-completion-2026-08-05/06-structure-editor.png`
- Create: `artifacts/script-creation-completion-2026-08-05/07-script-check.png`
- Create: `artifacts/script-creation-completion-2026-08-05/08-review-revision.png`
- Create: `artifacts/script-creation-completion-2026-08-05/09-storyboard-export.png`
- Create: `artifacts/script-creation-completion-2026-08-05/10-task-version-stale.png`
- Create: `artifacts/script-creation-completion-2026-08-05/11-stage-transition.png`
- Modify: `design-qa.md`

**Interfaces:**
- Consumes: final prototype URL, implemented actions, automated tests, and accepted design screenshots.
- Produces: fresh browser evidence, interaction audit notes, console verification, and final deliverable tab.

- [ ] **Step 1: Run automated tests and syntax checks**

Run:

```bash
node --test tests/script-creation-prototype.test.cjs
node -e 'const fs=require("fs");const s=fs.readFileSync(".superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html","utf8");new Function(s.match(/<script>([\s\S]*?)<\/script>/)[1]);console.log("syntax pass")'
git diff --check
```

Expected: all tests pass, JavaScript syntax passes, and `git diff --check` has no output.

- [ ] **Step 2: Verify the complete happy path in the in-app browser**

At 1280 × 720, exercise professional creation from settings through export. Confirm stages 1–7 one by one, verify that each confirmation opens the transition-progress page, wait for success, and enter the next stage. Before every action take a fresh DOM snapshot; after every action verify the visible state changed to the intended page, panel, task, validation, or result.

- [ ] **Step 3: Verify global and failure paths**

Exercise project detail/list, invalid settings, upload failure/retry, partial analysis failure, locked stage, save failure, task cancel/retry, version diff/restore, stale impact choices, review blocker, storyboard continuity error, export failure/retry, archive read-only state, transition failure/cancel/retry, and stage-8 lock/export/canvas actions.

- [ ] **Step 4: Capture and inspect eleven accepted screenshots**

Save the exact browser screenshots to the listed artifact paths. Inspect every saved image and reject any blank, loading, cropped, wrong-state, or wrong-window capture.

- [ ] **Step 5: Update design QA with implementation evidence and limits**

Document viewport, captured states, test command/result, console issue count, known static-prototype limitations, and any remaining P2/P3 finding. Do not claim backend, persistence, download, collaboration, or accessibility compliance.

- [ ] **Step 6: Finalize the browser deliverable and commit QA evidence metadata**

```bash
git add design-qa.md
git commit -m "docs: verify completed script creation prototype"
```

Keep the final browser tab on the default home or creation-settings entry with deliverable status.
