const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const htmlPath = '.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html';
const html = fs.readFileSync(htmlPath, 'utf8');
const prdPath = path.join(__dirname, '..', '漫剧视频创作平台_PRD.md');
const prd = fs.readFileSync(prdPath, 'utf8');

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
    const position = html.indexOf(`name: '${expected[index]}'`);
    assert.ok(position > -1, `missing stage ${expected[index]}`);
    if (index) assert.ok(position > html.indexOf(`name: '${expected[index - 1]}'`));
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
  assert.deepEqual(Array.from(state.stages, stage => stage.status), ['COMPLETED','COMPLETED','COMPLETED','COMPLETED','STALE','STALE','STALE','STALE']);
});

test('stage transition opens the next stage only after success', () => {
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

test('transition percentage respects task-state boundaries', () => {
  const model = loadModel();
  assert.equal(model.advanceTransitionProgress('QUEUED', 0), 4);
  assert.equal(model.advanceTransitionProgress('QUEUED', 20), 20);
  assert.equal(model.advanceTransitionProgress('RUNNING', 20), 27);
  assert.equal(model.advanceTransitionProgress('RUNNING', 98), 99);
  assert.equal(model.advanceTransitionProgress('FAILED', 62), 62);
  assert.equal(model.advanceTransitionProgress('SUCCEEDED', 62), 100);
});

test('prototype contains shared transition controls and progress states', () => {
  assert.match(html, /id="stage-transition-view"/);
  assert.match(html, /function renderStageFooter\(\)/);
  assert.match(html, /function renderTransitionPage\(\)/);
  for (const action of ['save-stage-draft','confirm-stage-transition','cancel-stage-transition','retry-stage-transition','lock-storyboard']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  for (const status of ['QUEUED','RUNNING','SUCCEEDED','FAILED']) assert.ok(html.includes(status));
  assert.ok(html.includes('确认当前阶段并进入下一步'));
  assert.ok(html.includes('确认并锁定文字分镜'));
});

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

test('prototype includes project task version regenerate stale and export surfaces', () => {
  for (const id of ['project-list-view','workflow-overlay']) assert.match(html, new RegExp(`id="${id}"`));
  for (const id of ['task-center','version-history','version-diff','regenerate-dialog','stale-impact-dialog','export-dialog','archive-dialog']) assert.match(html, new RegExp(`overlayFrame\\('${id}'`));
  for (const action of ['open-project-list','open-project-detail','open-task-center','open-version-history','open-version-diff','open-regenerate','open-stale-impact','open-export','archive-project','close-overlay']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  assert.match(html, /function openOverlay\(type, payload/);
  assert.match(html, /function renderOverlay\(\)/);
});

test('all eight stages expose their development-ready controls', () => {
  for (const key of ['projectName','episodeDuration','language','aspectRatio','safetyLevel']) assert.match(html, new RegExp(`data-model="config\\.${key}"`));
  for (const action of [
    'select-demo-file','retry-upload','confirm-source','preview-chapter',
    'edit-summary','add-event','open-all-characters','edit-world','confirm-analysis',
    'select-hook','add-adaptation-rule','confirm-adaptation',
    'open-episode-structure','add-beat','regenerate-beats','confirm-episode-structure',
    'add-scene','add-script-block','run-script-check','export-script',
    'filter-review-issues','open-review-issue','save-review-revision','approve-episode',
    'add-shot','split-shot','merge-shot','toggle-storyboard-view','run-continuity-check'
  ]) assert.match(html, new RegExp(`data-action="${action}"`));
});

test('prototype includes complete task upload block and storyboard vocabularies', () => {
  for (const status of ['EMPTY','UPLOADING','PARSING','PARSED','CONFIRMED','FAILED','CANCELED']) assert.ok(html.includes(status));
  for (const code of ['FILE_TYPE_INVALID','FILE_TOO_LARGE','FILE_CORRUPTED','CONTENT_TOO_SHORT','PARSE_FAILED']) assert.ok(html.includes(code));
  for (const block of ['ACTION','DIALOGUE','VOICE_OVER','OFF_SCREEN','SUBTITLE','SFX','VFX','TRANSITION']) assert.ok(html.includes(block));
  for (const field of ['shot_size','camera_angle','camera_movement','composition','generation_prompt','negative_prompt','continuity_note']) assert.ok(html.includes(field));
});

test('PRD defines the authoritative eight-stage workflow and transition contract', () => {
  assert.match(prd, /文档版本：V2\.0/);
  assert.match(prd, /八阶段主流程/);
  assert.doesNotMatch(prd, /六步主流程/);
  for (const stage of ['创作设置','小说上传与原文确认','小说分析','改编方案','分集结构','剧本正文','审核修订','文字分镜']) assert.ok(prd.includes(stage));
  for (const term of ['阶段过渡进度页','确认当前阶段并进入下一步','QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELED']) assert.ok(prd.includes(term));
});

test('PRD covers project pages, export configuration and transition acceptance', () => {
  for (const page of ['默认首页','项目列表页','项目详情页','任务中心','版本历史','版本对比','重新生成配置','下游影响确认','导出配置']) assert.ok(prd.includes(page));
  assert.match(prd, /失败.*重试/s);
  assert.match(prd, /过渡.*取消/s);
});

test('workbench uses contextual actions instead of the six-button header toolbar', () => {
  const header = html.match(/<div class="project-head">([\s\S]*?)<div id="stage-content">/)?.[1] || '';
  assert.doesNotMatch(header, /class="project-actions"/);
  for (const id of ['workspace-breadcrumb','stage-version-action','workspace-more-action','workspace-more-menu']) {
    assert.match(header, new RegExp(`id="${id}"`));
  }
  for (const action of ['open-task-center','open-version-history','open-regenerate','open-stale-impact','open-settings-menu','back-home']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  assert.match(html, /function renderContextualStageActions\(\)/);
});

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

test('artifact updates create a new version without losing history', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'SUMMARY-001', type:'summary', stage:2, title:'故事梗概', data:{ summary:'初稿' } });
  const updated = model.updateArtifact(state, 'SUMMARY-001', { data:{ summary:'修订稿' }, updatedBy:'user' });
  assert.equal(updated.version, 2);
  assert.equal(updated.data.summary, '修订稿');
  assert.equal(updated.history.length, 1);
  assert.equal(updated.history[0].data.summary, '初稿');
});

test('point records keep estimate preconsume settlement and refund', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const entry = model.recordPoints(state, { taskId:'TASK-1', estimated:420, preconsumed:420, actual:390, refunded:30, modelId:'demo' });
  assert.equal(entry.actual, 390);
  assert.equal(state.billingEntries.length, 1);
});

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

test('accepting generation versions its source artifact and marks downstream stale', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'ADAPT-001', type:'adaptation', stage:3, title:'改编方案', path:'04-改编方案.md', affects:['EP-001-STRUCTURE'], data:{ 策略:'初稿' } });
  model.createArtifact(state, { id:'EP-001-STRUCTURE', type:'structure', stage:4, title:'单集结构', path:'05-分集结构/EP-001-单集结构.md', dependsOn:['ADAPT-001'], data:{ 节拍:'初稿' } });
  const result = model.acceptGenerationResult(state, { action:'regenerate', taskId:'task-1', artifactId:'ADAPT-001', data:{ 策略:'生成稿' }, actualPoints:0 });
  assert.equal(result.version, 2);
  assert.equal(state.artifacts['ADAPT-001'].history.length, 1);
  assert.equal(state.artifacts['EP-001-STRUCTURE'].stale, true);
});

