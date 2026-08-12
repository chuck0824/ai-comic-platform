# Script Workbench Top Actions Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the six-button toolbar from every stage and preserve each capability through contextual, conditional entry points.

**Architecture:** Keep the single-file static prototype and existing overlay handlers. Replace the global stage toolbar with breadcrumb, stage version, and one “更多” menu; render regeneration and stale-impact actions inside stage content according to stage state. Update the PRD to match without changing workflow state or backend contracts.

**Tech Stack:** Static HTML/CSS/JavaScript, Node.js built-in test runner, Markdown.

## Global Constraints

- Preserve the existing eight-stage order and transition progress workflow.
- Do not remove task, version, regeneration, dependency-impact, settings, or home navigation capabilities.
- At 1280px width, project title, status, version, and “更多” must remain readable without the old multi-line toolbar.
- Do not introduce new dependencies.

---

### Task 1: Protect the simplified header contract

**Files:**
- Modify: `tests/script-creation-prototype.test.cjs`
- Test: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Consumes: rendered prototype HTML.
- Produces: regression checks for `workspace-breadcrumb`, `stage-version-action`, `workspace-more-action`, conditional regeneration and stale-impact surfaces.

- [ ] **Step 1: Write the failing tests**

Add this test:

```js
test('workbench uses contextual actions instead of the six-button header toolbar', () => {
  const header = html.match(/<header class="project-head">([\s\S]*?)<\/header>/)?.[1] || '';
  assert.doesNotMatch(header, /class="project-actions"/);
  for (const id of ['workspace-breadcrumb','stage-version-action','workspace-more-action','workspace-more-menu']) {
    assert.match(html, new RegExp(`id="${id}"`));
  }
  for (const action of ['open-task-center','open-version-history','open-regenerate','open-stale-impact','open-settings-menu','back-home']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  assert.match(html, /function renderContextualStageActions\(\)/);
});
```

- [ ] **Step 2: Run the test to verify RED**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL because the old toolbar still exists and the new header controls do not.

- [ ] **Step 3: Commit the failing contract test with the implementation in Task 2**

Do not commit a permanently red tree; stage this test together with Task 2 after GREEN.

### Task 2: Implement contextual workbench actions

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Test: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Consumes: existing `openOverlay`, `dispatchAction`, `appState.stages`, `activeIndex`, and `showHome`.
- Produces: `workspace-breadcrumb`, `stage-version-action`, `workspace-more-action`, `workspace-more-menu`, `renderContextualStageActions()`.

- [ ] **Step 1: Replace the header toolbar**

Replace the header actions with nodes using these stable IDs: `workspace-breadcrumb`, `stage-version-action`, `workspace-more-action`, and `workspace-more-menu`. The menu items use `data-action="open-settings-menu"`, `data-action="open-project-detail"`, and `data-action="archive-project"`.

- [ ] **Step 2: Add contextual stage actions**

Implement and append this function from `renderContent()`:

```js
function renderContextualStageActions() {
  const hasOutput = activeIndex > 0 || appState.stages[activeIndex].status !== 'READY';
  const hasStaleDownstream = appState.stages.slice(activeIndex + 1).some(stage => stage.status === 'STALE');
  return `<div class="contextual-stage-actions">
    ${hasOutput ? '<button class="btn secondary" data-action="open-regenerate">重新生成当前产物</button>' : ''}
    ${hasStaleDownstream ? '<button class="btn warn" data-action="open-stale-impact">查看依赖影响</button>' : ''}
  </div>`;
}
```

- [ ] **Step 3: Bind menu and breadcrumb behavior**

Add `open-settings-menu` to `dispatchAction`, mapping it to `openSettings()`. Add a `toggleWorkspaceMoreMenu()` function that switches `aria-expanded` and `.open`; close it on document click outside the menu and on `Escape`, then focus `#workspace-more-action`.

- [ ] **Step 4: Run tests and syntax check**

Run: `node --test tests/script-creation-prototype.test.cjs`

Run: `node -e "const fs=require('fs'),vm=require('vm');const h=fs.readFileSync('.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html','utf8');new vm.Script(h.match(/<script>([\\s\\S]*?)<\\/script>/)[1]);"`

Expected: all tests pass and syntax command exits 0.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "fix: simplify script workbench actions"
```

### Task 3: Synchronize PRD and visual verification

**Files:**
- Modify: `漫剧视频创作平台_PRD.md`
- Modify: `design-qa.md`
- Create: `artifacts/script-creation-completion-2026-08-06/13-simplified-workbench-header.png`

**Interfaces:**
- Consumes: implemented header and contextual actions.
- Produces: authoritative entry-location rules and accepted viewport evidence.

- [ ] **Step 1: Update PRD**

Add a subsection titled `5.1.2 工作台功能入口分层` with a table mapping the six old actions to their new locations and condition rules exactly as specified in the design document.

- [ ] **Step 2: Verify in the in-app browser**

Reload `http://localhost:62096/`, enter professional creation, inspect the 1280px layout, open/close “更多”, open version history, and verify the bottom transition button remains functional.

- [ ] **Step 3: Save and inspect screenshot**

Save the accepted viewport capture as `13-simplified-workbench-header.png` and inspect it for wrapping, clipping, alignment, focus, and content hierarchy.

- [ ] **Step 4: Run final verification**

Run tests, HTML script syntax check, and `git diff --check`.

- [ ] **Step 5: Commit**

```bash
git add 漫剧视频创作平台_PRD.md design-qa.md artifacts/script-creation-completion-2026-08-06/13-simplified-workbench-header.png
git commit -m "docs: align PRD with contextual workbench actions"
```
