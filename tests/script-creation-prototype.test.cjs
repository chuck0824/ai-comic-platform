const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const htmlPath = '.superpowers/brainstorm/15618-1785939141/content/eight-stage-workbench.html';
const html = fs.readFileSync(htmlPath, 'utf8');
const prdPath = path.join(__dirname, '..', '漫剧视频创作平台_PRD.md');
const prd = fs.readFileSync(prdPath, 'utf8');
const matrixPath = path.join(__dirname, '..', 'artifacts', 'script-action-feedback-2026-08-06', 'behavior-matrix.md');
const matrix = fs.readFileSync(matrixPath, 'utf8');
const screenshotPath = path.join(__dirname, '..', 'artifacts', 'script-action-feedback-2026-08-06', 'final-action-results.png');
const screenshot = fs.readFileSync(screenshotPath);

function behaviorRows() {
  return matrix.split(/\r?\n/)
    .filter(line => /^\|\s*\d+\s*\|/.test(line))
    .map(line => line.split('|').slice(1, -1).map(cell => cell.trim()));
}

test('PRD requires clickable actions condition guidance and persistent results', () => {
  for (const term of ['所有业务按钮可点击','条件不足','操作前需要完成','结果弹层','不能仅以 Toast','行为结果测试']) {
    assert.ok(prd.includes(term), `missing ${term}`);
  }
});

test('PRD sections 17.1 through 17.5 preserve the complete action feedback contract', () => {
  for (const section of ['17.1 Action Guard 规则','17.2 生成与结果状态','17.3 六阶段操作矩阵','17.4 交付与只读规则','17.5 行为结果测试']) assert.ok(prd.includes(section), section);
  for (const field of ['allowed','code','title','message','targetAction','targetLabel']) assert.ok(prd.includes(`\`${field}\``), field);
  for (const code of ['SCRIPT_BLOCK_REQUIRED','REVIEW_BLOCKED','SHOT_REQUIRED','STAGE_ARTIFACT_REQUIRED','STORYBOARD_HISTORY_REQUIRED','PROJECT_ARCHIVED']) assert.ok(prd.includes(`\`${code}\``), code);
  for (const action of [
    '编辑梗概','新增事件','人物详情','编辑世界观','确认改编方案','选择高压开场','新增改编规则',
    '打开单集结构','新增节拍','重新生成节拍','续写选中段落','增强冲突','精简对白','改写语气','检查角色一致性',
    '新增场景','新增正文块','运行正文检查','导出正文','筛选问题','保存局部修订','对比修订前后','审核通过本集',
    '新增镜头','拆分镜头','合并镜头','切换卡片/表格','连续性检查','完成并归档','配置导图','创建画布项目'
  ]) assert.ok(prd.includes(action), action);
  for (const stage of ['改编方案','结构化文字剧本','剧本正文','审核修订','文字分镜']) {
    assert.ok(prd.includes(`${stage}：artifact 缺失 blocked 零写入；artifact 存在 allowed 生成/采纳。`), `${stage} regen dual path`);
  }
  for (const term of ['Toast 只是补充反馈','Markdown 路径','STALE','PROJECT_ARCHIVED','静态演示','不会对外部后端']) assert.ok(prd.includes(term), term);
});

test('PRD captures the final settlement readonly and executable-evidence corrections', () => {
  for (const term of ['生成成功即结算','结果决策','完整计价快照','SOURCE_CONFIRMATION_REQUIRED','EPISODE_NOT_AVAILABLE','确认恢复','仅预览','可执行 DOM']) {
    assert.ok(prd.includes(term), term);
  }
});