test('generation running state updates its task and result diff remains visible', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const generation = model.startGeneration(state, { action:'regenerate', before:'旧方案内容', after:'新方案内容' });
  model.setGenerationStatus(state, 'RUNNING');
  assert.equal(state.tasks[0].status, 'RUNNING');
  assert.equal(generation.before, '旧方案内容');
  assert.equal(generation.after, '新方案内容');
  assert.match(html, /result\.before/);
  assert.match(html, /result\.after/);
});

test('task center exposes revisitable generation results', () => {
  assert.match(html, /actionResults/);
  assert.match(html, /data-action="open-generation-result"/);
});

test('regeneration without a saved stage artifact is guided without creating records', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const precondition = model.evaluateActionPrecondition({ hasStageArtifact:false }, 'open-regenerate');
  assert.equal(precondition.allowed, false);
  assert.equal(precondition.code, 'STAGE_ARTIFACT_REQUIRED');
  assert.equal(state.tasks.length, 0);
  assert.equal(state.billingEntries.length, 0);
});

test('accepting the same generation task twice is idempotent', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'ADAPT-001', type:'adaptation', stage:3, title:'改编方案', path:'04-改编方案.md', data:{ 策略:'初稿' } });
  const spec = { action:'regenerate', taskId:'task-1', artifactId:'ADAPT-001', data:{ 策略:'生成稿' }, actualPoints:0 };
  const first = model.acceptGenerationResult(state, spec);
  const second = model.acceptGenerationResult(state, spec);
  assert.equal(second.id, first.id);
  assert.equal(state.artifacts['ADAPT-001'].version, 2);
  assert.equal(state.billingEntries.length, 1);
});

