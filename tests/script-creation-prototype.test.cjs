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

test('prototype contains shared transition controls and progress states', () => {
  assert.match(html, /id="stage-transition-view"/);
  assert.match(html, /function renderStageFooter\(\)/);
  assert.match(html, /function renderTransitionPage\(\)/);
  for (const action of ['save-stage-draft','confirm-stage-transition','cancel-stage-transition','simulate-transition-failure','retry-stage-transition','enter-next-stage','lock-storyboard']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
  for (const status of ['QUEUED','RUNNING','SUCCEEDED','FAILED']) assert.ok(html.includes(status));
  assert.ok(html.includes('确认当前阶段并进入下一步'));
  assert.ok(html.includes('确认并锁定文字分镜'));
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
