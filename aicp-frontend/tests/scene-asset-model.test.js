import test from 'node:test'
import assert from 'node:assert/strict'
import {
  normalizeSceneAsset,
  validateSceneAssetDraft,
  mergeSceneAssetVariant,
  classifySceneAssetChange
} from '../src/views/content-project/workbench/sceneAssetModel.js'
import {
  resolveSceneAssetProjectId,
  sceneAssetCacheKey,
  readSceneAssetListCache,
  writeSuccessfulSceneAssetListCache,
  invalidateSceneAssetListCache,
  prepareSceneAssetMutation
} from '../src/views/content-project/workbench/sceneAssetState.js'

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

test('variant binding never treats display version as an AssetVersion primary key', () => {
  const incomplete = mergeSceneAssetVariant(
    { id: 7, version: 2, name: '出租屋' },
    { id: 'VAR-001', version: 1, name: '停电' }
  )
  assert.equal(incomplete.masterVersion, 2)
  assert.equal(incomplete.bindingPayload.sceneAssetVersionId, null)
  assert.equal(incomplete.bindingState.submittable, false)
  assert.equal(incomplete.bindingState.fieldErrors.sceneAssetVersionId, '请选择包含资产版本主键的场景版本')

  const complete = mergeSceneAssetVariant(
    { id: 7, currentVersionId: 99, currentVersionNo: 2, name: '出租屋' },
    { id: 'VAR-001', version: 1, name: '停电' }
  )
  assert.equal(complete.masterVersion, 2)
  assert.equal(complete.bindingPayload.sceneAssetVersionId, 99)
  assert.equal(complete.bindingState.submittable, true)
})

test('variant rename and version-only changes remain CURRENT', () => {
  const change = classifySceneAssetChange(
    { variants: [{ id: 'VAR-001', name: '白天', version: 1, tags: ['主用'], notes: '旧备注' }] },
    { variants: [{ id: 'VAR-001', name: '晨间', version: 2, tags: ['常驻'], notes: '新备注' }] }
  )
  assert.deepEqual(change, { visualChange: false, downstreamStatus: 'CURRENT', affectedScopes: [] })
})

test('variant lighting event and prompt deltas stale their semantic scopes', () => {
  const change = classifySceneAssetChange(
    { variants: [{ id: 'VAR-001', lightingDelta: '自然光', eventState: '平静', prompts: '干净室内' }] },
    { variants: [{ id: 'VAR-001', lightingDelta: '应急灯', eventState: '停电', prompts: '闪烁应急灯' }] }
  )
  assert.equal(change.visualChange, true)
  assert.equal(change.downstreamStatus, 'STALE')
  assert.deepEqual(change.affectedScopes, ['visual', 'continuity'])
})

test('adding an unreferenced variant keeps downstream scenes CURRENT', () => {
  const change = classifySceneAssetChange(
    { variants: [] },
    { variants: [{ id: 'VAR-002', lightingDelta: '烛光', eventState: '夜间' }] }
  )
  assert.deepEqual(change, { visualChange: false, downstreamStatus: 'CURRENT', affectedScopes: [] })
})

test('removing an existing variant stales continuity bindings', () => {
  const change = classifySceneAssetChange(
    { variants: [{ id: 'VAR-001', lightingDelta: '自然光', eventState: '停电' }] },
    { variants: [] }
  )
  assert.equal(change.visualChange, false)
  assert.equal(change.downstreamStatus, 'STALE')
  assert.deepEqual(change.affectedScopes, ['continuity'])
})

test('scene asset project resolver follows ref-like route changes for API and cache keys', () => {
  const projectId = { value: 21 }
  assert.equal(resolveSceneAssetProjectId(projectId), 21)
  assert.match(sceneAssetCacheKey(projectId, { status: 'ACTIVE' }), /^scene_assets:21:/)
  projectId.value = 22
  assert.equal(resolveSceneAssetProjectId(projectId), 22)
  assert.match(sceneAssetCacheKey(projectId, { status: 'ACTIVE' }), /^scene_assets:22:/)
})

test('scene asset cache distinguishes an absent snapshot from a successful empty list and invalidates after mutation', () => {
  const storage = memoryStorage()
  const filters = { status: 'ACTIVE' }
  assert.deepEqual(readSceneAssetListCache(21, filters, storage), { found: false, items: [] })
  writeSuccessfulSceneAssetListCache(21, filters, [], storage)
  assert.deepEqual(readSceneAssetListCache(21, filters, storage), { found: true, items: [] })
  invalidateSceneAssetListCache(21, storage)
  assert.deepEqual(readSceneAssetListCache(21, filters, storage), { found: false, items: [] })
})

test('scene asset cache safely degrades when the global localStorage getter throws', () => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    get() { throw new Error('SecurityError') }
  })
  try {
    assert.deepEqual(readSceneAssetListCache(21, { status: 'ACTIVE' }), { found: false, items: [] })
    assert.doesNotThrow(() => writeSuccessfulSceneAssetListCache(21, { status: 'ACTIVE' }, []))
    assert.doesNotThrow(() => invalidateSceneAssetListCache(21))
  } finally {
    if (descriptor) Object.defineProperty(globalThis, 'localStorage', descriptor)
    else delete globalThis.localStorage
  }
})

test('scene asset mutation cache invalidation ignores broken storage operations', () => {
  const storage = {
    get length() { throw new Error('storage unavailable') },
    key() { throw new Error('storage unavailable') },
    removeItem() { throw new Error('storage unavailable') }
  }
  assert.doesNotThrow(() => invalidateSceneAssetListCache(21, storage))
})

test('scene asset mutation guard runs before draft validation for archived and degraded state', () => {
  let validated = false
  const validate = () => {
    validated = true
    return { name: '场景名称不能为空' }
  }
  assert.equal(prepareSceneAssetMutation({ projectArchived: true, state: 'ready', draft: {}, validate }).code, 'PROJECT_ARCHIVED')
  assert.equal(validated, false)
  assert.equal(prepareSceneAssetMutation({ projectArchived: false, state: 'readonly', draft: {}, validate }).code, 'DEGRADED_READ_ONLY')
  assert.equal(validated, false)
  assert.equal(prepareSceneAssetMutation({ projectArchived: false, state: 'ready', draft: {}, validate }).code, 'VALIDATION_FAILED')
  assert.equal(validated, true)
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
