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

test('all script AI tools and editor controls are actionable', () => {
  for (const action of ['select-script-block','ai-continue','ai-conflict','ai-condense-dialogue','ai-rewrite-tone','ai-character-check','save-scene','save-script-block','open-script-check','open-script-export','accept-ai-diff']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
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