test('browser matrix has exactly 45 complete action rows and all required dual paths', () => {
  const rows = behaviorRows();
  assert.equal(rows.length, 45);
  assert.match(matrix, /\| # \| Stage \| Action \| Prerequisite \| Browser-visible click result \| Persistent result \| Status \|/);
  for (const row of rows) {
    assert.equal(row.length, 7, `row ${row[0]} column count`);
    assert.equal(row[6], 'PASS', `row ${row[0]} status`);
  }
  for (const marker of [
    'AI 编辑 / 续写选中段落 (blocked)','AI 编辑 / 续写选中段落 (allowed)',
    '审核通过本集 (blocked)','审核通过本集 (allowed)',
    '拆分镜头 (blocked)','拆分镜头 (allowed)','合并镜头 (blocked)','合并镜头 (allowed)'
  ]) assert.ok(matrix.includes(marker), marker);
  assert.match(matrix, /\| 5 \| 改编方案 \| 重新生成当前产物 \(blocked\)/);
  assert.match(matrix, /\| 9 \| 改编方案 \| 重新生成当前产物 \(allowed\)/);
  for (const [row, stage] of [[13,'结构化文字剧本'],[24,'剧本正文'],[30,'审核修订'],[39,'文字分镜']]) {
    const line = rows.find(item => item[0] === String(row));
    assert.equal(line[1], stage);
    assert.match(line[2], /\(blocked \+ allowed\)/);
    assert.match(line[4], /blocked:[\s\S]*allowed:/);
    assert.match(line[5], /before tasks=\d+, results=\d+, billing=\d+, artifacts=\d+; after blocked tasks=\d+, results=\d+, billing=\d+, artifacts=\d+; after allowed tasks=\d+, results=\d+, billing=\d+, artifacts=\d+/);
  }
  const persistentWriteRows = [1,2,3,4,6,7,8,9,10,11,12,13,15,16,17,18,19,20,21,22,23,24,27,29,30,34,35,36,38,39];
  for (const rowNumber of persistentWriteRows) {
    const saved = rows.find(row => row[0] === String(rowNumber))[5];
    assert.match(saved, /(?:\.md|artifactId=)/, `row ${rowNumber} path/artifact`);
    assert.match(saved, /\bV\d+\b/, `row ${rowNumber} version`);
    assert.match(saved, /task-\d+/, `row ${rowNumber} task`);
    assert.match(saved, /\d+ 积分/, `row ${rowNumber} points`);
  }
  assert.match(rows.find(row => row[0] === '28')[5], /0 (?:任务|task).*0 (?:计费|billing).*0 (?:版本|version)/);
  for (const [rowNumber, token] of [[40,'packagePath='],[41,'packagePath='],[42,'canvasProjectId='],[43,'archiveTaskId=']]) {
    const saved = rows.find(row => row[0] === String(rowNumber))[5];
    assert.ok(saved.includes(token), `row ${rowNumber} ${token}`);
    assert.match(saved, /task-\d+/);
    assert.match(saved, /\d+ 积分/);
  }
});

test('final browser evidence is a real 1280 by 720 PNG with documented IHDR proof', () => {
  assert.equal(screenshot.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  assert.equal(screenshot.subarray(12, 16).toString('ascii'), 'IHDR');
  assert.equal(screenshot.readUInt32BE(16), 1280);
  assert.equal(screenshot.readUInt32BE(20), 720);
  assert.ok(matrix.includes('PNG signature: `89504e470d0a1a0a`; IHDR: `1280×720`'));
});

test('all five current-artifact regeneration guards are zero-write when blocked and persistent when allowed', () => {
  const model = loadModel();
  const cases = [
    [3, 'ADAPT-001', '04-改编方案.md'],
    [4, 'EP-001-STRUCTURE', '05-分集结构/EP-001-单集结构.md'],
    [5, 'EP-001-SCRIPT', '06-剧本正文/EP-001-剧本正文.md'],
    [6, 'EP-001-REVIEW', '07-审核修订/EP-001-审核记录.md'],
    [7, 'EP-001-STORYBOARD', '08-文字分镜/EP-001-文字分镜.md']
  ];
  const counts = state => [state.tasks.length, state.actionResults.length, state.billingEntries.length, Object.keys(state.artifacts).length];

  for (const [stage, artifactId, artifactPath] of cases) {
    const state = model.createInitialState();
    const before = counts(state);
    const blocked = model.evaluateActionPrecondition({ hasStageArtifact:false }, 'open-regenerate');
    assert.equal(blocked.code, 'STAGE_ARTIFACT_REQUIRED');
    assert.deepEqual(counts(state), before, `stage ${stage} blocked must be zero-write`);

    const artifact = model.ensureStageArtifactForGeneration(state, stage, { 修复轮次:'browser-matrix-audit' });
    assert.equal(artifact.id, artifactId);
    assert.equal(artifact.path, artifactPath);
    assert.equal(model.evaluateActionPrecondition({ hasStageArtifact:true }, 'open-regenerate').allowed, true);

    const generation = model.startGeneration(state, {
      action:'regenerate', stage, artifactId, path:artifactPath,
      data:{ 修复轮次:'browser-matrix-audit-accepted' }, before:'V1', after:'V2',
      modelId:'demo-script-pro', estimatedPoints:0, actualPoints:0
    });
    model.setGenerationStatus(state, 'RUNNING');
    model.setGenerationStatus(state, 'SUCCEEDED');
    const result = model.acceptGenerationResult(state, { ...generation.spec, taskId:generation.taskId });

    assert.deepEqual(counts(state), [1,1,1,1], `stage ${stage} allowed counts`);
    assert.equal(result.taskId, 'task-1');
    assert.equal(result.path, artifactPath);
    assert.equal(result.version, 2);
    assert.equal(result.actualPoints, 0);
    assert.equal(state.artifacts[artifactId].version, 2);
  }
});

test('footer stage confirmation guard blocks every incomplete stage before task creation', () => {
  const model = loadModel();
  const cases = [
    [0, { configComplete:false }, 'CREATION_CONFIG_REQUIRED'],
    [1, { sourceText:'', sourceConfirmed:false, hasStageArtifact:false }, 'SOURCE_CONFIRMATION_REQUIRED'],
    [2, { hasStageArtifact:false }, 'STAGE_ARTIFACT_REQUIRED'],
    [3, { hasStageArtifact:true, selectedHook:null, adaptationRuleCount:1 }, 'HOOK_REQUIRED'],
    [4, { hasStageArtifact:false }, 'STAGE_ARTIFACT_REQUIRED'],
    [5, { hasStageArtifact:false }, 'STAGE_ARTIFACT_REQUIRED'],
    [6, { hasStageArtifact:true, issues:[{ severity:'BLOCKER', status:'OPEN' }] }, 'REVIEW_BLOCKED'],
    [7, { hasStageArtifact:false }, 'STAGE_ARTIFACT_REQUIRED']
  ];

  for (const [stage, context, expectedCode] of cases) {
    const state = model.createInitialState();
    const before = {
      tasks:state.tasks.length,
      results:state.actionResults.length,
      billing:state.billingEntries.length,
      artifacts:Object.keys(state.artifacts).length
    };
    const outcome = model.beginStageConfirmation(state, stage, context);
    assert.equal(outcome.allowed, false, `stage ${stage} must be blocked`);
    assert.equal(outcome.code, expectedCode, `stage ${stage} code`);
    assert.deepEqual({
      tasks:state.tasks.length,
      results:state.actionResults.length,
      billing:state.billingEntries.length,
      artifacts:Object.keys(state.artifacts).length
    }, before, `stage ${stage} blocked confirmation is zero-write`);
  }
});

test('footer stage confirmation creates a transition task only after its artifact is valid', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, {
    id:'SUMMARY-001', type:'summary', stage:2, title:'故事梗概',
    path:'03-小说分析/故事梗概.md', data:{ 完整梗概:'已保存梗概' }
  });

  const outcome = model.beginStageConfirmation(state, 2, { hasStageArtifact:true });
  assert.equal(outcome.allowed, true);
  assert.equal(outcome.transition.taskId, 'task-1');
  assert.equal(state.tasks.length, 1);
  assert.equal(state.actionResults.length, 0);
  assert.equal(state.billingEntries.length, 0);
  assert.equal(Object.keys(state.artifacts).length, 1);
});

test('review revision requires a scene and block target without mutating artifacts', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const scenes = [{ id:'SCENE-001', blocks:[{ id:'BLOCK-001', type:'ACTION', text:'原正文' }] }];
  model.createArtifact(state, { id:'EP-001-SCRIPT', type:'script', stage:5, title:'正文', data:{ 场景列表:scenes } });
  model.createArtifact(state, { id:'EP-001-REVIEW', type:'review', stage:6, title:'审核', data:{ 问题列表:[] } });
  const before = JSON.parse(JSON.stringify({ artifacts:state.artifacts, scenes }));

  const outcome = model.applyReviewRevision(state, {
    issueId:'ISSUE-001',
    issues:[{ id:'ISSUE-001', severity:'HIGH', status:'OPEN', title:'无定位问题' }],
    scenes,
    revision:'修订正文'
  });

  assert.equal(outcome.allowed, false);
  assert.equal(outcome.code, 'REVIEW_TARGET_REQUIRED');
  assert.deepEqual(JSON.parse(JSON.stringify({ artifacts:state.artifacts, scenes })), before);
});

