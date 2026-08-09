import { computed, reactive, ref } from 'vue'
import { classifySceneAssetChange, normalizeSceneAsset, validateSceneAssetDraft } from './sceneAssetModel.js'
import { normalizeSceneAssetMarkdown } from './sceneAssetMarkdown.js'
import {
  filterSceneAssets,
  impactConsumers,
  persistSceneAssetActionResult,
  preservedImpactConsumers,
  readSceneAssetActionResults,
  sceneAssetIsReferenced
} from './sceneAssetUiModel.js'
import {
  invalidateSceneAssetListCache,
  prepareSceneAssetMutation,
  readSceneAssetListCache,
  resolveSceneAssetProjectId,
  sceneAssetMutationGuard,
  writeSuccessfulSceneAssetListCache
} from './sceneAssetState.js'

function failure(code, message) {
  return { ok: false, code, message }
}

function lazyDefaultApi() {
  return new Proxy({}, {
    get(_target, operation) {
      return async (...args) => {
        const { sceneAssetApi } = await import('@/api/sceneAsset')
        return sceneAssetApi[operation](...args)
      }
    }
  })
}

/** Project-scoped scene asset state; degraded cache is intentionally read-only. */
export function useSceneAssets(projectId, {
  isProjectArchived = false,
  api = null,
  resultStorage,
  consumerAdapter = null
} = {}) {
  const client = api || lazyDefaultApi()
  const state = ref('loading')
  const assets = ref([])
  const filters = reactive({ keyword: '', spaceType: '', reusability: '', status: '', referenced: undefined })
  const selectedAsset = ref(null)
  const selectedVersion = ref(null)
  const impact = ref(null)
  const markdown = ref(null)
  const actionResult = ref(null)
  const actionResults = ref(readSceneAssetActionResults(resolveSceneAssetProjectId(projectId), resultStorage))
  const projectArchived = ref(false)
  const activeProjectId = () => resolveSceneAssetProjectId(projectId)
  const isArchived = () => projectArchived.value || Boolean(typeof isProjectArchived === 'function'
    ? isProjectArchived()
    : isProjectArchived?.value ?? isProjectArchived)
  const readOnly = computed(() => state.value === 'readonly' || isArchived())

  const filteredAssets = computed(() => filterSceneAssets(assets.value, filters))

  function selectAsset(asset) {
    selectedAsset.value = asset ? normalizeSceneAsset(asset) : null
    selectedVersion.value = selectedAsset.value?.currentVersionId ?? null
    impact.value = null
    return selectedAsset.value
  }

  function setProjectArchived(value = true) {
    projectArchived.value = Boolean(value)
    if (projectArchived.value) state.value = 'readonly'
  }

  async function load() {
    state.value = 'loading'
    const resolvedProjectId = activeProjectId()
    const filterSnapshot = { ...filters }
    try {
      const response = await client.list(resolvedProjectId, filterSnapshot)
      const list = Array.isArray(response) ? response : response.items ?? []
      assets.value = list.map(normalizeSceneAsset)
      writeSuccessfulSceneAssetListCache(resolvedProjectId, filterSnapshot, assets.value)
      state.value = isArchived() ? 'readonly' : (assets.value.length ? 'ready' : 'empty')
      actionResults.value = readSceneAssetActionResults(resolvedProjectId, resultStorage)
      return { ok: true, items: assets.value }
    } catch (error) {
      const cached = readSceneAssetListCache(resolvedProjectId, filterSnapshot)
      if (cached.found) {
        assets.value = cached.items.map(normalizeSceneAsset)
        state.value = 'readonly'
        return failure('DEGRADED_READ_ONLY', '网络不可用，正在显示最近成功缓存，当前仅可查看')
      }
      state.value = 'error'
      return failure(error?.code || 'SCENE_ASSET_LOAD_FAILED', error?.message || '场景资产加载失败')
    }
  }

  async function loadAsset(assetId) {
    try {
      const asset = normalizeSceneAsset(await client.get(activeProjectId(), assetId))
      selectAsset(asset)
      return { ok: true, asset }
    } catch (error) {
      return failure(error?.code || 'SCENE_ASSET_LOAD_FAILED', error?.message || '场景资产加载失败')
    }
  }

  async function mutate(operation) {
    const guarded = sceneAssetMutationGuard({ projectArchived: isArchived(), state: state.value })
    if (guarded) return guarded
    try {
      const result = await operation()
      actionResult.value = { ok: true, data: result?.result || result }
      return actionResult.value
    } catch (error) {
      actionResult.value = failure(error?.code || 'SCENE_ASSET_ACTION_FAILED', error?.message || '场景资产操作失败')
      return actionResult.value
    }
  }

  function recordResult(result) {
    const resolvedProjectId = activeProjectId()
    const normalized = {
      id: result.id || `SCENE-RESULT-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      createdAt: result.createdAt || new Date().toISOString(),
      ...result
    }
    persistSceneAssetActionResult(resolvedProjectId, normalized, resultStorage)
    actionResults.value = readSceneAssetActionResults(resolvedProjectId, resultStorage)
    if (!actionResults.value.some(item => item.id === normalized.id)) actionResults.value = [normalized, ...actionResults.value]
    actionResult.value = { ok: true, data: normalized }
    return normalized
  }

  async function impactAfterPersist(assetId) {
    const loaded = await loadImpact(assetId)
    if (loaded.ok) return { impact: loaded.impact, impactRefresh: { ok: true } }
    return {
      impact: { assetId, references: [], staleReferences: 0, lockedReferences: 0, status: 'UNAVAILABLE' },
      impactRefresh: {
        ok: false,
        code: loaded.code,
        message: `资产已持久化，但影响范围刷新失败：${loaded.message}`,
        targetAction: 'retry_scene_asset_impact'
      }
    }
  }

  async function create(draft) {
    const prepared = prepareSceneAssetMutation({
      projectArchived: isArchived(), state: state.value, draft, validate: validateSceneAssetDraft
    })
    if (prepared) return prepared
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const asset = normalizeSceneAsset(await client.create(resolvedProjectId, draft))
      assets.value = [asset, ...assets.value]
      invalidateSceneAssetListCache(resolvedProjectId)
      selectAsset(asset)
      state.value = 'ready'
      return asset
    })
  }

  async function update(assetId, draft) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const before = assets.value.find(item => item.id === assetId) || selectedAsset.value || {}
      const asset = normalizeSceneAsset(await client.update(resolvedProjectId, assetId, draft))
      replaceAsset(asset)
      invalidateSceneAssetListCache(resolvedProjectId)
      const change = classifySceneAssetChange(before, asset)
      const { impact: refreshedImpact, impactRefresh } = await impactAfterPersist(assetId)
      const result = recordResult({
        action: 'update-scene-asset', assetId, versionId: asset.currentVersionId,
        change, impact: refreshedImpact, impactRefresh, affectedConsumers: impactConsumers(change, refreshedImpact)
      })
      return { asset, result }
    })
  }

  async function createFromLocation(draft) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const asset = normalizeSceneAsset(await client.createFromLocation(resolvedProjectId, draft))
      replaceAsset(asset, true)
      invalidateSceneAssetListCache(resolvedProjectId)
      return asset
    })
  }

  async function createVariant(assetId, draft) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const before = assets.value.find(item => item.id === assetId) || selectedAsset.value || {}
      const asset = normalizeSceneAsset(await client.createVariant(resolvedProjectId, assetId, draft))
      replaceAsset(asset)
      invalidateSceneAssetListCache(resolvedProjectId)
      const change = classifySceneAssetChange(before, asset)
      const { impact: refreshedImpact, impactRefresh } = await impactAfterPersist(assetId)
      return { asset, result: recordResult({ action: 'create-variant', assetId, change, impact: refreshedImpact, impactRefresh, affectedConsumers: impactConsumers(change, refreshedImpact) }) }
    })
  }

  async function updateVariant(assetId, variantId, draft) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const before = assets.value.find(item => item.id === assetId) || selectedAsset.value || {}
      const asset = normalizeSceneAsset(await client.updateVariant(resolvedProjectId, assetId, variantId, draft))
      replaceAsset(asset)
      invalidateSceneAssetListCache(resolvedProjectId)
      const change = classifySceneAssetChange(before, asset)
      const { impact: refreshedImpact, impactRefresh } = await impactAfterPersist(assetId)
      return { asset, result: recordResult({ action: 'update-variant', assetId, variantId, change, impact: refreshedImpact, impactRefresh, affectedConsumers: impactConsumers(change, refreshedImpact) }) }
    })
  }

  async function restore(assetId, versionId, draft = {}) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const before = assets.value.find(item => item.id === assetId) || selectedAsset.value || {}
      const version = await client.restore(resolvedProjectId, assetId, versionId, draft)
      invalidateSceneAssetListCache(resolvedProjectId)
      const reloaded = await loadAsset(assetId)
      const change = reloaded.ok
        ? classifySceneAssetChange(before, selectedAsset.value || {})
        : { visualChange: true, downstreamStatus: 'STALE', affectedScopes: ['visual', 'continuity'] }
      const { impact: refreshedImpact, impactRefresh } = await impactAfterPersist(assetId)
      const result = recordResult({
        action: 'restore-version', assetId, restoredVersionId: versionId,
        version, change, assetRefresh: reloaded.ok ? { ok: true } : reloaded,
        impact: refreshedImpact, impactRefresh, affectedConsumers: impactConsumers(change, refreshedImpact)
      })
      return { version, asset: selectedAsset.value, result }
    })
  }

  async function archive(assetId) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      await client.archive(resolvedProjectId, assetId)
      const archived = assets.value.find(asset => asset.id === assetId)
      assets.value = assets.value.map(asset => asset.id === assetId ? { ...asset, status: 'ARCHIVED' } : asset)
      if (selectedAsset.value?.id === assetId && archived) selectAsset({ ...archived, status: 'ARCHIVED' })
      invalidateSceneAssetListCache(resolvedProjectId)
      return recordResult({ action: 'archive-scene-asset', assetId, affectedConsumers: [] })
    })
  }

  async function disable(assetId) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const loadedImpact = await loadImpact(assetId)
      if (!loadedImpact.ok) throw Object.assign(new Error(loadedImpact.message), { code: loadedImpact.code })
      const affectedConsumers = preservedImpactConsumers(loadedImpact.impact)
      const asset = normalizeSceneAsset(await client.disable(resolvedProjectId, assetId))
      replaceAsset(asset)
      invalidateSceneAssetListCache(resolvedProjectId)
      return { asset, result: recordResult({ action: 'disable-scene-asset', assetId, affectedConsumers }) }
    })
  }

  async function activate(assetId) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const asset = normalizeSceneAsset(await client.activate(resolvedProjectId, assetId))
      replaceAsset(asset)
      invalidateSceneAssetListCache(resolvedProjectId)
      return { asset, result: recordResult({ action: 'activate-scene-asset', assetId, affectedConsumers: [] }) }
    })
  }

  async function loadImpact(assetId = selectedAsset.value?.id) {
    try {
      if (assetId == null) return failure('SCENE_ASSET_REQUIRED', '请先选择场景资产')
      impact.value = await client.impact(activeProjectId(), assetId)
      return { ok: true, impact: impact.value }
    } catch (error) {
      return failure(error?.code || 'SCENE_ASSET_IMPACT_FAILED', error?.message || '影响范围加载失败')
    }
  }

  async function loadMarkdown(assetId = selectedAsset.value?.id) {
    try {
      if (assetId == null) return failure('SCENE_ASSET_REQUIRED', '请先选择场景资产')
      markdown.value = normalizeSceneAssetMarkdown(await client.markdown(activeProjectId(), assetId))
      return { ok: true, markdown: markdown.value }
    } catch (error) {
      return failure(error?.code || 'SCENE_ASSET_MARKDOWN_FAILED', error?.message || 'Markdown 预览加载失败')
    }
  }

  function replaceAsset(asset, addWhenMissing = false) {
    const index = assets.value.findIndex(item => item.id === asset.id)
    assets.value = index < 0 ? (addWhenMissing ? [asset, ...assets.value] : assets.value) : assets.value.map(item => item.id === asset.id ? asset : item)
    if (selectedAsset.value?.id === asset.id) selectAsset(asset)
  }

  function openActionResult(resultOrId) {
    const result = typeof resultOrId === 'object'
      ? resultOrId
      : actionResults.value.find(item => item.id === resultOrId)
    if (!result) return failure('SCENE_ACTION_RESULT_NOT_FOUND', '未找到该次场景资产操作结果')
    actionResult.value = { ok: true, data: result }
    return actionResult.value
  }

  async function replaceReferences(assetId, replacement, adapter = consumerAdapter?.replaceReferences) {
    if (typeof adapter !== 'function') return failure('REFERENCE_REPLACEMENT_UNAVAILABLE', '请先连接剧本/分镜引用迁移服务')
    const guarded = sceneAssetMutationGuard({ projectArchived: isArchived(), state: state.value })
    if (guarded) return guarded
    const refreshedImpact = await loadImpact(assetId)
    if (!refreshedImpact.ok) return refreshedImpact
    const affectedConsumers = preservedImpactConsumers(refreshedImpact.impact)
    try {
      const response = await adapter({ assetId, replacement, impact: refreshedImpact.impact })
      if (!response?.persisted) return failure(response?.code || 'REFERENCE_REPLACEMENT_FAILED', response?.message || '引用迁移未持久化，请重试')
      const result = recordResult({
        action: 'replace-reference', assetId, replacement,
        affectedConsumers: response.affectedConsumers?.length ? response.affectedConsumers : affectedConsumers, response
      })
      return { ok: true, data: result }
    } catch (error) {
      return failure(error?.code || 'REFERENCE_REPLACEMENT_FAILED', error?.message || '引用迁移失败')
    }
  }

  async function resolveConsumer(reference, decision, adapter = consumerAdapter?.resolveConsumer) {
    const allowed = ['view-diff', 'keep-old', 'upgrade-new']
    if (!allowed.includes(decision)) return failure('CONSUMER_DECISION_INVALID', '请选择查看差异、保留旧版或升级新版')
    if (decision === 'view-diff' && typeof consumerAdapter?.viewDiff === 'function') {
      return { ok: true, data: await consumerAdapter.viewDiff(reference) }
    }
    if (typeof adapter !== 'function') return failure('CONSUMER_RESOLUTION_UNAVAILABLE', '请先连接剧本/分镜版本处理服务')
    try {
      const response = await adapter({ reference, decision })
      if (!response?.persisted) return failure(response?.code || 'CONSUMER_RESOLUTION_FAILED', response?.message || '处理结果未持久化')
      return { ok: true, data: recordResult({ action: decision, reference, response, affectedConsumers: [reference] }) }
    } catch (error) {
      return failure(error?.code || 'CONSUMER_RESOLUTION_FAILED', error?.message || '下游版本处理失败')
    }
  }

  const referencedSelectedAsset = computed(() => sceneAssetIsReferenced(selectedAsset.value || {}))

  return {
    state, assets, filteredAssets, filters, selectedAsset, selectedVersion, impact, markdown, actionResult,
    actionResults, referencedSelectedAsset, readOnly,
    load, loadAsset, selectAsset, setProjectArchived, create, update, createFromLocation, createVariant,
    updateVariant, restore, disable, activate, archive, loadImpact, loadMarkdown, openActionResult, replaceReferences, resolveConsumer
  }
}
