# 剧本创作工作台交互、Obsidian 文档与模型积分实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐八阶段静态原型全部核心交互，并让模型选择、积分消耗、变更影响和 Obsidian Markdown 产物形成可验证闭环。

**Architecture:** 保留当前单 HTML 原型和事件委托结构，在 `WORKFLOW_MODEL` 中新增纯函数与结构化状态，使 Node 测试可以直接验证字数限制、文档生成、依赖标记、模型回退和积分记录。页面通过统一 overlay 呈现编辑、差异、模型和导出操作；3001 模型使用 `/api/pricing` 读取，空列表或失败时切换内置演示模型，正式认证接口仅在 PRD 中定义后端代理契约。

**Tech Stack:** 原生 HTML/CSS/JavaScript、Node.js `node:test`、静态 Python 预览服务器、New API（localhost:3001）、Markdown/Obsidian YAML Frontmatter

## Global Constraints

- 小说粘贴文本最多 2000 个 Unicode 汉字，必须显示汉字数与总字符数。
- 所有用户列出的核心按钮必须产生可见、可恢复的状态变化，不能仅弹无后续结果的提示。
- 每次人工保存或 AI 生成创建新版本，不覆盖旧版本。
- 上游修改必须精确记录受影响文档，并在用户确认前保留旧版。
- 模型优先读取 3001；无数据或不可用时使用明确标记的内置演示模型。
- 用户侧统一使用积分；`1 quota = 1 积分`，不做货币换算。
- 演示模型消耗固定为 0 积分，不调用真实结算。
- 不在静态页面或 Markdown 中保存 3001 的 API Key、Cookie 或管理员凭据。

---

## File Structure

- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html` — 页面、状态、交互、模型读取、积分展示、Markdown 预览与下载。
- Modify: `tests/script-creation-prototype.test.cjs` — 纯函数、动作绑定、文档结构和 PRD 契约测试。
- Modify: `漫剧视频创作平台_PRD.md` — Obsidian 文档服务、依赖图、模型代理和积分接口正式需求。
- Create: `artifacts/script-creation-completion-2026-08-06/15-*.png` — 关键完成态浏览器截图。

### Task 1: 可测试的工作流状态、文本限制与文档核心

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Produces: `WorkflowModel.countHan(text): number`
- Produces: `WorkflowModel.limitHanText(text, maxHan): { text, hanCount, totalCount, truncated }`
- Produces: `WorkflowModel.createArtifact(state, spec): Artifact`
- Produces: `WorkflowModel.updateArtifact(state, artifactId, patch): Artifact`
- Produces: `WorkflowModel.markArtifactImpacts(state, artifactId, changedFields): Impact[]`
- Produces: `WorkflowModel.renderMarkdown(state, artifactId): string`
- Produces: `WorkflowModel.recordPoints(state, entry): BillingEntry`

- [ ] **Step 1: Write failing pure-function tests**

```js
test('limits pasted novel text to 2000 Chinese characters', () => {
  const model = loadModel();
  const result = model.limitHanText('序'.repeat(2001) + ' END', 2000);
  assert.equal(result.hanCount, 2000);
  assert.equal(result.truncated, true);
  assert.equal(model.countHan(result.text), 2000);
});

test('artifact changes mark linked downstream markdown stale', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const person = model.createArtifact(state, { id:'CHAR-001', type:'character', stage:2, title:'林野', data:{ name:'林野' } });
  model.createArtifact(state, { id:'SCRIPT-001', type:'script', stage:5, title:'第1集正文', dependsOn:['CHAR-001'], data:{} });
  const impacts = model.markArtifactImpacts(state, person.id, ['name']);
  assert.deepEqual(Array.from(impacts, item => item.artifactId), ['SCRIPT-001']);
  assert.equal(state.artifacts['SCRIPT-001'].stale, true);
});