test('review revision applies text to the referenced script block and versions both artifacts', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const scenes = [{ id:'SCENE-001', title:'出租屋', blocks:[{ id:'BLOCK-001', type:'ACTION', text:'林野看见账本。' }] }];
  const issues = [{ id:'ISSUE-001', sceneId:'SCENE-001', blockId:'BLOCK-001', severity:'HIGH', status:'OPEN', title:'缺少水印动作' }];
  model.createArtifact(state, {
    id:'EP-001-SCRIPT', type:'script', stage:5, title:'EP-001 剧本正文',
    path:'06-剧本正文/EP-001-剧本正文.md', data:{ 场景列表:scenes }
  });
  model.createArtifact(state, {
    id:'EP-001-REVIEW', type:'review', stage:6, title:'EP-001 审核记录',
    path:'07-审核修订/EP-001-审核记录.md', data:{ 问题列表:issues, 修订记录:[] }
  });
  state.artifacts['EP-001-REVIEW'].dependsOn = ['EP-001-SCRIPT'];
  state.artifacts['EP-001-REVIEW'].affects = ['EP-001-STORYBOARD'];
  model.createArtifact(state, {
    id:'EP-001-STORYBOARD', type:'storyboard', stage:7, title:'EP-001 文字分镜',
    path:'08-文字分镜/EP-001-文字分镜.md', dependsOn:['EP-001-REVIEW'], data:{ 镜头列表:[] }
  });

  const outcome = model.applyReviewRevision(state, {
    issueId:'ISSUE-001', issues, scenes,
    revision:'林野举起手机，拍下账本水印与时间。'
  });

  assert.equal(outcome.allowed, true);
  assert.equal(outcome.before, '林野看见账本。');
  assert.equal(outcome.after, '林野举起手机，拍下账本水印与时间。');
  assert.equal(scenes[0].blocks[0].text, outcome.after);
  assert.equal(state.artifacts['EP-001-SCRIPT'].version, 2);
  assert.equal(state.artifacts['EP-001-SCRIPT'].data.场景列表[0].blocks[0].text, outcome.after);
  assert.equal(state.artifacts['EP-001-SCRIPT'].history[0].data.场景列表[0].blocks[0].text, outcome.before);
  assert.equal(state.artifacts['EP-001-REVIEW'].version, 2);
  assert.equal(state.artifacts['EP-001-REVIEW'].data.问题列表[0].status, 'RESOLVED');
  assert.equal(state.artifacts['EP-001-REVIEW'].data.修订记录[0].after, outcome.after);
  assert.equal(state.artifacts['EP-001-REVIEW'].history[0].data.修订记录.length, 0);
  assert.equal(state.artifacts['EP-001-REVIEW'].stale, false);
  assert.equal(state.artifacts['EP-001-STORYBOARD'].stale, true);
});

test('3001 pricing normalization preserves the complete fixed and ratio snapshots', () => {
  const model = loadModel();
  const payload = {
    data:[
      { model_name:'fixed-model', description:'按次模型', icon:'fixed.svg', tags:'fixed', owner_by:'vendor', quota_type:1, model_price:0.02, model_ratio:0, completion_ratio:0, cache_ratio:0.1, create_cache_ratio:1.25, billing_mode:'fixed', enable_groups:['vip'], supported_endpoint_types:['chat'], vendor_id:9, pricing_version:'item-v1' },
      { model_name:'ratio-model', description:'倍率模型', icon:'ratio.svg', tags:'ratio', owner_by:'vendor', quota_type:0, model_price:0, model_ratio:2, completion_ratio:3, cache_ratio:0.1, create_cache_ratio:1.25, billing_mode:'ratio', enable_groups:['vip'], supported_endpoint_types:['chat','responses'], vendor_id:9 }
    ],
    vendors:[{ id:9, name:'供应商甲', description:'官方模型', icon:'vendor.svg' }],
    group_ratio:{ vip:1.5 },
    usable_group:{ vip:'VIP' },
    supported_endpoint:{ chat:{ name:'Chat' }, responses:{ name:'Responses' } },
    auto_groups:['vip'],
    pricing_version:'payload-v2'
  };

  const snapshot = model.normalizePricingPayload(payload);
  assert.equal(snapshot.pricingVersion, 'payload-v2');
  assert.deepEqual(JSON.parse(JSON.stringify(snapshot.vendors)), payload.vendors);
  assert.deepEqual(JSON.parse(JSON.stringify(snapshot.groupRatio)), payload.group_ratio);
  assert.deepEqual(JSON.parse(JSON.stringify(snapshot.usableGroup)), payload.usable_group);
  assert.deepEqual(JSON.parse(JSON.stringify(snapshot.supportedEndpoint)), payload.supported_endpoint);
  assert.deepEqual(JSON.parse(JSON.stringify(snapshot.autoGroups)), payload.auto_groups);
  assert.deepEqual(JSON.parse(JSON.stringify(snapshot.items.map(item => ({
    id:item.id, quotaType:item.quotaType, modelPrice:item.modelPrice, modelRatio:item.modelRatio,
    completionRatio:item.completionRatio, cacheRatio:item.cacheRatio, createCacheRatio:item.createCacheRatio,
    billingMode:item.billingMode, enableGroups:item.enableGroups, groupRatio:item.groupRatio,
    vendorId:item.vendorId, vendor:item.vendor, pricingVersion:item.pricingVersion
  })))), [
    { id:'fixed-model', quotaType:1, modelPrice:0.02, modelRatio:0, completionRatio:0, cacheRatio:0.1, createCacheRatio:1.25, billingMode:'fixed', enableGroups:['vip'], groupRatio:1.5, vendorId:9, vendor:'供应商甲', pricingVersion:'item-v1' },
    { id:'ratio-model', quotaType:0, modelPrice:0, modelRatio:2, completionRatio:3, cacheRatio:0.1, createCacheRatio:1.25, billingMode:'ratio', enableGroups:['vip'], groupRatio:1.5, vendorId:9, vendor:'供应商甲', pricingVersion:'payload-v2' }
  ]);

  const state = model.createInitialState();
  Object.assign(state.models, snapshot, { source:'remote' });
  const taskSnapshot = model.createPricingSnapshot(state, snapshot.items[1]);
  assert.deepEqual(JSON.parse(JSON.stringify(taskSnapshot.groupRatioMap)), payload.group_ratio);
  assert.deepEqual(JSON.parse(JSON.stringify(taskSnapshot.usableGroup)), payload.usable_group);
  assert.deepEqual(JSON.parse(JSON.stringify(taskSnapshot.supportedEndpoint)), payload.supported_endpoint);
  assert.deepEqual(JSON.parse(JSON.stringify(taskSnapshot.autoGroups)), payload.auto_groups);
  assert.deepEqual(JSON.parse(JSON.stringify(taskSnapshot.endpoints)), ['chat','responses']);
  assert.equal(taskSnapshot.description, '倍率模型');
  assert.equal(taskSnapshot.ownerBy, 'vendor');
});