test('revisited generation results render in readonly mode', () => {
  assert.match(html, /result\.readonly/);
  assert.match(html, /已采纳结果仅供查看/);
});

test('stage draft remediation creates regeneratable artifacts with dependency mappings', () => {
  const model = loadModel();
  const cases = [
    [2, 'SUMMARY-001', '03-小说分析/故事梗概.md', ['SOURCE-001'], ['ADAPT-001']],
    [3, 'ADAPT-001', '04-改编方案.md', ['SUMMARY-001','EVENTS-001','WORLD-001'], ['EP-001-STRUCTURE']],
    [5, 'EP-001-SCRIPT', '06-剧本正文/EP-001-剧本正文.md', ['EP-001-STRUCTURE','CHAR-001','WORLD-001'], ['EP-001-REVIEW','EP-001-STORYBOARD']],
    [7, 'EP-001-STORYBOARD', '08-文字分镜/EP-001-文字分镜.md', ['EP-001-REVIEW','CHAR-001','WORLD-001'], []]
  ];
  for (const [stage, id, path, dependsOn, affects] of cases) {
    const state = model.createInitialState();
    const artifact = model.ensureStageArtifactForGeneration(state, stage);
    assert.equal(artifact.id, id);
    assert.equal(artifact.path, path);
    assert.deepEqual(Array.from(artifact.dependsOn), dependsOn);
    assert.deepEqual(Array.from(artifact.affects), affects);
    assert.equal(model.evaluateActionPrecondition({ hasStageArtifact:true }, 'open-regenerate').allowed, true);
  }
});

test('saving existing analysis and script drafts preserves real data and invalidates dependents', () => {
  const model = loadModel();
  const cases = [
    {
      stage:2,
      artifact:{ id:'SUMMARY-001', type:'summary', title:'故事梗概', path:'03-小说分析/故事梗概.md', data:{ 完整梗概:'林野追查前世死亡真相。', 主线目标:'找到账本' } },
      dependent:{ id:'ADAPT-001', type:'adaptation', stage:3, title:'改编方案', dependsOn:['SUMMARY-001'], data:{ 策略:'初稿' } }
    },
    {
      stage:5,
      artifact:{ id:'EP-001-SCRIPT', type:'script', title:'EP-001 剧本正文', path:'06-剧本正文/EP-001-剧本正文.md', data:{ 集数:'第 1 集', 场景:[{ id:'SCENE-001', 正文块:[{ id:'BLOCK-001', text:'林野猛地睁眼。' }] }] } },
      dependent:{ id:'EP-001-REVIEW', type:'review', stage:6, title:'EP-001 审核记录', dependsOn:['EP-001-SCRIPT'], data:{ 状态:'待审核' } }
    }
  ];

  for (const { stage, artifact:artifactSpec, dependent } of cases) {
    const state = model.createInitialState();
    const originalData = JSON.parse(JSON.stringify(artifactSpec.data));
    model.createArtifact(state, { ...artifactSpec, stage });
    model.createArtifact(state, dependent);

    const saved = model.saveStageDraft(state, stage);

    assert.equal(saved.version, 2);
    assert.deepEqual(JSON.parse(JSON.stringify(saved.data)), originalData);
    assert.deepEqual(JSON.parse(JSON.stringify(saved.history[0].data)), originalData);
    assert.equal(state.artifacts[dependent.id].stale, true);
  }
});

test('generation remediation creates a missing artifact from current stage data', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const currentScript = { 集数:'第 1 集', 场景:[{ id:'SCENE-009', 正文块:[{ id:'BLOCK-021', text:'账本就在拍卖会。' }] }] };

  const artifact = model.ensureStageArtifactForGeneration(state, 5, currentScript);

  assert.deepEqual(JSON.parse(JSON.stringify(artifact.data)), currentScript);
  assert.equal(artifact.version, 1);
  assert.equal(model.evaluateActionPrecondition({ hasStageArtifact:Boolean(state.artifacts['EP-001-SCRIPT']) }, 'open-regenerate').allowed, true);
});

test('save stage draft remediation opens a retryable artifact confirmation', () => {
  assert.match(html, /function saveCurrentStageDraft\(\)/);
  assert.match(html, /data-action="open-regenerate"/);
  assert.match(html, /草稿已保存/);
});

