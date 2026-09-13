# Stage Transition Percentage Loader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every inter-stage transition screen with a percentage loader that automatically enters the next stage after successful completion.

**Architecture:** Extend the existing pure `WorkflowModel` with status-to-percentage progression, then render one accessible progress surface from `renderTransitionPage()`. Keep task status authoritative: the browser animation may advance only within the status range, `SUCCEEDED` sets 100%, and a separate 500ms timer performs automatic navigation.

**Tech Stack:** Static HTML/CSS/JavaScript, Node.js built-in test runner, Markdown.

## Global Constraints

- All seven inter-stage transitions use one percentage loader.
- `SUCCEEDED` is the only state allowed to produce `100%`.
- At `100%`, wait 500ms and automatically enter the next stage.
- Failure stops progress and offers retry or return; confirmed artifacts remain intact.
- Do not alter eight-stage ordering, version history, task center, or downstream stale logic.
- Do not add dependencies.

---

### Task 1: Protect percentage progression behavior

**Files:**
- Modify: `tests/script-creation-prototype.test.cjs`
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`

**Interfaces:**
- Consumes: `WorkflowModel` task states.
- Produces: `advanceTransitionProgress(status: string, current: number): number`.

- [ ] **Step 1: Write the failing behavior test**

```js
test('transition percentage respects task-state boundaries', () => {
  const model = loadModel();
  assert.equal(model.advanceTransitionProgress('QUEUED', 0), 4);
  assert.equal(model.advanceTransitionProgress('QUEUED', 20), 20);
  assert.equal(model.advanceTransitionProgress('RUNNING', 20), 27);
  assert.equal(model.advanceTransitionProgress('RUNNING', 98), 99);
  assert.equal(model.advanceTransitionProgress('FAILED', 62), 62);
  assert.equal(model.advanceTransitionProgress('SUCCEEDED', 62), 100);
});
```

- [ ] **Step 2: Run RED**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL because `advanceTransitionProgress` is not defined.

- [ ] **Step 3: Implement the pure progression function**

```js
function advanceTransitionProgress(status, current) {
  if (status === 'SUCCEEDED') return 100;
  if (status === 'FAILED' || status === 'CANCELED') return current;
  if (status === 'QUEUED') return Math.min(20, current + 4);
  if (status === 'RUNNING') return Math.min(99, current + 7);
  return current;
}
```

Export it from `WorkflowModel` and initialize `transition.progress = 0` in `startStageTransition`.

- [ ] **Step 4: Run GREEN**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all tests pass.

### Task 2: Replace the transition page and automate entry

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Consumes: `transition.progress`, `advanceTransitionProgress`, `enterTransitionTarget()`.
- Produces: accessible `role="progressbar"` UI and automatic successful navigation.

- [ ] **Step 1: Write the failing UI contract test**

```js
test('transition page is a single percentage loader', () => {
  assert.match(html, /role="progressbar"/);
  assert.match(html, /aria-valuenow="\$\{transition\.progress\}"/);
  assert.match(html, /transition-percentage/);
  assert.doesNotMatch(html, /transition-route/);
  assert.doesNotMatch(html, /transition-steps/);
  assert.doesNotMatch(html, /simulate-transition-failure/);
  assert.doesNotMatch(html, /enter-next-stage/);
  assert.match(html, /setTimeout\(enterTransitionTarget, 500\)/);
});
```

- [ ] **Step 2: Run RED**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL because the old route, steps, manual-enter action, and failure-demo action still exist.

- [ ] **Step 3: Implement loader rendering and timers**

Render only next-stage label, percentage, progressbar, status copy, and context-sensitive controls. Add these timer boundaries:

```js
let transitionProgressTimer = null;
function startTransitionProgress() {
  clearInterval(transitionProgressTimer);
  transitionProgressTimer = setInterval(() => {
    const transition = appState.transition;
    const next = WorkflowModel.advanceTransitionProgress(transition.status, transition.progress);
    if (next !== transition.progress) {
      transition.progress = next;
      render();
    }
  }, 120);
}
function clearTransitionProgress() {
  clearInterval(transitionProgressTimer);
  transitionProgressTimer = null;
}
```

Call `clearTransitionProgress()` on cancel, failure, success, and navigation. After `completeStageTransition`, assign `transition.progress = 100`, render, and call `setTimeout(enterTransitionTarget, 500)`.

- [ ] **Step 4: Run GREEN and syntax check**

Run: `node --test tests/script-creation-prototype.test.cjs`

Run: `node -e "const fs=require('fs'),vm=require('vm');const h=fs.readFileSync('.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html','utf8');new vm.Script(h.match(/<script>([\\s\\S]*?)<\\/script>/)[1]);"`

Expected: all tests pass and syntax command exits 0.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: simplify stage transitions to percentage loader"
```

### Task 3: Synchronize PRD and verify visually

**Files:**
- Modify: `漫剧视频创作平台_PRD.md`
- Modify: `design-qa.md`
- Create: `artifacts/script-creation-completion-2026-08-06/14-transition-percentage-loader.png`

**Interfaces:**
- Consumes: implemented percentage loader.
- Produces: authoritative PRD rules and accepted visual evidence.

- [ ] **Step 1: Update PRD**

Replace three-stage transition copy with the exact percentage mapping, 99% cap, 500ms auto-entry, cancellation, failure, and retry rules from the approved design.

- [ ] **Step 2: Browser verification**

Open professional creation, confirm stage 1, observe a percentage between 1 and 99, observe 100%, and verify automatic arrival at stage 2 without clicking a second button. Repeat through stage 8 or exercise the shared renderer for all seven transitions.

- [ ] **Step 3: Save and inspect screenshot**

Capture the running percentage state to `14-transition-percentage-loader.png` and inspect centering, number stability, progressbar clarity, clipping, and control hierarchy.

- [ ] **Step 4: Run final verification and commit**

Run tests, HTML syntax check, `git diff --check`, then commit PRD, QA notes, and screenshot with message `docs: align PRD with percentage transition loader`.