test('markdown contains Obsidian frontmatter and links', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'SUMMARY-001', type:'summary', stage:2, title:'故事梗概', dependsOn:['SOURCE-001'], data:{ summary:'测试梗概' } });
  const md = model.renderMarkdown(state, 'SUMMARY-001');
  assert.match(md, /^---/);
  assert.match(md, /artifact_id: SUMMARY-001/);
  assert.match(md, /\[\[SOURCE-001\]\]/);
  assert.match(md, /# 故事梗概/);
});
```

- [ ] **Step 2: Run tests and verify failure**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL because `limitHanText`, `createArtifact`, `markArtifactImpacts`, and `renderMarkdown` do not exist.

- [ ] **Step 3: Implement minimal workflow model**

Extend initial state with:

```js
artifacts: {},
artifactOrder: [],
impacts: [],
billingEntries: [],
models: { source:'fallback', loading:false, error:null, items:[], selectedId:null },
editor: { selectedBlockId:null, storyboardView:'cards', reviewFilters:{ severity:'全部', status:'全部' } }
```

Implement deterministic artifact versioning, Obsidian frontmatter rendering, Unicode Han counting with `/\p{Script=Han}/u`, transitive downstream impact traversal, and point-entry recording.

- [ ] **Step 4: Run tests and verify pass**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all Task 1 tests PASS and existing 14 tests remain green.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: add script artifact and markdown workflow model"
```

### Task 2: 小说上传限制与项目/阶段模型选择

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Consumes: `limitHanText`, `createArtifact`, `renderMarkdown`
- Produces: `WorkflowModel.normalizePricingModels(payload): ModelOption[]`
- Produces: `WorkflowModel.resolveModels(remoteModels, fallbackModels): { source, items }`
- Produces UI actions: `refresh-models`, `select-project-model`, `preview-source-markdown`

- [ ] **Step 1: Write failing tests for model fallback and controls**

```js
test('empty pricing response falls back to demo models', () => {
  const model = loadModel();
  const result = model.resolveModels([], [{ id:'demo-script-pro', demo:true, points:0 }]);
  assert.equal(result.source, 'fallback');
  assert.equal(result.items[0].demo, true);
  assert.equal(result.items[0].points, 0);
});

test('upload and model controls are present', () => {
  assert.match(html, /data-source-text/);
  assert.match(html, /汉字.*\/2000/);
  for (const action of ['refresh-models','select-project-model','preview-source-markdown']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});
```

- [ ] **Step 2: Run tests and verify failure**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL on missing model functions and controls.

- [ ] **Step 3: Implement upload editor and model selector**

Add a controlled source textarea with live `汉字 N/2000 · 总字符 M`, paste truncation, empty validation, and Markdown preview. Add project default model to 创作设置 and a compact stage model selector to the current-stage context. Fetch `http://localhost:3001/api/pricing`; normalize `data`, `vendors`, `supported_endpoint`, and `group_ratio`; if empty or failed, show three demo text models with `0 积分（演示）` and a clear fallback badge.

- [ ] **Step 4: Verify tests and browser state**

Run: `node --test tests/script-creation-prototype.test.cjs`

