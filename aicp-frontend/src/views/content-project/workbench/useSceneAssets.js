import { computed, reactive, ref } from 'vue'
import { sceneAssetApi } from '@/api/sceneAsset'
import { normalizeSceneAsset, validateSceneAssetDraft } from './sceneAssetModel'
import { normalizeSceneAssetMarkdown } from './sceneAssetMarkdown'

const cacheKey = projectId => `scene_assets:${projectId}`

function readCache(projectId) {
  try {
    const value = JSON.parse(localStorage.getItem(cacheKey(projectId)) || '[]')
    return Array.isArray(value) ? value.map(normalizeSceneAsset) : []
  } catch {
    return []
  }
}

function writeCache(projectId, items) {
  try {
    localStorage.setItem(cacheKey(projectId), JSON.stringify(items))
  } catch { /* Cache is optional. */ }
}

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
  const projectArchived = ref(Boolean(typeof isProjectArchived === 'function' ? isProjectArchived() : isProjectArchived?.value ?? isProjectArchived))
  const readOnly = computed(() => state.value === 'readonly' || projectArchived.value)

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
    try {
      const response = await sceneAssetApi.list(projectId, filters)
      const list = Array.isArray(response) ? response : response.items ?? []
      assets.value = list.map(normalizeSceneAsset)
      writeCache(projectId, assets.value)
      state.value = projectArchived.value ? 'readonly' : (assets.value.length ? 'ready' : 'empty')
      return { ok: true, items: assets.value }
    } catch (error) {
      const cached = readCache(projectId)
      if (cached.length) {
        assets.value = cached
        state.value = 'readonly'
        return failure('DEGRADED_READ_ONLY', '网络不可用，正在显示最近成功缓存，当前仅可查看')
      }
      state.value = 'error'
      return failure(error?.code || 'SCENE_ASSET_LOAD_FAILED', error?.message || '场景资产加载失败')
    }
  }

  async function loadAsset(assetId) {
    try {
      const asset = normalizeSceneAsset(await sceneAssetApi.get(projectId, assetId))
      selectAsset(asset)
      return { ok: true, asset }
    } catch (error) {
      return failure(error?.code || 'SCENE_ASSET_LOAD_FAILED', error?.message || '场景资产加载失败')
    }
  }

  async function mutate(operation) {
    if (projectArchived.value) return failure('PROJECT_ARCHIVED', '项目已归档，仅可查看场景资产')
    if (state.value === 'readonly') return failure('DEGRADED_READ_ONLY', '当前为缓存只读模式，网络恢复后再修改')
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
    const errors = validateSceneAssetDraft(draft)
    if (Object.keys(errors).length) return failure('VALIDATION_FAILED', '请补全必填场景信息')
    return mutate(async () => {
      const asset = normalizeSceneAsset(await sceneAssetApi.create(projectId, draft))
      assets.value = [asset, ...assets.value]
      writeCache(projectId, assets.value)
      selectAsset(asset)
      state.value = 'ready'
      return asset
    })
  }

  async function update(assetId, draft) {
    return mutate(async () => {
      const asset = normalizeSceneAsset(await sceneAssetApi.update(projectId, assetId, draft))
      replaceAsset(asset)
      return asset
    })
  }

  async function createFromLocation(draft) {
    return mutate(async () => {
      const asset = normalizeSceneAsset(await sceneAssetApi.createFromLocation(projectId, draft))
      replaceAsset(asset, true)
      return asset
    })
  }

  async function createVariant(assetId, draft) {
    return mutate(async () => {
      const asset = normalizeSceneAsset(await sceneAssetApi.createVariant(projectId, assetId, draft))
      replaceAsset(asset)
      return asset
    })
  }

  async function updateVariant(assetId, variantId, draft) {
    return mutate(async () => {
      const asset = normalizeSceneAsset(await sceneAssetApi.updateVariant(projectId, assetId, variantId, draft))
      replaceAsset(asset)
      return asset
    })
  }

  async function restore(assetId, versionId, draft = {}) {
    return mutate(async () => {
      const version = await sceneAssetApi.restore(projectId, assetId, versionId, draft)
      await loadAsset(assetId)
      return version
    })
  }

  async function archive(assetId) {
    return mutate(async () => {
      await sceneAssetApi.archive(projectId, assetId)
      assets.value = assets.value.map(asset => asset.id === assetId ? { ...asset, status: 'ARCHIVED' } : asset)
      writeCache(projectId, assets.value)
      return null
    })
  }

  async function loadImpact(assetId = selectedAsset.value?.id) {
    try {
      impact.value = await sceneAssetApi.impact(projectId, assetId)
      return { ok: true, impact: impact.value }
    } catch (error) {
      return failure(error?.code || 'SCENE_ASSET_IMPACT_FAILED', error?.message || '影响范围加载失败')
    }
  }

  async function loadMarkdown(assetId = selectedAsset.value?.id) {
    try {
      markdown.value = normalizeSceneAssetMarkdown(await sceneAssetApi.markdown(projectId, assetId))
      return { ok: true, markdown: markdown.value }
    } catch (error) {
      return failure(error?.code || 'SCENE_ASSET_MARKDOWN_FAILED', error?.message || 'Markdown 预览加载失败')
    }
  }

  function replaceAsset(asset, addWhenMissing = false) {
    const index = assets.value.findIndex(item => item.id === asset.id)
    assets.value = index < 0 ? (addWhenMissing ? [asset, ...assets.value] : assets.value) : assets.value.map(item => item.id === asset.id ? asset : item)
    writeCache(projectId, assets.value)
    if (selectedAsset.value?.id === asset.id) selectAsset(asset)
  }

  return {
    state, assets, filteredAssets, filters, selectedAsset, selectedVersion, impact, markdown, actionResult, readOnly,
    load, loadAsset, selectAsset, setProjectArchived, create, update, createFromLocation, createVariant,
    updateVariant, restore, archive, loadImpact, loadMarkdown
  }
}
