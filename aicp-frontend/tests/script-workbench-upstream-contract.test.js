import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import {
  addAdaptationRule,
  confirmAdaptationPlan,
  convertLocationToSceneAsset,
  countChineseCharacters,
  createAdaptationState,
  createAnalysisState,
  loadCreationModels,
  persistHookSelection,
  removeAdaptationRule,
  runArtifactRegeneration,
  saveAnalysisSection,
  updateAdaptationRule,
  validateCreationSettings,
  validatePastedNovel
} from '../src/views/content-project/workbench/upstreamStageModel.js'

const stagesDir = fileURLToPath(new URL('../src/views/content-project/stages/', import.meta.url))

function readStageSources(names) {
  return names.map(name => readFileSync(`${stagesDir}${name}.vue`, 'utf8')).join('\n')
}

test('upstream stages expose every approved editor and action', () => {
  const sources = readStageSources(['CreationSettingsStage', 'NovelUploadStage', 'NovelAnalysisStage', 'AdaptationStage'])
  for (const marker of [
    'creation-type', 'genre-selector', 'model-selector', 'paste-char-counter',
    'edit-synopsis', 'add-event', 'edit-chapter-outline', 'edit-worldview', 'character-detail',
    'convert-location-to-scene-asset', 'select-high-pressure-hook', 'add-adaptation-rule',
    'confirm-adaptation', 'regenerate-current-artifact'
  ]) assert.match(sources, new RegExp(`data-action=["']${marker}["']`))
  assert.doesNotMatch(sources, /data-action=["'][^"']+["'][^>]*\sdisabled(?:\s|=|>)/)
})

test('pasted novel enforces the 2000 Chinese-character product rule', () => {
  assert.equal(countChineseCharacters('中文 A1𠀀'), 3)
  assert.deepEqual(validatePastedNovel('中'.repeat(2000)), {
    allowed: true, code: null, chineseCount: 2000, limit: 2000, excess: 0, message: ''
  })
  assert.deepEqual(validatePastedNovel('中'.repeat(2003)), {
    allowed: false,
    code: 'NOVEL_TEXT_TOO_LONG',
    chineseCount: 2003,
    limit: 2000,
    excess: 3,
    message: '已超出 3 个汉字，请删减后重试，或改用文件上传。'
  })
})

test('creation settings use real usable models and only fall back to explicit free demos', async () => {
  const real = await loadCreationModels(async () => ({
    data: { models: [
      { model_id: 'qwen-plus', model_name: 'Qwen Plus', status: 'available', source: '3001', point_rule: '按实际 Token 结算', estimated_points: 8 },
      { model_id: 'offline', model_name: 'Offline', status: 'unavailable' }
    ] }
  }))
  assert.deepEqual(real, {
    mode: 'remote',
    models: [{ id: 'qwen-plus', name: 'Qwen Plus', status: 'available', source: '3001', sourceBadge: '3001 平台', pointRule: '按实际 Token 结算', estimatedPoints: 8, demo: false }],
    guidance: null
  })

  const fallback = await loadCreationModels(async () => { throw new Error('connection refused') })
  assert.equal(fallback.mode, 'demo')
  assert.equal(fallback.models.every(model => model.demo && model.estimatedPoints === 0), true)
  assert.match(fallback.guidance.message, /内置演示模型/)

  const invalidRealSettings = validateCreationSettings({
    creationType: 'novel_adaptation', genre: '悬疑', tone: '紧张', audience: '成年', episodeCount: 12,
    episodeDuration: 90, adaptationStrength: 'balanced', outputFormat: 'vertical_short_drama',
    model: { id: 'qwen-plus', demo: false }, estimatedPoints: 0
  })
  assert.equal(invalidRealSettings.allowed, false)
  assert.equal(invalidRealSettings.code, 'POINT_ESTIMATE_REQUIRED')
})

test('analysis saves validate fields and record artifact version plus impact only after persistence', async () => {
  const state = createAnalysisState({ synopsis: '旧梗概' })
  const invalid = await saveAnalysisSection(state, 'synopsis', '  ', async () => ({ persisted: true }))
  assert.equal(invalid.code, 'ANALYSIS_FIELD_REQUIRED')
  assert.equal(state.synopsis, '旧梗概')

  const failed = await saveAnalysisSection(state, 'synopsis', '新梗概', async () => ({ persisted: false, message: '保存冲突' }))
  assert.equal(failed.code, 'ANALYSIS_PERSISTENCE_FAILED')
  assert.equal(state.synopsis, '旧梗概')

  const saved = await saveAnalysisSection(state, 'synopsis', '新梗概', async payload => {
    assert.deepEqual(payload, { section: 'synopsis', value: '新梗概', previousVersion: 0 })
    return { persisted: true, version: 1, artifactPath: '03-小说分析/故事梗概.md', impact: { stale: ['改编方案'] } }
  })
  assert.equal(state.synopsis, '新梗概')
  assert.deepEqual(saved, {
    ok: true, section: 'synopsis', version: 1, artifactPath: '03-小说分析/故事梗概.md', impact: { stale: ['改编方案'] }
  })
  assert.deepEqual(state.artifactVersions, [saved])
})

