import test from 'node:test'
import assert from 'node:assert/strict'
import {
  normalizeSceneAsset,
  validateSceneAssetDraft,
  mergeSceneAssetVariant,
  classifySceneAssetChange
} from '../src/views/content-project/workbench/sceneAssetModel.js'

test('scene asset normalizes the API envelope into camelCase state', () => {
  const asset = normalizeSceneAsset({
    id: 7,
    current_version_id: 12,
    current_version_no: 2,
    content_project_id: 3,
    master: { fixed_props: ['旧木桌'], continuity_rules: ['门保持关闭'] },
    variants: [{ id: 'VAR-001', lighting_delta: '应急灯' }]
  })

  assert.equal(asset.currentVersionId, 12)
  assert.equal(asset.currentVersionNo, 2)
  assert.equal(asset.contentProjectId, 3)
  assert.deepEqual(asset.master.fixedProps, ['旧木桌'])
  assert.deepEqual(asset.master.continuityRules, ['门保持关闭'])
  assert.equal(asset.variants[0].lightingDelta, '应急灯')
})

test('scene asset draft validation returns field messages for missing scene identity', () => {
  assert.deepEqual(validateSceneAssetDraft({
    name: ' ', spaceType: '', reusability: 'PRIMARY', realityType: null
  }), {
    name: '场景名称不能为空',
    spaceType: '空间类型不能为空',
    realityType: '现实类型不能为空'
  })
})

test('variant keeps deltas while resolving a production snapshot', () => {
  const resolved = mergeSceneAssetVariant(
    { id: 7, version: 2, name: '出租屋', path: '04-场景资产/SCENE-007-出租屋.md', lighting: '自然光', palette: ['灰'], fixedProps: ['旧木桌'], continuityRules: ['门保持关闭'] },
    { id: 'VAR-001', version: 1, name: '停电', lightingDelta: '仅应急灯', eventState: '停电' }
  )
  assert.equal(resolved.masterVersion, 2)
  assert.equal(resolved.variantVersion, 1)
  assert.equal(resolved.lighting, '仅应急灯')
  assert.deepEqual(resolved.fixedProps, ['旧木桌'])
  assert.deepEqual(resolved.bindingSnapshot, {
    master: { id: 7, name: '出租屋', version: 2, path: '04-场景资产/SCENE-007-出租屋.md', fixedProps: ['旧木桌'] },
    variant: { id: 'VAR-001', name: '停电', version: 1 },
    sceneOverride: { lighting: '仅应急灯', eventState: '停电' },
    continuityRules: ['门保持关闭']
  })
})

test('management-only changes do not stale downstream scenes', () => {
  const change = classifySceneAssetChange(
    { name: '出租屋', tags: ['主场景'], lighting: '自然光' },
    { name: '青桥出租屋', tags: ['主场景', '常驻'], lighting: '自然光' }
  )
  assert.equal(change.visualChange, false)
  assert.equal(change.downstreamStatus, 'CURRENT')
  assert.deepEqual(change.affectedScopes, [])
})

test('scene asset visual and continuity edits stale only their affected downstream scopes', () => {
  const change = classifySceneAssetChange(
    { lighting: '自然光', continuityRules: ['门保持关闭'] },
    { lighting: '夜景霓虹', continuityRules: ['门被撞开'] }
  )
  assert.equal(change.visualChange, true)
  assert.equal(change.downstreamStatus, 'STALE')
  assert.deepEqual(change.affectedScopes, ['visual', 'continuity'])
})

test('scene asset continuity-only edits stale downstream without claiming a visual change', () => {
  const change = classifySceneAssetChange(
    { continuityRules: ['门保持关闭'] },
    { continuityRules: ['门被撞开'] }
  )
  assert.equal(change.visualChange, false)
  assert.equal(change.downstreamStatus, 'STALE')
  assert.deepEqual(change.affectedScopes, ['continuity'])
})

test('scene asset nested API master visual changes stale downstream scenes', () => {
  const change = classifySceneAssetChange(
    { name: '出租屋', master: { lighting: '自然光', continuityRules: ['门保持关闭'] } },
    { name: '出租屋', master: { lighting: '夜景霓虹', continuityRules: ['门保持关闭'] } }
  )
  assert.equal(change.visualChange, true)
  assert.equal(change.downstreamStatus, 'STALE')
  assert.deepEqual(change.affectedScopes, ['visual'])
})