test('3001 local estimate supports fixed price and cache-aware ratio price', () => {
  const model = loadModel();
  assert.equal(model.estimatePoints({ demo:false, quotaType:1, modelPrice:0.02, groupRatio:1.5 }, { inputTokens:1000, outputTokens:200 }), 15000);
  assert.equal(model.estimatePoints({ demo:false, quotaType:0, modelRatio:2, completionRatio:3, cacheRatio:0.1, createCacheRatio:1.25, groupRatio:1.5 }, {
    inputTokens:1000, outputTokens:200, cacheReadTokens:100, cacheCreationTokens:50
  }), 4568);
  assert.equal(model.estimatePoints({ demo:true, quotaType:1, modelPrice:99, groupRatio:10 }, { inputTokens:1000 }), 0);
});

test('generation success settles once before accept or discard and preserves its pricing snapshot', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'ADAPT-001', type:'adaptation', stage:3, title:'改编方案', path:'04-改编方案.md', data:{ 策略:'旧版' } });
  const pricingSnapshot = {
    source:'remote', pricingVersion:'payload-v2', quotaType:0, modelPrice:0, modelRatio:2,
    completionRatio:3, cacheRatio:0.1, createCacheRatio:1.25, billingMode:'ratio',
    enableGroups:['vip'], groupRatio:1.5, vendors:[{ id:9, name:'供应商甲' }]
  };
  const generation = model.startGeneration(state, {
    action:'regenerate', stage:3, artifactId:'ADAPT-001', path:'04-改编方案.md',
    modelId:'ratio-model', estimatedPoints:420, actualPoints:390, pricingSnapshot,
    data:{ 策略:'新版' }, before:'旧版', after:'新版'
  });

  model.setGenerationStatus(state, 'SUCCEEDED');
  assert.equal(state.tasks[0].status, 'SUCCEEDED');
  assert.equal(state.billingEntries.length, 1);
  assert.equal(state.billingEntries[0].actual, 390);
  assert.deepEqual(JSON.parse(JSON.stringify(state.billingEntries[0].pricingSnapshot)), pricingSnapshot);
  assert.equal(state.actionResults.length, 1);
  assert.equal(state.actionResults[0].decision, 'PENDING');
  assert.equal(state.artifacts['ADAPT-001'].version, 1);
  assert.match(model.buildVaultFiles(state)['99-生成与计费记录.md'], /"pricingVersion":"payload-v2"/);
  assert.match(model.buildVaultFiles(state)['99-生成与计费记录.md'], /"quotaType":0/);

  const discarded = model.discardGenerationResult(state, generation.taskId);
  assert.equal(discarded.decision, 'DISCARDED');
  assert.equal(state.billingEntries.length, 1);
  assert.equal(state.actionResults.length, 1);
  assert.equal(state.artifacts['ADAPT-001'].version, 1);
});

test('accepting an already settled generation versions the artifact without billing twice', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'ADAPT-001', type:'adaptation', stage:3, title:'改编方案', path:'04-改编方案.md', data:{ 策略:'旧版' } });
  const generation = model.startGeneration(state, {
    action:'regenerate', stage:3, artifactId:'ADAPT-001', path:'04-改编方案.md', modelId:'ratio-model',
    estimatedPoints:420, actualPoints:390, pricingSnapshot:{ pricingVersion:'payload-v2', quotaType:0 },
    data:{ 策略:'新版' }, before:'旧版', after:'新版'
  });
  model.setGenerationStatus(state, 'SUCCEEDED');

  const accepted = model.acceptGenerationResult(state, { ...generation.spec, taskId:generation.taskId });
  assert.equal(accepted.decision, 'ACCEPTED');
  assert.equal(accepted.version, 2);
  assert.equal(state.artifacts['ADAPT-001'].data.策略, '新版');
  assert.equal(state.billingEntries.length, 1);
  assert.equal(state.actionResults.length, 1);
});

test('source markdown preview is read-only even before a source artifact exists', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const before = JSON.parse(JSON.stringify(state));

  const preview = model.renderSourcePreview(state, { sourceText:'只用于预览的原文', chapterRange:'第 1 章' });

  assert.match(preview, /只用于预览的原文/);
  assert.match(preview, /preview_only: true/);
  assert.deepEqual(JSON.parse(JSON.stringify(state)), before);
});

test('archived mutation guard covers source configuration and model controls', () => {
  const model = loadModel();
  for (const action of ['change-source-text','change-source-mode','change-config','change-model','select-file','select-demo-file','refresh-models','select-project-model']) {
    assert.equal(model.isWriteAction(action), true, action);
    assert.equal(model.evaluateActionPrecondition({ projectArchived:true }, action).code, 'PROJECT_ARCHIVED', action);
  }
  assert.equal(model.evaluateActionPrecondition({ projectArchived:true }, 'toggle-storyboard-view').allowed, true);
});

test('archived project is restored only through a confirmed recorded action', () => {
  const model = loadModel();
  const state = model.createInitialState();
  state.project.archived = true;
  state.project.status = 'ARCHIVED';

  const precondition = model.evaluateActionPrecondition({ projectArchived:true }, 'confirm-restore-project');
  assert.equal(precondition.allowed, true);
  const result = model.restoreArchivedProject(state);

  assert.equal(state.project.archived, false);
  assert.equal(state.project.status, 'IN_PROGRESS');
  assert.equal(state.tasks.length, 1);
  assert.equal(state.tasks[0].type, 'MANUAL_ACTION');
  assert.equal(state.tasks[0].status, 'SUCCEEDED');
  assert.equal(state.billingEntries.length, 1);
  assert.equal(state.billingEntries[0].actual, 0);
  assert.equal(state.actionResults.length, 1);
  assert.equal(result.action, 'restore-project');
  assert.equal(result.status, 'SUCCEEDED');
  assert.equal(result.actualPoints, 0);
});

test('Obsidian dependencies resolve artifact ids to navigable paths and aliases', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'SOURCE-001', type:'source', stage:1, title:'小说原文索引', path:'02-小说原文/00-原文索引.md', affects:['SUMMARY-001'], data:{ 原文:'第一版原文' } });
  model.createArtifact(state, { id:'SUMMARY-001', type:'summary', stage:2, title:'故事梗概', path:'03-小说分析/故事梗概.md', dependsOn:['SOURCE-001'], data:{ 完整梗概:'测试梗概' } });

  const source = model.renderMarkdown(state, 'SOURCE-001');
  const summary = model.renderMarkdown(state, 'SUMMARY-001');
  assert.match(source, /\[\[03-小说分析\/故事梗概\|故事梗概\]\]/);
  assert.match(summary, /\[\[02-小说原文\/00-原文索引\|小说原文索引\]\]/);
  assert.doesNotMatch(summary, /\[\[SOURCE-001\]\]/);
});

