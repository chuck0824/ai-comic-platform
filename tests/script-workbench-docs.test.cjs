const test = require('node:test')
const assert = require('node:assert/strict')
const { readFileSync, existsSync } = require('node:fs')
const { resolve } = require('node:path')

const root = resolve(__dirname, '..')
const requiredDocs = [
  '漫剧视频创作平台_PRD.md',
  '剧本创作页面逻辑盘点与补充清单.md',
  'docs/superpowers/specs/2026-08-06-script-workbench-obsidian-model-billing-design.md',
  'docs/superpowers/specs/2026-07-02-script-creation-creative-bible-design.md',
  'docs/superpowers/specs/2026-07-01-script-creation-warehouse-flow-design.md',
  'docs/剧本创作模块_场景资产与八阶段融合说明.md'
]
const stages = ['创作设置', '小说上传', '小说分析', '改编方案', '结构化文字剧本', '剧本正文', '审核修订', '文字分镜']
const sceneLayers = ['场景母资产', '场景变体', '剧本场景实例', '分镜场景快照']

function read(relativePath) {
  const path = resolve(root, relativePath)
  assert.equal(existsSync(path), true, `missing required document: ${relativePath}`)
  return readFileSync(path, 'utf8')
}

function assertOrdered(text, terms, label) {
  let cursor = -1
  for (const term of terms) {
    const index = text.indexOf(term, cursor + 1)
    assert.ok(index > cursor, `${label} must contain ordered term: ${term}`)
    cursor = index
  }
}

test('all product docs use the same eight-stage order and four-layer scene model', () => {
  for (const relativePath of requiredDocs) {
    const text = read(relativePath)
    assertOrdered(text, stages, relativePath)
    assertOrdered(text, sceneLayers, relativePath)
    assert.match(text, /\/script-gen[^\n]*四入口[\s\S]*\/script-gen\/:projectId\/workspace/,
      `${relativePath} must distinguish launchpad and native workbench routes`)
  }
})

test('documents distinguish native persistence from the static acceptance demo', () => {
  for (const relativePath of requiredDocs) {
    const text = read(relativePath)
    assert.match(text, /生产原生|原生工作台/)
    assert.match(text, /静态(?:验收)?演示[\s\S]{0,120}(?:当前页|不.*后端|非生产)/)
  }
})

test('billing documentation states real-model point parity and explicit demo fallback', () => {
  const text = read('docs/superpowers/specs/2026-08-06-script-workbench-obsidian-model-billing-design.md')
  assert.match(text, /3001[\s\S]*真实可用模型[\s\S]*内置演示模型/)
  assert.match(text, /仅.*(?:明确|显式).*演示模型[\s\S]*0\s*积分/)
  assert.match(text, /3001.*现有.*积分规则/)
  assert.match(text, /预估[\s\S]*预冻结|预冻结[\s\S]*预估/)
  assert.match(text, /实际结算[\s\S]*退回差额/)
  assert.match(text, /幂等[\s\S]*不重复扣费|不重复扣费[\s\S]*幂等/)
})

test('generation candidate lifecycle is server-first and auditable', () => {
  const text = read('docs/剧本创作模块_场景资产与八阶段融合说明.md')
  for (const status of ['pending', 'processing', 'failed', 'cancelled', 'completed']) {
    assert.match(text, new RegExp(`\\b${status}\\b`))
  }
  for (const term of ['candidate', 'accepted', 'discarded', 'result_version_id', 'actual_credits']) {
    assert.match(text, new RegExp(term))
  }
  assert.match(text, /服务端优先[\s\S]*采纳[\s\S]*放弃/)
  assert.match(text, /候选隔离/)
  assert.match(text, /API[^\n]*pending[^\n]*processing[^\n]*completed[^\n]*failed[^\n]*cancelled/)
  assert.match(text, /UI[^\n]*pending[^\n]*queued[^\n]*processing[^\n]*running/)
})

test('documents separate implemented compatibility behavior from deferred 3001 accounting', () => {
  for (const relativePath of requiredDocs) {
    const text = read(relativePath)
    assert.match(text, /当前实现[\s\S]{0,800}目标合同|目标合同[\s\S]{0,800}当前实现/,
      `${relativePath} must separate implemented behavior from target contracts`)
  }
  const billing = read('docs/superpowers/specs/2026-08-06-script-workbench-obsidian-model-billing-design.md')
  assert.match(billing, /\/api\/v1\/ai\/models/)
  assert.match(billing, /\/api\/v1\/credits\/estimate/)
  assert.match(billing, /本地[^\n]*(?:registry|模型注册表)/)
  assert.match(billing, /(?:预计|估算)[^\n]*不可[^\n]*账务证据|不可[^\n]*账务证据[^\n]*(?:预计|估算)/)
  assert.match(billing, /P0[^\n]*(?:延期|未落地)[^\n]*(?:3001|预冻结|结算)/)
  assert.match(billing, /3001[^\n]*(?:权威模型目录|权威目录)/)
  assert.match(billing, /预冻结[^\n]*结算[^\n]*退(?:款|回)/)
})

