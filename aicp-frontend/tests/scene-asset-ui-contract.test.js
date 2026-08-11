import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import {
  filterSceneAssets,
  impactConsumers,
  persistSceneAssetActionResult,
  preservedImpactConsumers,
  readSceneAssetActionResults,
  sceneConsumerStatus
} from '../src/views/content-project/workbench/sceneAssetUiModel.js'
import { normalizeSceneAsset } from '../src/views/content-project/workbench/sceneAssetModel.js'
import { useSceneAssets } from '../src/views/content-project/workbench/useSceneAssets.js'
import { ref } from 'vue'

const contentProjectDir = fileURLToPath(new URL('../src/views/content-project/', import.meta.url))
const read = path => readFileSync(`${contentProjectDir}${path}`, 'utf8')

test('scene asset library exposes searchable filters and complete asset cards', () => {
  const source = read('components/SceneAssetLibrary.vue')
  for (const marker of ['new-scene-asset', 'create-from-location', 'view-impact']) {
    assert.match(source, new RegExp(`data-action=["']${marker}["']`))
  }
  for (const field of ['keyword', 'spaceType', 'reusability', 'status', 'referenced']) {
    assert.match(source, new RegExp(`filters\\.${field}`))
  }
  for (const field of ['stableId', 'currentVersionNo', 'variants', 'episodeReferences', 'syncStatus']) {
    assert.match(source, new RegExp(field))
  }
  assert.match(source, /cover-fallback/)
})

test('scene asset detail tabs and lifecycle actions match the approved contract', () => {
  const source = read('components/SceneAssetDetailDrawer.vue')
  for (const tab of ['basic', 'visual', 'variants', 'continuity', 'references-versions']) {
    assert.match(source, new RegExp(`name=["']${tab}["']`))
  }
  for (const action of ['restore-version', 'replace-reference']) {
    assert.match(source, new RegExp(`data-action=["']${action}["']`))
  }
  for (const label of ['停用', '创建替代并迁移引用', '查看差异', '保留旧版', '升级新版']) assert.match(source, new RegExp(label))
  assert.match(source, /impact-confirmation/)
  assert.doesNotMatch(source, /data-action=["'][^"']+["'][^>]*\sdisabled(?:\s|=|>)/)
})

test('scene asset library search covers name location landmark and tag with all approved filters', () => {
  const assets = [
    { id: 1, name: '青桥出租屋', status: 'ACTIVE', referenced: true, master: { worldLocationRef: '青桥城中村', landmarks: ['红色楼梯'], tags: ['主场景'], spaceType: 'INTERIOR', reusability: 'HIGH' } },
    { id: 2, name: '黑塔广场', status: 'ARCHIVED', referenced: false, master: { worldLocationRef: '北城', landmarks: ['钟塔'], tags: ['外景'], spaceType: 'EXTERIOR', reusability: 'LOW' } }
  ]
  assert.deepEqual(filterSceneAssets(assets, { keyword: '楼梯' }).map(item => item.id), [1])
  assert.deepEqual(filterSceneAssets(assets, { keyword: '北城' }).map(item => item.id), [2])
  assert.deepEqual(filterSceneAssets(assets, { keyword: '主场景' }).map(item => item.id), [1])
  assert.deepEqual(filterSceneAssets(assets, { spaceType: 'INTERIOR', reusability: 'HIGH', status: 'ACTIVE', referenced: true }).map(item => item.id), [1])
  assert.deepEqual(filterSceneAssets(assets, { referenced: false }).map(item => item.id), [2])
})

test('semantic changes mark unlocked consumers STALE while locked storyboard shots stay pinned', () => {
  const projected = impactConsumers({ downstreamStatus: 'STALE', affectedScopes: ['visual'] }, {
    references: [
      { type: 'SCRIPT_SCENE', id: 11, versionId: 3, locked: false },
      { type: 'STORYBOARD_SHOT', id: 21, versionId: 7, locked: false },
      { type: 'STORYBOARD_SHOT', id: 22, versionId: 7, locked: true }
    ]
  })
  assert.deepEqual(projected.map(item => [item.id, item.downstreamStatus, item.actions]), [
    [11, 'STALE', ['view-diff', 'keep-old', 'upgrade-new']],
    [21, 'STALE', ['view-diff', 'keep-old', 'upgrade-new']],
    [22, 'PINNED', ['view-diff']]
  ])
})