test('worldview save versions its locations together so scene conversion never uses an unsaved place', async () => {
  const state = createAnalysisState({ locations: [{ id: 'old', name: '旧地点', spaceType: 'INTERIOR' }] })
  const worldview = {
    worldType: '近未来城市', timeSetting: '2040 年', powerSystem: '信用等级', rules: '等级决定通行权', factions: ['上城公司'],
    locations: [{ id: 'WORLD-LOC-009', name: '下城换乘站', spaceType: 'INTERIOR', description: '地下换乘枢纽' }]
  }
  await saveAnalysisSection(state, 'worldview', worldview, async payload => {
    assert.deepEqual(payload.value.locations, worldview.locations)
    return { persisted: true, version: 1, artifactPath: '03-小说分析/世界观.md', impact: { stale: ['场景资产检查'] } }
  })
  assert.deepEqual(state.locations, worldview.locations)
  assert.equal(Object.hasOwn(state.worldview, 'locations'), false)
})

test('location conversion creates once then opens the stable scene asset detail', async () => {
  const state = createAnalysisState({ locations: [{ id: 'WORLD-LOC-003', name: '青桥城中村出租屋', spaceType: 'INTERIOR' }] })
  let creates = 0
  const opened = []
  const adapter = {
    createFromLocation: async payload => {
      creates += 1
      assert.deepEqual(payload, { worldLocationRef: 'WORLD-LOC-003', name: '青桥城中村出租屋', spaceType: 'INTERIOR' })
      return { ok: true, data: { id: 7, stableId: 'SCENE-ASSET-001', currentVersionId: 12, currentVersionNo: 2, status: 'ACTIVE' } }
    },
    openAsset: asset => opened.push(asset.stableId)
  }

  const created = await convertLocationToSceneAsset(state, 'WORLD-LOC-003', adapter)
  const reused = await convertLocationToSceneAsset(state, 'WORLD-LOC-003', adapter)
  assert.equal(created.created, true)
  assert.equal(reused.created, false)
  assert.equal(creates, 1)
  assert.deepEqual(opened, ['SCENE-ASSET-001'])
  assert.deepEqual(state.locations[0].sceneAsset, {
    id: 7, stableId: 'SCENE-ASSET-001', versionId: 12, versionNo: 2, status: 'ACTIVE'
  })
})

test('adaptation persists hook before confirmation and keeps draft rules editable', async () => {
  const state = createAdaptationState({ hooks: [{ id: 'hook-1', title: '债主堵门' }] })
  const failed = await persistHookSelection(state, 'hook-1', async () => ({ persisted: false, message: '网络错误' }))
  assert.equal(failed.code, 'HOOK_PERSISTENCE_FAILED')
  assert.equal(state.selectedHookId, null)

  const selected = await persistHookSelection(state, 'hook-1', async hookId => ({ persisted: hookId === 'hook-1', version: 2 }))
  assert.equal(selected.ok, true)
  assert.equal(state.selectedHookId, 'hook-1')
  assert.equal(state.hookVersion, 2)

  const added = addAdaptationRule(state, { title: '保留女主动机', instruction: '不能改为被动角色' })
  assert.equal(added.id, 'RULE-001')
  assert.equal(updateAdaptationRule(state, added.id, { instruction: '保留主动选择' }).instruction, '保留主动选择')
  assert.equal(removeAdaptationRule(state, added.id).ok, true)

  const confirmed = await confirmAdaptationPlan(state, {
    creationType: 'novel_adaptation', genre: '悬疑', tone: '紧张', audience: '成年', episodeCount: 12,
    episodeDuration: 90, adaptationStrength: 'balanced', outputFormat: 'vertical_short_drama',
    model: { id: 'demo-text', demo: true }, estimatedPoints: 0
  }, async payload => ({ persisted: payload.selectedHookId === 'hook-1', version: 3, impact: { stale: [] } }))
  assert.equal(confirmed.ok, true)
  assert.equal(state.confirmed, true)
  assert.equal(state.version, 3)

  assert.equal(addAdaptationRule(state, { title: '过晚', instruction: '不应添加' }).code, 'ADAPTATION_CONFIRMED')
})

test('adaptation regeneration delegates to the shared task/progress/result flow', async () => {
  const calls = []
  const workbench = {
    beginGeneration(input) { calls.push(['begin', input]); return { id: 'task-1' } },
    updateGenerationProgress(id, update) { calls.push(['progress', id, update]) },
    finishGeneration(id, outcome) { calls.push(['finish', id, outcome]); return { taskId: id, ...outcome } }
  }
  const result = await runArtifactRegeneration({
    workbench,
    input: { model: { id: 'qwen-plus' }, estimatedPoints: 9, subtask: '重新生成改编方案' },
    execute: async task => {
      assert.equal(task.id, 'task-1')
      return { actualPoints: 7, artifact: { path: '04-改编方案/改编方案.md', version: 4, impact: '分集结构待更新' } }
    }
  })
  assert.equal(result.status, 'completed')
  assert.deepEqual(calls.map(call => call[0]), ['begin', 'progress', 'progress', 'finish'])
  assert.equal(calls.at(-1)[2].actualPoints, 7)
})
