const CACHE_PREFIX = 'scene_assets:'

function stableValue(value) {
  if (Array.isArray(value)) return `[${value.map(stableValue).join(',')}]`
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${key}:${stableValue(value[key])}`).join(',')}}`
  }
  return JSON.stringify(value)
}

function storageOrNull(storage) {
  if (storage != null) return storage
  try {
    return globalThis.localStorage ?? null
  } catch {
    return null
  }
}

function failure(code, message, fieldErrors) {
  return { ok: false, code, message, ...(fieldErrors ? { fieldErrors } : {}) }
}

/** Unwraps refs/computed-like values at the moment an API or cache call is made. */
export function resolveSceneAssetProjectId(projectId) {
  let value = projectId
  const seen = new Set()
  while (value && typeof value === 'object' && 'value' in value && !seen.has(value)) {
    seen.add(value)
    value = value.value
  }
  return value == null ? null : value
}

export function sceneAssetFilterSignature(filters = {}) {
  return stableValue(Object.fromEntries(Object.entries(filters)
    .filter(([, value]) => value !== undefined)
    .sort(([left], [right]) => left.localeCompare(right))))
}

export function sceneAssetCacheKey(projectId, filters = {}) {
  const resolved = resolveSceneAssetProjectId(projectId)
  return `${CACHE_PREFIX}${resolved ?? 'unknown'}:${encodeURIComponent(sceneAssetFilterSignature(filters))}`
}

/** Reads only an envelope written by a successful list call, including a valid empty list. */
export function readSceneAssetListCache(projectId, filters = {}, storage) {
  const store = storageOrNull(storage)
  if (!store) return { found: false, items: [] }
  try {
    const key = sceneAssetCacheKey(projectId, filters)
    const value = JSON.parse(store.getItem(key) || 'null')
    if (!value || value.schemaVersion !== 1 || value.filterSignature !== sceneAssetFilterSignature(filters)
      || !Array.isArray(value.items)) return { found: false, items: [] }
    return { found: true, items: value.items }
  } catch {
    return { found: false, items: [] }
  }
}

/** Writes a cache snapshot only after the list request has completed successfully. */
export function writeSuccessfulSceneAssetListCache(projectId, filters = {}, items = [], storage) {
  const store = storageOrNull(storage)
  if (!store || !Array.isArray(items)) return
  try {
    store.setItem(sceneAssetCacheKey(projectId, filters), JSON.stringify({
      schemaVersion: 1,
      filterSignature: sceneAssetFilterSignature(filters),
      items
    }))
  } catch { /* Cache is optional. */ }
}

/** Mutations invalidate every filtered list snapshot for the active project. */
export function invalidateSceneAssetListCache(projectId, storage) {
  const store = storageOrNull(storage)
  if (!store) return
  const prefix = `${CACHE_PREFIX}${resolveSceneAssetProjectId(projectId) ?? 'unknown'}:`
  try {
    const keys = Array.from({ length: store.length }, (_, index) => store.key(index))
      .filter(key => key?.startsWith(prefix))
    keys.forEach(key => store.removeItem(key))
  } catch { /* Cache is optional. */ }
}

export function sceneAssetMutationGuard({ projectArchived = false, state = 'ready' } = {}) {
  if (projectArchived) return failure('PROJECT_ARCHIVED', '项目已归档，仅可查看场景资产')
  if (state === 'readonly') return failure('DEGRADED_READ_ONLY', '当前为缓存只读模式，网络恢复后再修改')
  return null
}

/** Applies the lifecycle guard before draft validation so archived/degraded is authoritative. */
export function prepareSceneAssetMutation({ projectArchived = false, state = 'ready', draft, validate } = {}) {
  const guarded = sceneAssetMutationGuard({ projectArchived, state })
  if (guarded) return guarded
  const fieldErrors = validate ? validate(draft) : {}
  if (Object.keys(fieldErrors).length) return failure('VALIDATION_FAILED', '请补全必填场景信息', fieldErrors)
  return null
}