test('semantic impact does not stale a consumer already bound to the new current version', () => {
  const projected = impactConsumers({ downstreamStatus: 'STALE', affectedScopes: ['visual'] }, {
    references: [
      { type: 'SCRIPT_SCENE', id: 1, syncStatus: 'NEEDS_SYNC' },
      { type: 'SCRIPT_SCENE', id: 2, syncStatus: 'CURRENT' }
    ]
  })
  assert.deepEqual(projected.map(item => item.downstreamStatus), ['STALE', 'CURRENT'])
})

test('management-only changes keep consumers current', () => {
  const projected = impactConsumers({ downstreamStatus: 'CURRENT', affectedScopes: [] }, {
    references: [{ type: 'SCRIPT_SCENE', id: 11, syncStatus: 'APPLIED' }]
  })
  assert.equal(projected[0].downstreamStatus, 'CURRENT')
  assert.deepEqual(projected[0].actions, ['view-diff'])
})

test('consumer status comes from the selected persistent action result instead of aggregate stale count', () => {
  const managementResult = {
    assetId: 3,
    change: { downstreamStatus: 'CURRENT', affectedScopes: [] },
    affectedConsumers: [{ type: 'SCRIPT_SCENE', id: 11, downstreamStatus: 'CURRENT' }]
  }
  assert.equal(sceneConsumerStatus({ assetId: 3, type: 'SCRIPT_SCENE', id: 11, result: managementResult, fallback: 'STALE' }), 'CURRENT')
  assert.equal(sceneConsumerStatus({ assetId: 3, type: 'STORYBOARD_SHOT', id: 22, locked: true, result: managementResult, fallback: 'STALE' }), 'PINNED')
})

test('reloaded DTO keeps one exact script scene current without aggregate asset fallback', () => {
  const asset = normalizeSceneAsset({ id: 3, sync_status: 'CURRENT', reference_count: 2 })
  const result = normalizeSceneAsset({
    assetId: 3,
    change: { downstreamStatus: 'STALE', affectedScopes: ['visual'] },
    affectedConsumers: [
      { type: 'SCRIPT_SCENE', consumer_id: 9001, consumer_key: '9001', downstreamStatus: 'STALE' },
      { type: 'SCRIPT_SCENE', consumer_id: 9002, consumer_key: '9002', downstreamStatus: 'CURRENT' }
    ]
  })
  assert.equal(sceneConsumerStatus({ assetId: asset.id, type: 'SCRIPT_SCENE', id: 9001, result, fallback: asset.syncStatus }), 'STALE')
  assert.equal(sceneConsumerStatus({ assetId: asset.id, type: 'SCRIPT_SCENE', id: 9002, result, fallback: asset.syncStatus }), 'CURRENT')
  assert.equal(sceneConsumerStatus({ assetId: asset.id, type: 'SCRIPT_SCENE', id: 9002, fallback: asset.syncStatus }), 'CURRENT')
})

test('script scene impact matches the stable consumer key used by the script stage', () => {
  const result = normalizeSceneAsset({
    assetId: 3,
    affectedConsumers: [
      { type: 'SCRIPT_SCENE', consumer_id: 9001, consumer_key: 'EP-007-SCENE-001', downstream_status: 'STALE' }
    ]
  })
  assert.equal(sceneConsumerStatus({
    assetId: 3,
    type: 'SCRIPT_SCENE',
    id: 'local-render-id',
    consumerKey: 'EP-007-SCENE-001',
    result,
    fallback: 'CURRENT'
  }), 'STALE')
  assert.match(read('stages/ScriptBodyStage.vue'), /type:'SCRIPT_SCENE',id:scene\.id,consumerKey:String\(scene\.id\)/)
})

test('impactful action results persist by project and remain revisitable', () => {
  const storage = memoryStorage()
  const result = persistSceneAssetActionResult(9, {
    id: 'RESULT-1', action: 'restore-version', assetId: 3,
    affectedConsumers: [{ type: 'SCRIPT_SCENE', id: 11, downstreamStatus: 'STALE' }]
  }, storage)
  assert.equal(result.id, 'RESULT-1')
  assert.deepEqual(readSceneAssetActionResults(9, storage), [result])
  assert.deepEqual(readSceneAssetActionResults(10, storage), [])
})

test('novel analysis script body and storyboard open one authoritative asset flow and show sync state', () => {
  const source = [
    read('stages/NovelAnalysisStage.vue'),
    read('stages/ScriptBodyStage.vue'),
    read('stages/TextStoryboardStage.vue')
  ].join('\n')
  for (const marker of ['open-scene-asset', 'open-scene-action-result', 'sceneAssetStatus']) assert.match(source, new RegExp(marker))
})