test('scene variant, snapshot and completion contracts match production DTOs', () => {
  const text = read('docs/剧本创作模块_场景资产与八阶段融合说明.md')
  assert.match(text, /新增[^\n]*场景变体[^\n]*(?:append|追加)[^\n]*资产版本/)
  assert.match(text, /语义等价[^\n]*(?:CURRENT|不[^\n]*STALE)/)
  assert.match(text, /scene_variant_version/)
  assert.doesNotMatch(text, /scene_variant_version_id/)
  for (const key of ['master', 'variant', 'sceneOverride', 'continuityRules', 'finalPromptFragment', 'fingerprint']) {
    assert.match(text, new RegExp(`"${key}"`), `snapshot example must include ${key}`)
  }
  assert.match(text, /完成并归档[^\n]*(?:锁定分镜|storyboard lock)[^\n]*本地八阶段完成/)
  assert.match(text, /不[^\n]*归档项目|不会[^\n]*项目[^\n]*ARCHIVED/)
})

test('implementation references use exact component, migration and sourced error facts', () => {
  const text = read('docs/剧本创作模块_场景资产与八阶段融合说明.md')
  assert.match(text, /SceneAssetDetailDrawer\.vue/)
  assert.doesNotMatch(text, /`SceneAssetDrawer\.vue`/)
  for (const column of ['scene_asset_id', 'scene_asset_version_id', 'scene_variant_id', 'scene_variant_version', 'scene_asset_snapshot']) {
    assert.match(text, new RegExp(column))
  }
  assert.match(text, /V16[^\n]*(?:两个索引|2 个索引)/)
  assert.match(text, /错误码[^\n]*来源|来源[^\n]*错误码/)
  for (const source of ['ErrorCode.java', 'scriptWorkbenchModel.js', 'downstreamStageModel.js', 'generationResultPersistence.js']) {
    assert.match(text, new RegExp(source.replace('.', '\\.')))
  }
})

test('fusion guide covers V17, APIs, Obsidian graph, stale policy and rollback', () => {
  const text = read('docs/剧本创作模块_场景资产与八阶段融合说明.md')
  for (const term of [
    'V17', 'ACTIVE', 'DISABLED', 'ARCHIVED', 'CURRENT', 'STALE', 'PINNED',
    '04-场景资产/00-场景资产索引.md', 'immutable', 'superseded',
    '/api/v1/content-projects/{projectId}/scene-assets',
    '/api/v1/generation-jobs/{jobId}/accept',
    '部署', '回滚', '错误码'
  ]) assert.match(text, new RegExp(term))
})

test('visible stage actions have end-to-end traceability', () => {
  const text = read('docs/剧本创作模块_场景资产与八阶段融合说明.md')
  for (const heading of ['操作', '前置条件', '成功结果', '失败结果', 'Markdown 产物', '失效下游', 'API', '验收测试']) {
    assert.match(text, new RegExp(heading))
  }
  for (const action of [
    '编辑梗概', '新增事件', '人物详情', '编辑世界观', '确认改编方案', '选择高压开场',
    '新增改编规则', '打开单集结构', '新增节拍', '重新生成节拍', '续写选中段落',
    '增强冲突', '精简对白', '改写语气', '检查角色一致性', '新增场景', '新增正文块',
    '运行正文检查', '导出正文', '筛选问题', '保存局部修订', '对比修订前后',
    '审核通过本集', '新增镜头', '拆分镜头', '合并镜头', '切换卡片/表格',
    '连续性检查', '完成并归档', '配置导图', '创建画布项目', '重新生成当前产物'
  ]) assert.match(text, new RegExp(action), `missing action traceability: ${action}`)
})

test('page inventory includes all non-happy-path states', () => {
  const text = read('剧本创作页面逻辑盘点与补充清单.md')
  for (const term of ['加载态', '空状态', '错误态', 'ARCHIVED', 'DISABLED', 'STALE', 'PINNED', '权限不足', '重试']) {
    assert.match(text, new RegExp(term))
  }
})
