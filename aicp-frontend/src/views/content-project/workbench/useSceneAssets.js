import { computed, reactive, ref, watch } from 'vue'
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
  let operationGeneration = 0
  const activeProjectId = () => resolveSceneAssetProjectId(projectId)
  watch(activeProjectId, () => { operationGeneration += 1 }, { flush: 'sync' })
  const captureOperation = () => ({ projectId: activeProjectId(), generation: operationGeneration })
  const operationIsCurrent = context => context.generation === operationGeneration && context.projectId === activeProjectId()
  const staleResponse = () => failure('STALE_PROJECT_RESPONSE', '已忽略上一项目的场景资产响应')
  const requireCurrent = context => {
    if (!operationIsCurrent(context)) throw Object.assign(new Error(staleResponse().message), { code: 'STALE_PROJECT_RESPONSE' })
  }
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
    const context = captureOperation()
    state.value = 'loading'
    const filterSnapshot = { ...filters }
    try {
      const response = await client.list(context.projectId, filterSnapshot)
      if (!operationIsCurrent(context)) return staleResponse()
      const list = Array.isArray(response) ? response : response.items ?? []
      assets.value = list.map(normalizeSceneAsset)
      writeSuccessfulSceneAssetListCache(context.projectId, filterSnapshot, assets.value)
      state.value = isArchived() ? 'readonly' : (assets.value.length ? 'ready' : 'empty')
      actionResults.value = readSceneAssetActionResults(context.projectId, resultStorage)
      return { ok: true, items: assets.value }
    } catch (error) {
      if (!operationIsCurrent(context)) return staleResponse()
      const cached = readSceneAssetListCache(context.projectId, filterSnapshot)
      if (cached.found) {
        assets.value = cached.items.map(normalizeSceneAsset)
        state.value = 'readonly'
        return failure('DEGRADED_READ_ONLY', '网络不可用，正在显示最近成功缓存，当前仅可查看')
      }
      state.value = 'error'
      return failure(error?.code || 'SCENE_ASSET_LOAD_FAILED', error?.message || '场景资产加载失败')
    }
  }

  function reset() {
    operationGeneration += 1
    state.value = 'loading'
    assets.value = []
    selectedAsset.value = null
    selectedVersion.value = null
    impact.value = null
    markdown.value = null
    actionResult.value = null
    actionResults.value = []
    projectArchived.value = false
  }

  async function loadAsset(assetId, context = captureOperation()) {
    try {
      const asset = normalizeSceneAsset(await client.get(context.projectId, assetId))
      if (!operationIsCurrent(context)) return staleResponse()
      selectAsset(asset)
      return { ok: true, asset }
    } catch (error) {
      if (!operationIsCurrent(context)) return staleResponse()
      return failure(error?.code || 'SCENE_ASSET_LOAD_FAILED', error?.message || '场景资产加载失败')
    }
  }

  async function mutate(context, operation) {
    const guarded = sceneAssetMutationGuard({ projectArchived: isArchived(), state: state.value })
    if (guarded) return guarded
    try {
      const result = await operation(context)
      if (!operationIsCurrent(context)) return staleResponse()
      actionResult.value = { ok: true, data: result?.result || result }
      return actionResult.value
    } catch (error) {
      if (!operationIsCurrent(context)) return staleResponse()
      actionResult.value = failure(error?.code || 'SCENE_ASSET_ACTION_FAILED', error?.message || '场景资产操作失败')
      return actionResult.value
    }
  }

  function recordResult(result, context = captureOperation()) {
    requireCurrent(context)
    const normalized = {
      id: result.id || `SCENE-RESULT-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      createdAt: result.createdAt || new Date().toISOString(),
      ...result
    }
    persistSceneAssetActionResult(context.projectId, normalized, resultStorage)
    actionResults.value = readSceneAssetActionResults(context.projectId, resultStorage)
    if (!actionResults.value.some(item => item.id === normalized.id)) actionResults.value = [normalized, ...actionResults.value]
    actionResult.value = { ok: true, data: normalized }
    return normalized
  }

  async function impactAfterPersist(assetId, context) {
    const loaded = await loadImpact(assetId, context)
    requireCurrent(context)
    if (loaded.code === 'STALE_PROJECT_RESPONSE') requireCurrent(context)
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
    const context = captureOperation()
    return mutate(context, async () => {
      const asset = normalizeSceneAsset(await client.create(context.projectId, draft))
      requireCurrent(context)
      assets.value = [asset, ...assets.value]
      invalidateSceneAssetListCache(context.projectId)
      selectAsset(asset)
      state.value = 'ready'
      return asset
    })
  }

  async function update(assetId, draft) {
    const context = captureOperation()
    return mutate(context, async () => {
      const before = assets.value.find(item => item.id === assetId) || selectedAsset.value || {}
      const asset = normalizeSceneAsset(await client.update(context.projectId, assetId, draft))
      requireCurrent(context)
      replaceAsset(asset)
      invalidateSceneAssetListCache(context.projectId)
      const change = classifySceneAssetChange(before, asset)
      const { impact: refreshedImpact, impactRefresh } = await impactAfterPersist(assetId, context)
      requireCurrent(context)
      const result = recordResult({
        action: 'update-scene-asset', assetId, versionId: asset.currentVersionId,
        change, impact: refreshedImpact, impactRefresh, affectedConsumers: impactConsumers(change, refreshedImpact)
      }, context)
      return { asset, result }
    })
  }

  async function createFromLocation(draft) {
    const context = captureOperation()
    return mutate(context, async () => {
      const asset = normalizeSceneAsset(await client.createFromLocation(context.projectId, draft))
      requireCurrent(context)
      replaceAsset(asset, true)
      invalidateSceneAssetListCache(context.projectId)
      return asset
    })
  }

  async function createVariant(assetId, draft) {
    const context = captureOperation()
    return mutate(context, async () => {
      const before = assets.value.find(item => item.id === assetId) || selectedAsset.value || {}
      const asset = normalizeSceneAsset(await client.createVariant(context.projectId, assetId, draft))
      requireCurrent(context)
      replaceAsset(asset)
      invalidateSceneAssetListCache(context.projectId)
      const change = classifySceneAssetChange(before, asset)
      const { impact: refreshedImpact, impactRefresh } = await impactAfterPersist(assetId, context)
      requireCurrent(context)
      return { asset, result: recordResult({ action: 'create-variant', assetId, change, impact: refreshedImpact, impactRefresh, affectedConsumers: impactConsumers(change, refreshedImpact) }, context) }
    })
  }

  async function updateVariant(assetId, variantId, draft) {
    const context = captureOperation()
    return mutate(context, async () => {
      const before = assets.value.find(item => item.id === assetId) || selectedAsset.value || {}
      const asset = normalizeSceneAsset(await client.updateVariant(context.projectId, assetId, variantId, draft))
      requireCurrent(context)
      replaceAsset(asset)
      invalidateSceneAssetListCache(context.projectId)
      const change = classifySceneAssetChange(before, asset)
      const { impact: refreshedImpact, impactRefresh } = await impactAfterPersist(assetId, context)
      requireCurrent(context)
      return { asset, result: recordResult({ action: 'update-variant', assetId, variantId, change, impact: refreshedImpact, impactRefresh, affectedConsumers: impactConsumers(change, refreshedImpact) }, context) }
    })
  }

  async function restore(assetId, versionId, draft = {}) {
    const context = captureOperation()
    return mutate(context, async () => {
      const before = assets.value.find(item => item.id === assetId) || selectedAsset.value || {}
      const version = await client.restore(context.projectId, assetId, versionId, draft)
      requireCurrent(context)
      invalidateSceneAssetListCache(context.projectId)
      const reloaded = await loadAsset(assetId, context)
      requireCurrent(context)
      const change = reloaded.ok
        ? classifySceneAssetChange(before, selectedAsset.value || {})
        : { visualChange: true, downstreamStatus: 'STALE', affectedScopes: ['visual', 'continuity'] }
      const { impact: refreshedImpact, impactRefresh } = await impactAfterPersist(assetId, context)
      requireCurrent(context)
      const result = recordResult({
        action: 'restore-version', assetId, restoredVersionId: versionId,
        version, change, assetRefresh: reloaded.ok ? { ok: true } : reloaded,
        impact: refreshedImpact, impactRefresh, affectedConsumers: impactConsumers(change, refreshedImpact)
      }, context)
      return { version, asset: selectedAsset.value, result }
    })
  }

  async function archive(assetId) {
    const context = captureOperation()
    return mutate(context, async () => {
      await client.archive(context.projectId, assetId)
      requireCurrent(context)
      const archived = assets.value.find(asset => asset.id === assetId)
      assets.value = assets.value.map(asset => asset.id === assetId ? { ...asset, status: 'ARCHIVED' } : asset)
      if (selectedAsset.value?.id === assetId && archived) selectAsset({ ...archived, status: 'ARCHIVED' })
      invalidateSceneAssetListCache(context.projectId)
      return recordResult({ action: 'archive-scene-asset', assetId, affectedConsumers: [] }, context)
    })
  }

  async function disable(assetId) {
    const context = captureOperation()
    return mutate(context, async () => {
      const loadedImpact = await loadImpact(assetId, context)
      requireCurrent(context)
      if (!loadedImpact.ok) throw Object.assign(new Error(loadedImpact.message), { code: loadedImpact.code })
      const affectedConsumers = preservedImpactConsumers(loadedImpact.impact)
      const asset = normalizeSceneAsset(await client.disable(context.projectId, assetId))
      requireCurrent(context)
      replaceAsset(asset)
      invalidateSceneAssetListCache(context.projectId)
      return { asset, result: recordResult({ action: 'disable-scene-asset', assetId, affectedConsumers }, context) }
    })
  }

  async function activate(assetId) {
    const context = captureOperation()
    return mutate(context, async () => {
      const asset = normalizeSceneAsset(await client.activate(context.projectId, assetId))
      requireCurrent(context)
      replaceAsset(asset)
      invalidateSceneAssetListCache(context.projectId)
      return { asset, result: recordResult({ action: 'activate-scene-asset', assetId, affectedConsumers: [] }, context) }
    })
  }

  async function loadImpact(assetId = selectedAsset.value?.id, context = captureOperation()) {
    try {
      if (assetId == null) return failure('SCENE_ASSET_REQUIRED', '请先选择场景资产')
      const response = await client.impact(context.projectId, assetId)
      if (!operationIsCurrent(context)) return staleResponse()
      impact.value = response
      return { ok: true, impact: impact.value }
    } catch (error) {
      if (!operationIsCurrent(context)) return staleResponse()
      return failure(error?.code || 'SCENE_ASSET_IMPACT_FAILED', error?.message || '影响范围加载失败')
    }
  }

  async function loadMarkdown(assetId = selectedAsset.value?.id, context = captureOperation()) {
    try {
      if (assetId == null) return failure('SCENE_ASSET_REQUIRED', '请先选择场景资产')
      const response = await client.markdown(context.projectId, assetId)
      if (!operationIsCurrent(context)) return staleResponse()
      markdown.value = normalizeSceneAssetMarkdown(response)
      return { ok: true, markdown: markdown.value }
    } catch (error) {
      if (!operationIsCurrent(context)) return staleResponse()
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
    const context = captureOperation()
    const refreshedImpact = await loadImpact(assetId, context)
    if (!operationIsCurrent(context)) return staleResponse()
    if (!refreshedImpact.ok) return refreshedImpact
    const affectedConsumers = preservedImpactConsumers(refreshedImpact.impact)
    try {
      const response = await adapter({ assetId, replacement, impact: refreshedImpact.impact })
      if (!operationIsCurrent(context)) return staleResponse()
      if (!response?.persisted) return failure(response?.code || 'REFERENCE_REPLACEMENT_FAILED', response?.message || '引用迁移未持久化，请重试')
      const result = recordResult({
        action: 'replace-reference', assetId, replacement,
        affectedConsumers: response.affectedConsumers?.length ? response.affectedConsumers : affectedConsumers, response
      }, context)
      return { ok: true, data: result }
    } catch (error) {
      if (!operationIsCurrent(context)) return staleResponse()
      return failure(error?.code || 'REFERENCE_REPLACEMENT_FAILED', error?.message || '引用迁移失败')
    }
  }

  async function resolveConsumer(reference, decision, adapter = consumerAdapter?.resolveConsumer) {
    const allowed = ['view-diff', 'keep-old', 'upgrade-new']
    if (!allowed.includes(decision)) return failure('CONSUMER_DECISION_INVALID', '请选择查看差异、保留旧版或升级新版')
    const context = captureOperation()
    if (decision === 'view-diff' && typeof consumerAdapter?.viewDiff === 'function') {
      try {
        const response = await consumerAdapter.viewDiff(reference)
        if (!operationIsCurrent(context)) return staleResponse()
        return { ok: true, data: response }
      } catch (error) {
        if (!operationIsCurrent(context)) return staleResponse()
        return failure(error?.code || 'CONSUMER_DIFF_FAILED', error?.message || '查看差异失败')
      }
    }
    if (typeof adapter !== 'function') return failure('CONSUMER_RESOLUTION_UNAVAILABLE', '请先连接剧本/分镜版本处理服务')
    try {
      const response = await adapter({ reference, decision })
      if (!operationIsCurrent(context)) return staleResponse()
      if (!response?.persisted) return failure(response?.code || 'CONSUMER_RESOLUTION_FAILED', response?.message || '处理结果未持久化')
      return { ok: true, data: recordResult({ action: decision, reference, response, affectedConsumers: [reference] }, context) }
    } catch (error) {
      if (!operationIsCurrent(context)) return staleResponse()
      return failure(error?.code || 'CONSUMER_RESOLUTION_FAILED', error?.message || '下游版本处理失败')
    }
  }

  const referencedSelectedAsset = computed(() => sceneAssetIsReferenced(selectedAsset.value || {}))

  return {
    state, assets, filteredAssets, filters, selectedAsset, selectedVersion, impact, markdown, actionResult,
    actionResults, referencedSelectedAsset, readOnly,
    load, reset, loadAsset, selectAsset, setProjectArchived, create, update, createFromLocation, createVariant,
    updateVariant, restore, disable, activate, archive, loadImpact, loadMarkdown, openActionResult, replaceReferences, resolveConsumer
  }
}