test('drawer and stages consume persistent change evidence and do not infer all consumers from staleReferences', () => {
  const source = [
    read('components/SceneAssetDetailDrawer.vue'),
    read('stages/NovelAnalysisStage.vue'),
    read('stages/ScriptBodyStage.vue'),
    read('stages/TextStoryboardStage.vue')
  ].join('\n')
  assert.match(source, /currentResult(?:\.value)?\?\.change/)
  assert.match(source, /currentResult(?:\.value)?\?\.affectedConsumers/)
  assert.match(source, /sceneConsumerStatus/)
})

test('revisiting a stored result loads and selects its asset before opening the drawer', () => {
  const source = read('components/SceneAssetLibrary.vue')
  assert.match(source, /await sceneAssets\.loadAsset\(result\.assetId\)/)
  assert.match(source, /if \(!loaded\.ok\)/)
})

test('visual editor preserves materials palette and lighting as separate fields', () => {
  const source = read('components/SceneAssetDetailDrawer.vue')
  for (const field of ['materialsText', 'paletteText', 'lighting']) {
    assert.match(source, new RegExp(`draft\\.${field}`))
  }
  assert.match(source, /materials:\s*fromLines\(draft\.materialsText\)/)
  assert.match(source, /palette:\s*fromLines\(draft\.paletteText\)/)
  assert.match(source, /lighting:\s*draft\.lighting/)
})

test('basic editor persists searchable tags as management-only metadata', () => {
  const source = read('components/SceneAssetDetailDrawer.vue')
  assert.match(source, /draft\.tagsText/)
  assert.match(source, /tags:\s*fromLines\(draft\.tagsText\)/)
})

test('referenced lifecycle uses reversible disable and keeps archive separate', () => {
  const composable = read('workbench/useSceneAssets.js')
  const drawer = read('components/SceneAssetDetailDrawer.vue')
  const library = read('components/SceneAssetLibrary.vue')
  assert.match(composable, /client\.disable/)
  assert.match(composable, /client\.activate/)
  assert.match(composable, /action:\s*'archive-scene-asset'/)
  assert.match(library, /'archive-scene-asset':\s*'归档场景资产'/)
  assert.match(drawer, /sceneAssets\.disable/)
  assert.match(read('components/SceneAssetPicker.vue'), /\['ARCHIVED',\s*'DISABLED'\]/)
})

test('disable can preserve loaded authoritative consumers before selection clears impact', () => {
  const references = [{ type: 'SCRIPT_SCENE', consumerId: 9001, consumerKey: '9001', syncStatus: 'CURRENT' }]
  const captured = preservedImpactConsumers({ assetId: 3, references })
  const clearedImpact = null
  assert.deepEqual(captured, references)
  assert.equal(clearedImpact, null)
  const composable = read('workbench/useSceneAssets.js')
  assert.match(composable, /const affectedConsumers = preservedImpactConsumers\(refreshedImpact\.impact\)[\s\S]*await adapter[\s\S]*affectedConsumers:/)
})

test('composable disable loads authoritative impact before mutation and preserves consumers', async () => {
  const calls = []
  const references = [{ type: 'SCRIPT_SCENE', consumerId: 9001, consumerKey: 'EP-007-SCENE-001', syncStatus: 'CURRENT' }]
  const api = {
    list: async () => [{ id: 3, status: 'ACTIVE', current_version_id: 8 }],
    impact: async (_projectId, assetId) => { calls.push(`impact:${assetId}`); return { assetId, references } },
    disable: async (_projectId, assetId) => { calls.push(`disable:${assetId}`); return { id: assetId, status: 'DISABLED', current_version_id: 8 } }
  }
  const sceneAssets = useSceneAssets(9, { api, resultStorage: memoryStorage() })
  await sceneAssets.load()
  sceneAssets.selectAsset(sceneAssets.assets.value[0])

  const result = await sceneAssets.disable(3)

  assert.deepEqual(calls, ['impact:3', 'disable:3'])
  assert.equal(result.ok, true)
  assert.deepEqual(result.data.affectedConsumers, references)
})

test('composable disable guides on impact load failure and never fakes mutation success', async () => {
  let disableCalls = 0
  const api = {
    list: async () => [{ id: 3, status: 'ACTIVE', current_version_id: 8 }],
    impact: async () => { throw Object.assign(new Error('影响范围加载失败'), { code: 'SCENE_ASSET_IMPACT_FAILED' }) },
    disable: async () => { disableCalls += 1; return { id: 3, status: 'DISABLED' } }
  }
  const sceneAssets = useSceneAssets(9, { api, resultStorage: memoryStorage() })
  await sceneAssets.load()

  const result = await sceneAssets.disable(3)

  assert.equal(result.ok, false)
  assert.equal(result.code, 'SCENE_ASSET_IMPACT_FAILED')
  assert.match(result.message, /影响范围加载失败/)
  assert.equal(disableCalls, 0)
})