test('empty pricing response falls back to demo models', () => {
  const model = loadModel();
  const result = model.resolveModels([], [{ id:'demo-script-pro', name:'演示剧本模型', demo:true, points:0 }]);
  assert.equal(result.source, 'fallback');
  assert.equal(result.items[0].demo, true);
  assert.equal(result.items[0].points, 0);
});

test('pricing response normalizes real models from localhost 3001', () => {
  const model = loadModel();
  const result = model.normalizePricingModels({ data:[{ model_name:'model-a', vendor_name:'供应商A', model_ratio:1.5, completion_ratio:2 }] });
  assert.equal(result[0].id, 'model-a');
  assert.equal(result[0].demo, false);
  assert.equal(result[0].modelRatio, 1.5);
  assert.equal(result[0].completionRatio, 2);
});

test('upload and model controls are present', () => {
  assert.match(html, /data-source-text/);
  assert.match(html, /汉字.*\/2000/);
  for (const action of ['refresh-models','select-project-model','preview-source-markdown']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});

test('analysis editors have save actions and markdown targets', () => {
  for (const action of ['save-summary','save-event','save-character','save-world','open-markdown-preview','apply-impact-choice']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  for (const file of ['故事梗概.md','主要事件.md','章节大纲.md','世界观.md','00-人物索引.md']) assert.ok(html.includes(file));
});

test('adaptation and structure actions persist instead of only notifying', () => {
  for (const action of ['choose-hook','save-adaptation-rule','save-adaptation','save-episode-structure','save-beat','confirm-beat-regeneration']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});

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

test('manual analysis and adaptation results retain succeeded task and zero-point billing records', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const result = model.completeManualAction(state, {
    action:'save-summary', stage:2, scope:'小说分析 · 保存故事梗概',
    artifactId:'SUMMARY-001', path:'03-小说分析/故事梗概.md', version:1
  });

  assert.equal(result.status, 'SUCCEEDED');
  assert.equal(result.resultType, 'MANUAL_ACTION');
  assert.ok(result.taskId);
  assert.equal(state.tasks[0].id, result.taskId);
  assert.equal(state.tasks[0].status, 'SUCCEEDED');
  assert.equal(state.versions.analysis.at(-1).source, '人工保存');
  assert.equal(state.billingEntries[0].taskId, result.taskId);
  assert.deepEqual(JSON.parse(JSON.stringify(state.billingEntries[0])), {
    id:'BILL-1', status:'SETTLED', createdAt:'2026-08-06T11:30:00+08:00', taskId:result.taskId,
    operation:'save-summary', modelId:'manual', estimated:0, preconsumed:0, actual:0, refunded:0
  });
  assert.match(html, /open-action-result/);
  assert.match(html, /状态：/);
  assert.match(html, /任务 /);
  assert.match(html, /积分/);
});

test('all script AI tools and editor controls are actionable', () => {
  for (const action of ['select-script-block','ai-continue','ai-conflict','ai-condense-dialogue','ai-rewrite-tone','ai-character-check','save-scene','save-script-block','open-script-check','open-script-export','accept-ai-diff']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});

test('structure and script operations expose visible outputs', () => {
  for (const action of ['view-structure-result','view-script-result','focus-script-blocks','open-script-check','open-script-export']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  assert.doesNotMatch(html, /data-action="ai-(?:continue|conflict|condense-dialogue|rewrite-tone|character-check)"[^>]*disabled/);
});

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

test('review approval is blocked while high severity issues remain open', () => {
  const model = loadModel();
  assert.equal(model.canApproveReview([{ severity:'HIGH', status:'OPEN' }]), false);
  assert.equal(model.canApproveReview([{ severity:'HIGH', status:'RESOLVED' }, { severity:'MEDIUM', status:'OPEN' }]), true);
});

test('review storyboard and delivery controls expose complete interactions', () => {
  for (const action of ['apply-review-filter','save-review-edit','open-review-diff','resolve-review-issue','approve-review','select-shot','save-shot','undo-storyboard','archive-project-complete','open-export','create-canvas-project']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});

test('script export confirmation persists a completed manual result with delivery metadata', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const result = model.completeExportAction(state, {
    stage:5, scope:'剧本正文 · 第 1 集', artifactId:'EP-001-SCRIPT',
    path:'06-剧本正文/EP-001-剧本正文.md', version:3, exportRange:'第 1 集', exportFormat:'DOCX'
  });

  assert.equal(result.status, 'SUCCEEDED');
  assert.equal(result.resultType, 'MANUAL_ACTION');
  assert.equal(result.exportRange, '第 1 集');
  assert.equal(result.exportFormat, 'DOCX');
  assert.equal(state.tasks[0].status, 'SUCCEEDED');
  assert.equal(state.billingEntries[0].actual, 0);
});

test('script and project export confirmations preserve separate action scopes and metadata', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const scriptResult = model.completeExportAction(state, {
    stage:5, artifactId:'EP-001-SCRIPT', path:'06-剧本正文/EP-001-剧本正文.md', version:4,
    exportRange:'第 1 集', exportFormat:'JSON'
  });
  const projectResult = model.completeProjectExportAction(state, {
    stage:7, exportRange:'全部有效产物', exportVersion:'当前已确认版本', exportFormat:'DOCX / PDF', packagePath:'重生项目_创作包.zip'
  });

  assert.equal(scriptResult.action, 'export-script');
  assert.equal(scriptResult.artifactId, 'EP-001-SCRIPT');
  assert.equal(projectResult.action, 'export-project');
  assert.equal(projectResult.artifactId, null);
  assert.equal(projectResult.exportRange, '全部有效产物');
  assert.equal(projectResult.exportVersion, '当前已确认版本');
  assert.equal(projectResult.exportFormat, 'DOCX / PDF');
  assert.equal(projectResult.packagePath, '重生项目_创作包.zip');
  assert.match(html, /data-action="confirm-script-export"/);
  assert.match(html, /data-action="confirm-project-export"/);
});

test('stage model selection drives the displayed and submitted generation estimate', () => {
  const model = loadModel();
  const state = model.createInitialState();
  state.models.items = [
    { id:'project-model', demo:false, modelRatio:1, completionRatio:1, groupRatio:1 },
    { id:'stage-model', demo:false, modelRatio:2, completionRatio:3, groupRatio:1 }
  ];
  state.models.stageSelections = { 4:'stage-model' };
  const generation = model.generationPricing(state, 4, 'project-model', { inputTokens:1800, outputTokens:600 });

  assert.equal(generation.model.id, 'stage-model');
  assert.equal(generation.estimatedPoints, 7200);
});

test('accepting script AI generation versions its artifact, stales dependents, and settles a result', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'EP-001-SCRIPT', type:'script', stage:5, title:'EP-001 剧本正文', path:'06-剧本正文/EP-001-剧本正文.md', affects:['EP-001-REVIEW'], data:{ 正文:'旧文本' } });
  model.createArtifact(state, { id:'EP-001-REVIEW', type:'review', stage:6, title:'审核记录', path:'07-审核修订/EP-001-审核记录.md', dependsOn:['EP-001-SCRIPT'], data:{ 结论:'待检查' } });
  const result = model.acceptGenerationResult(state, { action:'script-ai-edit', taskId:'TASK-AI-1', artifactId:'EP-001-SCRIPT', data:{ 正文:'AI 新文本' }, estimatedPoints:12, actualPoints:9, modelId:'stage-model' });

  assert.equal(state.artifacts['EP-001-SCRIPT'].version, 2);
  assert.equal(state.artifacts['EP-001-REVIEW'].stale, true);
  assert.equal(result.status, 'SUCCEEDED');
  assert.equal(state.billingEntries[0].actual, 9);
});