test('vault export builds real chapter character and version indexes from artifact snapshots', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, {
    id:'SOURCE-001', type:'source', stage:1, title:'小说原文索引', path:'02-小说原文/00-原文索引.md',
    data:{ 原文:'第一版原文', 章节:[{ id:'CH-001', title:'第一章 重生', content:'章节正文一' }] }
  });
  model.updateArtifact(state, 'SOURCE-001', { data:{ 原文:'第二版原文', 章节:[{ id:'CH-001', title:'第一章 重生', content:'章节正文二' }] }, updatedBy:'user' });
  model.createArtifact(state, { id:'CHAR-001', type:'character', stage:2, title:'林野', path:'03-小说分析/人物/CHAR-001-林野.md', data:{ 姓名:'林野' } });
  model.createArtifact(state, { id:'SUMMARY-001', type:'summary', stage:2, title:'故事梗概', path:'03-小说分析/故事梗概.md', data:{ 完整梗概:'梗概初稿' } });
  model.updateArtifact(state, 'SUMMARY-001', { data:{ 完整梗概:'梗概修订稿' }, updatedBy:'user' });

  const files = model.buildVaultFiles(state);
  assert.match(files['02-小说原文/01-章节索引.md'], /\[\[02-小说原文\/章节\/CH-001-第一章 重生\|第一章 重生\]\]/);
  assert.match(files['02-小说原文/章节/CH-001-第一章 重生.md'], /章节正文二/);
  assert.match(files['03-小说分析/人物/00-人物索引.md'], /\[\[03-小说分析\/人物\/CHAR-001-林野\|林野\]\]/);
  assert.match(files['02-小说原文/source_versions/SOURCE-001-V1.md'], /第一版原文/);
  assert.doesNotMatch(files['02-小说原文/source_versions/SOURCE-001-V1.md'], /第二版原文/);
  assert.match(files['03-小说分析/versions/故事梗概-V1.md'], /梗概初稿/);
  assert.match(files['91-版本历史.md'], /SOURCE-001-V1/);
  assert.match(files['91-版本历史.md'], /故事梗概-V1/);
});

test('version history and comparison read actual artifact history snapshots', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'ADAPT-001', type:'adaptation', stage:3, title:'改编方案', path:'04-改编方案.md', data:{ 策略:'真实初稿' } });
  model.updateArtifact(state, 'ADAPT-001', { data:{ 策略:'真实修订稿' }, updatedBy:'user' });

  const history = model.listArtifactVersions(state, 'ADAPT-001');
  assert.equal(history.length, 2);
  assert.equal(history[0].version, 2);
  assert.equal(history[0].data.策略, '真实修订稿');
  assert.equal(history[1].version, 1);
  assert.equal(history[1].data.策略, '真实初稿');
  const comparison = model.compareArtifactVersions(state, 'ADAPT-001', 1, 2);
  assert.equal(comparison.before.data.策略, '真实初稿');
  assert.equal(comparison.after.data.策略, '真实修订稿');
});

test('saving an adaptation rule versions its artifact and records a zero-point action', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'ADAPT-001', type:'adaptation', stage:3, title:'改编方案', path:'04-改编方案.md', data:{ 改编规则:[{ id:'RULE-001', text:'旧规则' }] } });

  const result = model.saveAdaptationRule(state, { rule:{ id:'RULE-002', type:'节奏', priority:'高', text:'新规则' } });

  assert.equal(state.artifacts['ADAPT-001'].version, 2);
  assert.equal(state.artifacts['ADAPT-001'].data.改编规则.at(-1).text, '新规则');
  assert.equal(state.artifacts['ADAPT-001'].history[0].data.改编规则.length, 1);
  assert.equal(state.tasks.length, 1);
  assert.equal(state.tasks[0].type, 'MANUAL_ACTION');
  assert.equal(state.billingEntries[0].actual, 0);
  assert.equal(result.action, 'save-adaptation-rule');
  assert.equal(result.version, 2);
});

test('restoring an artifact snapshot creates a new version task result and keeps history', () => {
  const model = loadModel();
  const state = model.createInitialState();
  model.createArtifact(state, { id:'ADAPT-001', type:'adaptation', stage:3, title:'改编方案', path:'04-改编方案.md', data:{ 策略:'V1 文本' } });
  model.updateArtifact(state, 'ADAPT-001', { data:{ 策略:'V2 文本' }, updatedBy:'user' });

  const result = model.restoreArtifactVersion(state, 'ADAPT-001', 1);

  assert.equal(state.artifacts['ADAPT-001'].version, 3);
  assert.equal(state.artifacts['ADAPT-001'].data.策略, 'V1 文本');
  assert.equal(state.artifacts['ADAPT-001'].history.length, 2);
  assert.equal(state.artifacts['ADAPT-001'].history[1].data.策略, 'V2 文本');
  assert.equal(state.tasks.length, 1);
  assert.equal(state.actionResults.length, 1);
  assert.equal(result.restoredFromVersion, 1);
  assert.equal(result.version, 3);
});

test('retrying a failed generation creates a new settled task and result with model pricing', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const generation = model.startGeneration(state, {
    action:'regenerate', operation:'重新生成改编方案', type:'REGENERATE', stage:3, scope:'改编方案',
    modelId:'ratio-model', estimatedPoints:420, actualPoints:390,
    pricingSnapshot:{ quotaType:0, pricingVersion:'v2' }, artifactId:'ADAPT-001', path:'04-改编方案.md'
  });
  state.tasks[0].status = 'FAILED';

  const result = model.retryTask(state, generation.taskId);

  assert.equal(state.tasks.length, 2);
  assert.equal(state.tasks[0].id, 'task-2');
  assert.equal(state.tasks[0].retryOf, 'task-1');
  assert.equal(state.tasks[0].status, 'SUCCEEDED');
  assert.equal(state.tasks[0].estimatedPoints, 420);
  assert.deepEqual(JSON.parse(JSON.stringify(state.tasks[0].pricingSnapshot)), { quotaType:0, pricingVersion:'v2' });
  assert.equal(state.billingEntries.length, 1);
  assert.equal(state.billingEntries[0].actual, 390);
  assert.equal(result.taskId, 'task-2');
  assert.equal(result.retryOf, 'task-1');
  assert.equal(result.actualPoints, 390);
});