test('scene asset load ignores a stale response after project switch', async () => {
  const pending = new Map()
  const projectId = ref(9)
  const api = {
    list: id => new Promise(resolve => pending.set(id, resolve))
  }
  const sceneAssets = useSceneAssets(projectId, { api, resultStorage: memoryStorage() })
  const oldLoad = sceneAssets.load()
  projectId.value = 10
  const newLoad = sceneAssets.load()
  pending.get(10)([{ id: 10, name: '新项目场景', current_version_id: 1 }])
  await newLoad
  pending.get(9)([{ id: 9, name: '旧项目场景', current_version_id: 1 }])

  const staleResult = await oldLoad

  assert.equal(staleResult.ok, false)
  assert.equal(staleResult.code, 'STALE_PROJECT_RESPONSE')
  assert.deepEqual(sceneAssets.assets.value.map(asset => asset.id), [10])
})

test('scene asset detail load cannot select an asset after project switch', async () => {
  let resolveDetail
  const projectId = ref(9)
  const sceneAssets = useSceneAssets(projectId, {
    api: { get: () => new Promise(resolve => { resolveDetail = resolve }) }, resultStorage: memoryStorage()
  })
  const oldLoad = sceneAssets.loadAsset(3)
  projectId.value = 10
  sceneAssets.reset()
  resolveDetail({ id: 3, name: '旧项目场景', current_version_id: 8 })

  const result = await oldLoad

  assert.equal(result.code, 'STALE_PROJECT_RESPONSE')
  assert.equal(sceneAssets.selectedAsset.value, null)
  assert.deepEqual(sceneAssets.assets.value, [])
})

test('project operation token invalidates an old response even after switching back to the same project id', async () => {
  let resolveDetail
  const projectId = ref(9)
  const sceneAssets = useSceneAssets(projectId, {
    api: { get: () => new Promise(resolve => { resolveDetail = resolve }) }, resultStorage: memoryStorage()
  })
  const oldLoad = sceneAssets.loadAsset(3)
  projectId.value = 10
  projectId.value = 9
  resolveDetail({ id: 3, name: '过期场景', current_version_id: 8 })

  const result = await oldLoad

  assert.equal(result.code, 'STALE_PROJECT_RESPONSE')
  assert.equal(sceneAssets.selectedAsset.value, null)
})

test('successful stale mutation cannot write assets selection results or cache into the new project', async () => {
  let resolveCreate
  const projectId = ref(9)
  const storage = memoryStorage()
  const sceneAssets = useSceneAssets(projectId, {
    api: { create: () => new Promise(resolve => { resolveCreate = resolve }) }, resultStorage: storage
  })
  sceneAssets.state.value = 'ready'
  const oldCreate = sceneAssets.create({ name: '旧场景', spaceType: 'INTERIOR', reusability: 'PRIMARY', realityType: 'REALISTIC' })
  projectId.value = 10
  sceneAssets.reset()
  resolveCreate({ id: 3, name: '旧场景', current_version_id: 8 })

  const result = await oldCreate

  assert.equal(result.code, 'STALE_PROJECT_RESPONSE')
  assert.deepEqual(sceneAssets.assets.value, [])
  assert.equal(sceneAssets.selectedAsset.value, null)
  assert.equal(sceneAssets.actionResult.value, null)
  assert.equal(storage.length, 0)
})

test('stale mutation rejection cannot publish the old project error into new state', async () => {
  let rejectUpdate
  const projectId = ref(9)
  const sceneAssets = useSceneAssets(projectId, {
    api: { update: () => new Promise((_resolve, reject) => { rejectUpdate = reject }) }, resultStorage: memoryStorage()
  })
  sceneAssets.state.value = 'ready'
  sceneAssets.assets.value = [{ id: 3, name: '旧场景', currentVersionId: 8 }]
  const oldUpdate = sceneAssets.update(3, { name: '旧项目修改' })
  projectId.value = 10
  sceneAssets.reset()
  rejectUpdate(Object.assign(new Error('旧项目网络错误'), { code: 'OLD_PROJECT_ERROR' }))

  const result = await oldUpdate

  assert.equal(result.code, 'STALE_PROJECT_RESPONSE')
  assert.equal(sceneAssets.actionResult.value, null)
  assert.equal(sceneAssets.state.value, 'loading')
})

function memoryStorage() {
  const values = new Map()
  return {
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
    get length() { return values.size },
    key: index => [...values.keys()][index] ?? null
  }
}