test('point estimate follows 3001 token ratio quota rules', () => {
  const model = loadModel();
  assert.equal(model.estimatePoints({ demo:true }, { inputTokens:1000, outputTokens:500 }), 0);
  assert.equal(model.estimatePoints({ modelRatio:2, completionRatio:3, groupRatio:1 }, { inputTokens:1000, outputTokens:500 }), 5000);
});

test('vault export contains project index impact and billing markdown', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'SUMMARY-001', type:'summary', stage:2, title:'故事梗概', path:'03-小说分析/故事梗概.md', data:{ 梗概:'测试' } });
  model.recordPoints(state, { taskId:'TASK-1', operation:'生成梗概', modelId:'demo', estimated:0, preconsumed:0, actual:0, refunded:0 });
  const files = model.buildVaultFiles(state);
  for (const path of ['00-项目主页.md','01-创作设置.md','03-小说分析/故事梗概.md','90-变更影响.md','99-生成与计费记录.md']) assert.ok(files[path]);
  assert.match(files['99-生成与计费记录.md'], /实际积分/);
});

test('PRD specifies Obsidian vault model fallback and full points settlement', () => {
  for (const term of ['Obsidian','00-项目主页.md','90-变更影响.md','99-生成与计费记录.md','1 quota = 1 积分','演示模型','预计积分','预扣积分','实际积分','返还积分']) assert.ok(prd.includes(term), `missing ${term}`);
});