Browser checks: paste 2001 Han characters, confirm counter stays at 2000; reload with current 3001 empty data and verify fallback badge plus selected demo model.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: add novel text limit and model fallback"
```

### Task 3: 小说分析编辑与 Obsidian 文档联动

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Consumes: `createArtifact`, `updateArtifact`, `markArtifactImpacts`, `renderMarkdown`
- Produces overlay types: `summary-editor`, `event-editor`, `character-library`, `character-editor`, `world-editor`, `markdown-preview`, `impact-review`
- Produces actions: `save-summary`, `save-event`, `save-character`, `save-world`, `apply-impact-choice`

- [ ] **Step 1: Write failing action and document tests**

```js
test('analysis editors have save actions and markdown targets', () => {
  for (const action of ['save-summary','save-event','save-character','save-world','open-markdown-preview']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  for (const file of ['故事梗概.md','主要事件.md','章节大纲.md','世界观.md','00-人物索引.md']) assert.ok(html.includes(file));
});
```

- [ ] **Step 2: Run test and verify failure**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL because editor surfaces and save actions are absent.

- [ ] **Step 3: Implement analysis editors**

Replace feedback-only handlers with actual overlays and structured form state. Saving creates or updates `SUMMARY-001`, `EVENT-NNN`, `CHAR-NNN`, and `WORLD-001`, increments versions, opens the downstream impact review, and makes the generated Markdown available for preview/download.

- [ ] **Step 4: Verify tests and browser interactions**

Run: `node --test tests/script-creation-prototype.test.cjs`

Browser checks: edit summary and confirm visible text changes; add event and see list count increase; edit a character profile; edit each world tab; verify impact review and Markdown preview.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: make novel analysis editors persist artifacts"
```

### Task 4: 改编方案与结构化文字剧本

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Produces actions: `choose-hook`, `save-adaptation-rule`, `save-adaptation`, `save-episode-structure`, `save-beat`, `confirm-beat-regeneration`
- Produces artifacts: `ADAPT-001`, `EP-001-STRUCTURE`

- [ ] **Step 1: Write failing tests**

```js
test('adaptation and structure actions persist instead of only notifying', () => {
  for (const action of ['choose-hook','save-adaptation-rule','save-adaptation','save-episode-structure','save-beat','confirm-beat-regeneration']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});
```

- [ ] **Step 2: Run test and verify failure**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL on missing actions.

- [ ] **Step 3: Implement real selection and editors**

Make high-pressure openings selectable cards; store selected hook and reason. Add editable adaptation-rule rows. Block confirmation until a hook and rule exist. Add a single-episode structure overlay with editable goal/conflict/turn/hook and an ordered beat list. Regenerate beats through the shared model/points confirmation surface and preserve the old version.

- [ ] **Step 4: Verify tests and browser interactions**

Run: `node --test tests/script-creation-prototype.test.cjs`

Browser checks: select hook; add rule; confirm plan; open episode; add beat; regenerate; confirm artifact version and points entry change.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: complete adaptation and episode structure interactions"
```

### Task 5: 剧本正文编辑、AI 操作、检查与导出

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Produces actions: `select-script-block`, `ai-continue`, `ai-conflict`, `ai-condense-dialogue`, `ai-rewrite-tone`, `ai-character-check`, `save-scene`, `save-script-block`, `open-script-check`, `open-script-export`, `accept-ai-diff`
- Produces artifact: `EP-001-SCRIPT`

- [ ] **Step 1: Write failing button-binding test**

```js
test('all script AI tools and editor controls are actionable', () => {
  for (const action of ['ai-continue','ai-conflict','ai-condense-dialogue','ai-rewrite-tone','ai-character-check','save-scene','save-script-block','open-script-check','open-script-export','accept-ai-diff']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});
```

- [ ] **Step 2: Run test and verify failure**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL because AI tool buttons have no `data-action` and editor surfaces are absent.

- [ ] **Step 3: Implement block selection and real actions**

Store scenes and typed blocks in state. AI buttons remain disabled until a block is selected; clicking an enabled AI action opens model/points confirmation, then a before/after diff. Accepting writes a new script version and marks review/storyboard stale. Add scene/block forms, a locatable check result surface, and Markdown/DOCX/JSON export configuration (static demo creates downloadable Markdown/JSON only and labels DOCX as production service capability).

- [ ] **Step 4: Verify tests and browser interactions**

Run: `node --test tests/script-creation-prototype.test.cjs`

Browser checks: tools disabled before selection, enabled after selection; accept a rewrite; add scene and block; run check; download Markdown and verify content order.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: complete script body editing and AI tools"
```

### Task 6: 审核修订与文字分镜操作闭环

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Produces actions: `apply-review-filter`, `resolve-review-issue`, `save-review-revision`, `approve-episode`, `save-shot`, `confirm-split-shot`, `confirm-merge-shot`, `undo-storyboard`, `archive-project-complete`, `open-export`, `create-canvas-project`
- Produces artifacts: `EP-001-REVIEW`, `EP-001-STORYBOARD`

- [ ] **Step 1: Write failing workflow tests**

```js
test('review approval is blocked by open high issues', () => {
  const model = loadModel();
  assert.equal(model.canApproveReview([{ severity:'HIGH', status:'OPEN' }]).allowed, false);
  assert.equal(model.canApproveReview([{ severity:'HIGH', status:'RESOLVED' }]).allowed, true);
});

test('storyboard delivery buttons are bound', () => {
  for (const action of ['archive-project-complete','open-export','create-canvas-project','undo-storyboard']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});
```

- [ ] **Step 2: Run tests and verify failure**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL on `canApproveReview` and missing delivery actions.

- [ ] **Step 3: Implement review and storyboard state changes**

Implement severity/type/status filters, issue locate/resolve, local revision versioning, invalidation of old review, and approval gating. Make shot add/split/merge update structured rows with one-step undo. Persist card/table view. Generate continuity issues. Bind archive, export, and canvas handoff buttons to confirmation/summary overlays and artifacts.

- [ ] **Step 4: Verify tests and browser interactions**

Run: `node --test tests/script-creation-prototype.test.cjs`

Browser checks: filter and resolve HIGH issue; approve only after resolution; add/split/merge/undo shot; switch views; run continuity check; archive and create canvas handoff summary.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: complete review and storyboard workflows"
```

### Task 7: 统一重新生成、积分日志和 Obsidian 项目导出

**Files:**
- Modify: `.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html`
- Modify: `tests/script-creation-prototype.test.cjs`

**Interfaces:**
- Consumes: selected model, `recordPoints`, `renderMarkdown`, artifact dependencies
- Produces: `WorkflowModel.estimatePoints(model, usage): number`
- Produces: `WorkflowModel.buildVaultFiles(state): Record<string,string>`
- Produces actions: `confirm-regenerate`, `open-billing-log`, `preview-vault-index`, `download-vault-markdown`

- [ ] **Step 1: Write failing billing and vault tests**

```js
test('demo models always estimate zero points', () => {
  const model = loadModel();
  assert.equal(model.estimatePoints({ demo:true }, { inputTokens:5000, outputTokens:1000 }), 0);
});

test('vault export contains required Obsidian paths', () => {
  const model = loadModel();
  const files = model.buildVaultFiles(model.createInitialState());
  for (const path of ['00-项目主页.md','01-创作设置.md','03-小说分析/故事梗概.md','90-变更影响.md','99-生成与计费记录.md']) assert.ok(path in files);
});
```

- [ ] **Step 2: Run tests and verify failure**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL on missing points estimator and vault builder.

- [ ] **Step 3: Implement shared generation and export surfaces**

Upgrade the existing regenerate overlay to include model source, model selector, estimated Token, estimated points, available points state, and fallback warning. A completed demo task records a zero-point entry; a real-model mock records estimate/preconsume/actual/refund fields without changing 3001 state. Build all defined vault paths from current artifacts and provide an index preview plus per-file Markdown downloads; ZIP packaging remains a production backend requirement.

- [ ] **Step 4: Verify tests and browser interactions**

Run: `node --test tests/script-creation-prototype.test.cjs`

Browser checks: open regenerate from stages 3–8; confirm selected model and zero points fallback; inspect billing log; preview project homepage, impact log, and billing Markdown.

- [ ] **Step 5: Commit**

```bash
git add tests/script-creation-prototype.test.cjs .superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html
git commit -m "feat: add shared generation points and Obsidian export"
```

### Task 8: PRD、回归测试和浏览器验收

**Files:**
- Modify: `漫剧视频创作平台_PRD.md`
- Modify: `tests/script-creation-prototype.test.cjs`
- Create: `artifacts/script-creation-completion-2026-08-06/15-source-model.png`
- Create: `artifacts/script-creation-completion-2026-08-06/16-analysis-edit-impact.png`
- Create: `artifacts/script-creation-completion-2026-08-06/17-regenerate-points.png`
- Create: `artifacts/script-creation-completion-2026-08-06/18-vault-preview.png`

**Interfaces:**
- Documents formal endpoints: `GET /api/v1/ai/models`, `POST /api/v1/generation/estimate`, `POST /api/v1/generation/tasks`, `GET /api/v1/projects/{id}/vault`, `PUT /api/v1/projects/{id}/artifacts/{artifactId}`

- [ ] **Step 1: Write failing PRD coverage test**

```js
test('PRD defines Obsidian artifacts, model fallback and point billing', () => {
  for (const term of ['Obsidian','00-项目主页.md','90-变更影响.md','99-生成与计费记录.md','1 quota = 1 积分','演示模型','预计积分','预扣积分','实际积分','返还积分']) {
    assert.ok(prd.includes(term), `missing PRD term: ${term}`);
  }
});
```

- [ ] **Step 2: Run test and verify failure**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: FAIL because the PRD lacks the new formal contracts.

- [ ] **Step 3: Update PRD**

Add an Obsidian document-management chapter, dependency/impact matrix, model-source priority, project/stage/task selection rules, native-quota-to-point rule, estimate/preconsume/settle/refund states, security boundary, endpoint contracts, and acceptance cases matching the approved spec.

- [ ] **Step 4: Run full automated verification**

Run: `node --test tests/script-creation-prototype.test.cjs`

Expected: all tests PASS with zero failures.

- [ ] **Step 5: Run browser workflow verification**

Use the in-app browser at `http://localhost:62096/?version=20260806-interactions` and verify every user-reported button across stages 2–8. Capture the four named screenshots after confirming visible state changes, Markdown updates, model fallback, point estimate, and stage transitions.

- [ ] **Step 6: Check repository state**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; only intended PRD, prototype, test, and screenshot changes.

- [ ] **Step 7: Commit**

```bash
git add 漫剧视频创作平台_PRD.md tests/script-creation-prototype.test.cjs artifacts/script-creation-completion-2026-08-06
git commit -m "docs: complete script workbench Obsidian and points PRD"
```