test('simulated file selection creates visible persistent parsing evidence', () => {
  const model = loadModel();
  const state = model.createInitialState();

  const result = model.simulateFileSelection(state, { name:'示例小说.docx', size:2048, type:'application/vnd.openxmlformats-officedocument.wordprocessingml.document' });

  assert.equal(state.source.state, 'PARSED');
  assert.equal(state.source.file.name, '示例小说.docx');
  assert.equal(state.tasks.length, 1);
  assert.equal(state.tasks[0].status, 'SUCCEEDED');
  assert.equal(state.billingEntries[0].actual, 0);
  assert.equal(result.action, 'select-file');
  assert.equal(result.fileName, '示例小说.docx');
});

test('unimplemented script and storyboard episodes open current-episode guidance', () => {
  const model = loadModel();
  const blocked = model.evaluateActionPrecondition({ episodeAvailable:false, requestedEpisode:2 }, 'switch-episode');
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.code, 'EPISODE_NOT_AVAILABLE');
  assert.match(blocked.message, /第 01 集/);
  assert.equal(model.evaluateActionPrecondition({ episodeAvailable:true, requestedEpisode:1 }, 'switch-episode').allowed, true);
});

function loadModel() {
  const match = html.match(/\/\* WORKFLOW_MODEL_START \*\/([\s\S]*?)\/\* WORKFLOW_MODEL_END \*\//);
  assert.ok(match, 'workflow model block must exist');
  const sandbox = {};
  vm.runInNewContext(`${match[1]};globalThis.__model=WorkflowModel`, sandbox);
  return sandbox.__model;
}

function loadPrototypeDom() {
  const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
  assert.ok(script, 'prototype inline controller must exist');
  const documentListeners = new Map();
  const elements = new Map();
  const timers = [];

  class FakeClassList {
    constructor() { this.values = new Set(); }
    add(...values) { values.forEach(value => this.values.add(value)); }
    remove(...values) { values.forEach(value => this.values.delete(value)); }
    contains(value) { return this.values.has(value); }
    toggle(value, force) {
      const enabled = force === undefined ? !this.values.has(value) : Boolean(force);
      if (enabled) this.values.add(value); else this.values.delete(value);
      return enabled;
    }
  }

  class FakeElement {
    constructor(id = '', dataset = {}) {
      this.id = id;
      this.dataset = { ...dataset };
      this.value = '';
      this.innerHTML = '';
      this.textContent = '';
      this.style = {};
      this.attributes = {};
      this.classList = new FakeClassList();
    }
    addEventListener() {}
    querySelector() { return null; }
    querySelectorAll() { return []; }
    setAttribute(name, value) { this.attributes[name] = String(value); }
    getAttribute(name) { return this.attributes[name]; }
    focus() {}
    closest(selector) {
      if (selector === '[data-action]' && this.dataset.action) return this;
      if (selector === '[data-model-scope]' && this.dataset.modelScope) return this;
      return null;
    }
  }

  const fakeDocument = {
    getElementById(id) {
      if (!elements.has(id)) elements.set(id, new FakeElement(id));
      return elements.get(id);
    },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    addEventListener(type, listener) {
      const listeners = documentListeners.get(type) || [];
      listeners.push(listener);
      documentListeners.set(type, listeners);
    }
  };
  const sandbox = {
    document:fakeDocument,
    console,
    fetch:async () => { throw new Error('DOM harness intentionally offline'); },
    setTimeout(callback, delay = 0) { timers.push({ callback, delay }); return timers.length; },
    clearTimeout() {},
    setInterval() { return 1; },
    clearInterval() {}
  };
  vm.runInNewContext(script, sandbox);
  return {
    app:sandbox.WorkflowPrototypeHarness,
    element(id) { return fakeDocument.getElementById(id); },
    click(action, dataset = {}) {
      const target = new FakeElement('', { action, ...dataset });
      for (const listener of documentListeners.get('click') || []) listener({ target });
    },
    runAllTimers() {
      while (timers.length) {
        timers.sort((a,b) => a.delay - b.delay);
        timers.shift().callback();
      }
    }
  };
}

test('DOM click blocks footer transition with guidance and zero writes', () => {
  const dom = loadPrototypeDom();
  assert.ok(dom.app, 'inline controller must expose the executable harness');
  dom.app.setActiveStage(2);
  const state = dom.app.state;
  const before = [state.tasks.length, state.actionResults.length, state.billingEntries.length, Object.keys(state.artifacts).length];

  dom.click('confirm-stage-transition');

  assert.equal(dom.app.getOverlayState().type, 'action-guidance');
  assert.equal(dom.app.getOverlayState().payload.code, 'STAGE_ARTIFACT_REQUIRED');
  assert.deepEqual([state.tasks.length, state.actionResults.length, state.billingEntries.length, Object.keys(state.artifacts).length], before);
});

test('DOM click saves review revision into the referenced block and both artifact histories', () => {
  const dom = loadPrototypeDom();
  assert.ok(dom.app);
  dom.app.setActiveStage(6);
  dom.element('review-edit-text').value = 'DOM 点击写回的真实正文。';

  dom.click('save-review-edit');

  const state = dom.app.state;
  assert.equal(dom.app.ui.scriptState.scenes[0].blocks[0].text, 'DOM 点击写回的真实正文。');
  assert.equal(state.artifacts['EP-001-SCRIPT'].data.场景列表[0].blocks[0].text, 'DOM 点击写回的真实正文。');
  assert.match(state.artifacts['EP-001-SCRIPT'].history[0].data.场景列表[0].blocks[0].text, /风声掠过/);
  assert.equal(state.artifacts['EP-001-REVIEW'].data.修订记录[0].after, 'DOM 点击写回的真实正文。');
  assert.equal(dom.app.getOverlayState().type, 'action-result');
});

test('DOM generation click settles before discard and keeps billing task and result', () => {
  const dom = loadPrototypeDom();
  assert.ok(dom.app);
  dom.app.setActiveStage(3);
  dom.app.seedArtifact({ id:'ADAPT-001', type:'adaptation', stage:3, title:'改编方案', path:'04-改编方案.md', data:{ 策略:'当前版本' } });

  dom.click('confirm-regenerate');
  dom.runAllTimers();
  assert.equal(dom.app.getOverlayState().type, 'generation-result');
  assert.equal(dom.app.state.billingEntries.length, 1);
  assert.equal(dom.app.state.actionResults[0].decision, 'PENDING');
  dom.click('discard-generation-result');

  assert.equal(dom.app.state.tasks.length, 1);
  assert.equal(dom.app.state.tasks[0].status, 'SUCCEEDED');
  assert.equal(dom.app.state.billingEntries.length, 1);
  assert.equal(dom.app.state.actionResults.length, 1);
  assert.equal(dom.app.state.actionResults[0].decision, 'DISCARDED');
  assert.equal(dom.app.state.artifacts['ADAPT-001'].version, 1);
});

test('DOM archive clicks preserve read-only state until confirmed recorded restore', () => {
  const dom = loadPrototypeDom();
  assert.ok(dom.app);
  dom.click('confirm-archive');
  const state = dom.app.state;
  assert.equal(state.project.archived, true);
  assert.match(dom.element('stage-content').innerHTML, /归档只读模式/);
  const counts = [state.tasks.length, state.actionResults.length, state.billingEntries.length];

  dom.click('resume-project');
  assert.equal(dom.app.getOverlayState().payload.code, 'PROJECT_ARCHIVED');
  assert.equal(state.project.archived, true);
  assert.deepEqual([state.tasks.length, state.actionResults.length, state.billingEntries.length], counts);

  dom.click('open-restore-project');
  assert.equal(dom.app.getOverlayState().type, 'restore-project');
  dom.click('confirm-restore-project');
  assert.equal(state.project.archived, false);
  assert.equal(state.tasks.at(-1).status, 'SUCCEEDED');
  assert.equal(state.actionResults.at(-1).action, 'restore-project');
  assert.equal(state.actionResults.at(-1).actualPoints, 0);
});

test('DOM preview and Vault clicks are read-only and render real Obsidian history', () => {
  const dom = loadPrototypeDom();
  assert.ok(dom.app);
  dom.app.setActiveStage(1);
  dom.app.setSource('DOM 预览原文', 'EMPTY');
  dom.click('preview-source-markdown');
  assert.equal(Object.keys(dom.app.state.artifacts).length, 0);
  assert.equal(dom.app.getOverlayState().type, 'markdown-preview');
  assert.match(dom.element('overlay-content').innerHTML, /preview_only: true/);

  const source = dom.app.seedArtifact({ id:'SOURCE-001', type:'source', stage:1, title:'小说原文索引', path:'02-小说原文/00-原文索引.md', affects:['SUMMARY-001'], data:{ 原文:'历史原文' } });
  dom.app.seedArtifact({ id:'SUMMARY-001', type:'summary', stage:2, title:'故事梗概', path:'03-小说分析/故事梗概.md', dependsOn:['SOURCE-001'], data:{ 完整梗概:'梗概' } });
  dom.app.model.updateArtifact(dom.app.state, source.id, { data:{ 原文:'当前原文' }, updatedBy:'user' });
  dom.click('open-vault-preview');
  assert.equal(dom.app.getOverlayState().type, 'vault-preview');
  assert.match(dom.element('overlay-content').innerHTML, /source_versions\/SOURCE-001-V1\.md/);
  assert.match(dom.element('overlay-content').innerHTML, /03-小说分析\/故事梗概\|故事梗概/);
});

test('DOM remaining actions create results and unavailable episodes do not fake switches', () => {
  const dom = loadPrototypeDom();
  assert.ok(dom.app);
  dom.app.setActiveStage(3);
  dom.app.seedArtifact({ id:'ADAPT-001', type:'adaptation', stage:3, title:'改编方案', path:'04-改编方案.md', data:{ 改编规则:[{ id:'RULE-001', text:'旧规则' }] } });
  dom.element('adaptation-rule-text').value = 'DOM 新规则';
  dom.element('adaptation-rule-type').value = '节奏';
  dom.element('adaptation-rule-priority').value = '高';
  dom.click('save-adaptation-rule');
  assert.equal(dom.app.state.artifacts['ADAPT-001'].version, 2);
  assert.equal(dom.app.state.actionResults.at(-1).action, 'save-adaptation-rule');

  dom.click('restore-version', { artifactId:'ADAPT-001', version:'1' });
  assert.equal(dom.app.state.artifacts['ADAPT-001'].version, 3);
  assert.equal(dom.app.state.actionResults.at(-1).action, 'restore-version');

  dom.app.state.tasks.push({ id:'task-failed', type:'REGENERATE', stage:3, scope:'改编方案', status:'FAILED', modelId:'demo-script-pro', estimatedPoints:0, actualPoints:0 });
  dom.click('retry-task', { taskId:'task-failed' });
  assert.equal(dom.app.state.tasks[0].retryOf, 'task-failed');
  assert.equal(dom.app.state.actionResults.at(-1).retryOf, 'task-failed');

  dom.click('open-file-picker');
  assert.equal(dom.app.getOverlayState().type, 'file-picker-guidance');
  dom.click('simulate-file-selection');
  assert.equal(dom.app.state.source.state, 'PARSED');
  assert.equal(dom.app.state.actionResults.at(-1).action, 'select-file');

  dom.app.setActiveStage(5);
  const beforeTasks = dom.app.state.tasks.length;
  dom.click('switch-episode', { episode:'2' });
  assert.equal(dom.app.getOverlayState().payload.code, 'EPISODE_NOT_AVAILABLE');
  assert.equal(dom.app.getActiveStage(), 5);
  assert.equal(dom.app.state.tasks.length, beforeTasks);
});

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

test('generation result visibly separates settlement from acceptance decision', () => {
  assert.match(html, /任务状态：SUCCEEDED/);
  assert.match(html, /结算状态：SETTLED/);
  assert.match(html, /估算来源/);
  assert.match(html, /结果决策/);
  assert.match(html, /成功即结算/);
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
  assert.deepEqual(JSON.parse(JSON.stringify(result.vendors)), []);
  assert.deepEqual(JSON.parse(JSON.stringify(result.groupRatio)), {});
  assert.deepEqual(JSON.parse(JSON.stringify(result.usableGroup)), {});
  assert.deepEqual(JSON.parse(JSON.stringify(result.supportedEndpoint)), {});
  assert.deepEqual(JSON.parse(JSON.stringify(result.autoGroups)), []);
  assert.equal(result.pricingVersion, '');
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
  assert.equal(model.canApproveReview([{ severity:'BLOCKER', status:'OPEN' }]), false);
  assert.equal(model.canApproveReview([{ severity:'HIGH', status:'RESOLVED' }, { severity:'MEDIUM', status:'OPEN' }]), true);
});

test('review and storyboard guidance expose actionable focus states', () => {
  for (const marker of ['focusBlockers = true','focusShots = true','issue.severity === \'BLOCKER\'','guidance-focus']) assert.match(html, new RegExp(marker.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  assert.match(html, /reviewState\.filters\.severity = 'BLOCKING'/);
  assert.match(html, /reviewState\.filters\.status = 'OPEN'/);
  assert.match(html, /document\.querySelector\('\.issue\.guidance-focus'\)/);
  assert.match(html, /document\.querySelector\('\.shot-table tbody tr, \.selectable-card'\)/);
});

test('focused review and storyboard nodes have visible focus styles and a selectable blocking filter', () => {
  for (const selector of ['.issue.guidance-focus', '.shot-table tr.guidance-focus', '.selectable-card.guidance-focus']) {
    const rule = new RegExp(`${selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}[^\\{]*\\{[^}]*?(?:border|background|box-shadow|outline)`, 's');
    assert.match(html, rule, selector);
  }
  assert.match(html, new RegExp('<option value="BLOCKING"[^>]*>高风险与阻断</option>'));
  assert.match(html, /id="review-filter-severity"[\s\S]*?value="BLOCKING"/);
  assert.match(html, /reviewState\.filters\.severity === 'BLOCKING'/);
});

test('archive guard covers all write actions while view toggling stays local-only', () => {
  const model = loadModel();
  for (const action of ['confirm-archive','archive-project','archive-project-complete','save-shot','confirm-project-export']) {
    assert.equal(model.isWriteAction(action), true, action);
    assert.equal(model.evaluateActionPrecondition({ projectArchived:true }, action).code, 'PROJECT_ARCHIVED', action);
  }
  for (const action of ['toggle-storyboard-view','open-review-diff','open-version-diff','preview-source-markdown']) {
    assert.equal(model.isWriteAction(action), false, action);
    assert.equal(model.evaluateActionPrecondition({ projectArchived:true }, action).allowed, true, action);
  }
  assert.match(html, /当前以\$\{storyboardState\.view === 'table' \? '表格' : '卡片'\}视图查看镜头/);
});

test('empty storyboard history opens guidance instead of disabling undo', () => {
  const model = loadModel();
  const result = model.evaluateActionPrecondition({ hasStoryboardHistory:false }, 'undo-storyboard');
  assert.equal(result.code, 'STORYBOARD_HISTORY_REQUIRED');
  assert.doesNotMatch(html, /data-action="undo-storyboard"[^>]*disabled/);
});

test('review storyboard and delivery controls expose complete interactions', () => {
  for (const action of ['apply-review-filter','save-review-edit','open-review-diff','resolve-review-issue','approve-review','select-shot','save-shot','undo-storyboard','archive-project-complete','open-export','create-canvas-project']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});

test('review storyboard and delivery operations never end with toast only', () => {
  for (const id of ['review-filter','review-editor','review-diff','review-approval','shot-editor','continuity-check','archive-result','export-result','canvas-result']) {
    assert.match(html, new RegExp(`overlayFrame\\('${id}'`));
  }
  for (const action of ['focus-review-blockers','focus-storyboard-shots','view-archive-result','view-export-result','view-canvas-result']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});

test('archived projects guide write attempts instead of mutating read-only records', () => {
  const model = loadModel();
  const result = model.evaluateActionPrecondition({ projectArchived:true }, 'save-shot');
  assert.equal(result.allowed, false);
  assert.equal(result.code, 'PROJECT_ARCHIVED');
  assert.equal(result.targetAction, 'open-project-detail');
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

test('prototype keeps exactly eight creative stages and exposes project scene asset infrastructure', () => {
  const model = loadModel();
  assert.equal(model.createInitialState().stages.length, 8);
  for (const action of ['open-scene-assets','create-scene-asset','create-scene-variant','convert-location-to-scene-asset','bind-scene-asset','view-scene-asset-impact']) {
    assert.match(html, new RegExp(`data-action="${action}"`), action);
  }
  for (const label of ['场景母资产','场景变体','剧本场景实例','分镜锁定快照']) assert.match(html, new RegExp(label), label);
  assert.match(html, /场景资产是项目级共享基础设施，不是第九阶段/);
});

test('scene asset demo links a novel location through an immutable storyboard snapshot', () => {
  const model = loadModel();
  const state = model.createInitialState();
  const asset = state.sceneAssets.masters.find(item => item.id === 'SCENE-ASSET-001');
  assert.ok(asset);
  assert.equal(asset.variants.length, 2);
  assert.equal(state.sceneAssets.locations[0].sceneAssetId, asset.id);
  assert.equal(state.sceneAssets.scriptInstances[0].sceneAssetId, asset.id);
  assert.equal(state.sceneAssets.scriptInstances[0].variantId, asset.variants[0].id);
  assert.equal(state.sceneAssets.storyboardSnapshots[0].sceneAssetId, asset.id);
  assert.equal(state.sceneAssets.storyboardSnapshots[0].immutable, true);

  const locked = JSON.parse(JSON.stringify(state.sceneAssets.storyboardSnapshots[0]));
  model.updateSceneAsset(state, asset.id, { description:'出租屋被暴雨浸湿，电路闪烁。' });
  assert.equal(asset.version, 2);
  assert.equal(state.sceneAssets.scriptInstances[0].status, 'STALE');
  assert.equal(state.sceneAssets.storyboardSnapshots[0].status, 'PINNED');
  assert.deepEqual(JSON.parse(JSON.stringify(state.sceneAssets.storyboardSnapshots[0])), locked);

  const impact = model.getSceneAssetImpact(state, asset.id);
  assert.equal(impact.variants.length, 2);
  assert.equal(impact.scriptInstances[0].status, 'STALE');
  assert.equal(impact.storyboardSnapshots[0].status, 'PINNED');
});

test('scene asset actions have executable demo outcomes rather than dead controls', () => {
  const dom = loadPrototypeDom();
  assert.ok(dom.app);
  const expectedSurface = {
    'open-scene-assets':'scene-asset-library',
    'create-scene-asset':'scene-asset-editor',
    'create-scene-variant':'scene-variant-editor',
    'convert-location-to-scene-asset':'location-to-scene-asset',
    'bind-scene-asset':'scene-asset-picker',
    'view-scene-asset-impact':'scene-asset-impact'
  };
  for (const [action, surface] of Object.entries(expectedSurface)) {
    dom.click(action);
    assert.equal(dom.app.getOverlayState().type, surface, action);
  }
});

test('scene asset Obsidian export contains a stable index master variants and pinned references', () => {
  const model = loadModel();
  const files = model.buildVaultFiles(model.createInitialState());
  assert.ok(files['04-场景资产/00-场景资产索引.md']);
  assert.ok(files['04-场景资产/SCENE-ASSET-001-林野出租屋.md']);
  assert.match(files['04-场景资产/00-场景资产索引.md'], /SCENE-ASSET-001/);
  assert.match(files['04-场景资产/SCENE-ASSET-001-林野出租屋.md'], /SCENE-VARIANT-001/);
  assert.match(files['04-场景资产/SCENE-ASSET-001-林野出租屋.md'], /SCRIPT-SCENE-001/);
  assert.match(files['04-场景资产/SCENE-ASSET-001-林野出租屋.md'], /SNAPSHOT-001/);
  assert.match(html, /静态演示仅在当前页面内模拟保存，不会写入外部后端/);
});
