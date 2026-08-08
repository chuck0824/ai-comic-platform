import { computed, reactive, ref } from 'vue'
import { sceneAssetApi } from '@/api/sceneAsset'
import { normalizeSceneAsset, validateSceneAssetDraft } from './sceneAssetModel'
import { normalizeSceneAssetMarkdown } from './sceneAssetMarkdown'
import {
  invalidateSceneAssetListCache,
  prepareSceneAssetMutation,
  readSceneAssetListCache,
  resolveSceneAssetProjectId,
  sceneAssetMutationGuard,
  writeSuccessfulSceneAssetListCache
} from './sceneAssetState'

function failure(code, message) {
  return { ok: false, code, message }
}

/** Project-scoped scene asset state; degraded cache is intentionally read-only. */
export function useSceneAssets(projectId, { isProjectArchived = false } = {}) {
  const state = ref('loading')
  const assets = ref([])
  const filters = reactive({ keyword: '', spaceType: '', reusability: '', status: '', referenced: undefined })
  const selectedAsset = ref(null)
  const selectedVersion = ref(null)
  const impact = ref(null)
  const markdown = ref(null)
  const actionResult = ref(null)
  const projectArchived = ref(false)
  const activeProjectId = () => resolveSceneAssetProjectId(projectId)
  const isArchived = () => projectArchived.value || Boolean(typeof isProjectArchived === 'function'
    ? isProjectArchived()
    : isProjectArchived?.value ?? isProjectArchived)
  const readOnly = computed(() => state.value === 'readonly' || isArchived())

  const filteredAssets = computed(() => assets.value.filter(asset => {
    if (filters.keyword && !asset.name?.toLowerCase().includes(filters.keyword.toLowerCase())) return false
    if (filters.spaceType && asset.master?.spaceType !== filters.spaceType) return false
    if (filters.reusability && asset.master?.reusability !== filters.reusability) return false
    if (filters.status && asset.status !== filters.status) return false
    return true
  }))

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
      const response = await sceneAssetApi.list(resolvedProjectId, filterSnapshot)
      const list = Array.isArray(response) ? response : response.items ?? []
      assets.value = list.map(normalizeSceneAsset)
      writeSuccessfulSceneAssetListCache(resolvedProjectId, filterSnapshot, assets.value)
      state.value = isArchived() ? 'readonly' : (assets.value.length ? 'ready' : 'empty')
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
      const asset = normalizeSceneAsset(await sceneAssetApi.get(activeProjectId(), assetId))
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
      actionResult.value = { ok: true, data: result }
      return actionResult.value
    } catch (error) {
      actionResult.value = failure(error?.code || 'SCENE_ASSET_ACTION_FAILED', error?.message || '场景资产操作失败')
      return actionResult.value
    }
  }

  async function create(draft) {
    const prepared = prepareSceneAssetMutation({
      projectArchived: isArchived(), state: state.value, draft, validate: validateSceneAssetDraft
    })
    if (prepared) return prepared
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const asset = normalizeSceneAsset(await sceneAssetApi.create(resolvedProjectId, draft))
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
      const asset = normalizeSceneAsset(await sceneAssetApi.update(resolvedProjectId, assetId, draft))
      replaceAsset(asset)
      invalidateSceneAssetListCache(resolvedProjectId)
      return asset
    })
  }

  async function createFromLocation(draft) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const asset = normalizeSceneAsset(await sceneAssetApi.createFromLocation(resolvedProjectId, draft))
      replaceAsset(asset, true)
      invalidateSceneAssetListCache(resolvedProjectId)
      return asset
    })
  }

  async function createVariant(assetId, draft) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const asset = normalizeSceneAsset(await sceneAssetApi.createVariant(resolvedProjectId, assetId, draft))
      replaceAsset(asset)
      invalidateSceneAssetListCache(resolvedProjectId)
      return asset
    })
  }

  async function updateVariant(assetId, variantId, draft) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const asset = normalizeSceneAsset(await sceneAssetApi.updateVariant(resolvedProjectId, assetId, variantId, draft))
      replaceAsset(asset)
      invalidateSceneAssetListCache(resolvedProjectId)
      return asset
    })
  }

  async function restore(assetId, versionId, draft = {}) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      const version = await sceneAssetApi.restore(resolvedProjectId, assetId, versionId, draft)
      invalidateSceneAssetListCache(resolvedProjectId)
      await loadAsset(assetId)
      return version
    })
  }

  async function archive(assetId) {
    return mutate(async () => {
      const resolvedProjectId = activeProjectId()
      await sceneAssetApi.archive(resolvedProjectId, assetId)
      assets.value = assets.value.map(asset => asset.id === assetId ? { ...asset, status: 'ARCHIVED' } : asset)
      invalidateSceneAssetListCache(resolvedProjectId)
      return null
    })
  }

  async function loadImpact(assetId = selectedAsset.value?.id) {
    try {
      impact.value = await sceneAssetApi.impact(activeProjectId(), assetId)
      return { ok: true, impact: impact.value }
    } catch (error) {
      return failure(error?.code || 'SCENE_ASSET_IMPACT_FAILED', error?.message || '影响范围加载失败')
    }
  }

  async function loadMarkdown(assetId = selectedAsset.value?.id) {
    try {
      markdown.value = normalizeSceneAssetMarkdown(await sceneAssetApi.markdown(activeProjectId(), assetId))
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

  return {
    state, assets, filteredAssets, filters, selectedAsset, selectedVersion, impact, markdown, actionResult, readOnly,
    load, loadAsset, selectAsset, setProjectArchived, create, update, createFromLocation, createVariant,
    updateVariant, restore, archive, loadImpact, loadMarkdown
  }
}
