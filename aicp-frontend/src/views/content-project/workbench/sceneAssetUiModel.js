const RESULT_PREFIX = 'scene_asset_action_results:'

function textValues(value) {
  if (Array.isArray(value)) return value.flatMap(textValues)
  if (value == null) return []
  if (typeof value === 'object') return Object.values(value).flatMap(textValues)
  return [String(value)]
}

function includesKeyword(asset, keyword) {
  const normalized = String(keyword || '').trim().toLocaleLowerCase()
  if (!normalized) return true
  const master = asset?.master || {}
  return textValues([
    asset?.name,
    asset?.stableId,
    master.worldLocationRef,
    master.sourceLocation,
    master.landmarks,
    master.tags,
    asset?.tags
  ]).some(value => value.toLocaleLowerCase().includes(normalized))
}

export function sceneAssetIsReferenced(asset = {}) {
  if (typeof asset.referenced === 'boolean') return asset.referenced
  if (Number(asset.episodeReferenceCount) > 0) return true
  if (Array.isArray(asset.episodeReferences)) return asset.episodeReferences.length > 0
  return Number(asset.referenceCount ?? asset.referencesCount ?? 0) > 0
}

/** Pure, shared filtering used by the library and degraded cached lists. */
export function filterSceneAssets(assets = [], filters = {}) {
  return (Array.isArray(assets) ? assets : []).filter(asset => {
    const master = asset?.master || {}
    if (!includesKeyword(asset, filters.keyword)) return false
    if (filters.spaceType && master.spaceType !== filters.spaceType) return false
    if (filters.reusability && master.reusability !== filters.reusability) return false
    if (filters.status && String(asset.status || '').toUpperCase() !== String(filters.status).toUpperCase()) return false
    if (typeof filters.referenced === 'boolean' && sceneAssetIsReferenced(asset) !== filters.referenced) return false
    return true
  })
}

function isLocked(reference = {}) {
  const status = String(reference.syncStatus || '').toUpperCase()
  return reference.locked === true || reference.snapshotLocked === true || status === 'PINNED' || status === 'LOCKED'
}

/** Projects authoritative impact references into the three downstream decisions. */
export function impactConsumers(change = {}, impact = {}) {
  return (impact?.references || []).map(reference => {
    const locked = isLocked(reference)
    const semanticStale = change.downstreamStatus === 'STALE'
    return {
      ...reference,
      locked,
      downstreamStatus: locked ? 'PINNED' : (semanticStale ? 'STALE' : 'CURRENT'),
      affectedScopes: semanticStale ? [...(change.affectedScopes || [])] : [],
      actions: locked || !semanticStale
        ? ['view-diff']
        : ['view-diff', 'keep-old', 'upgrade-new']
    }
  })
}

function storageOrNull(storage) {
  if (storage != null) return storage
  try { return globalThis.localStorage ?? null } catch { return null }
}

function resultKey(projectId) {
  return `${RESULT_PREFIX}${projectId ?? 'unknown'}`
}

export function readSceneAssetActionResults(projectId, storage) {
  const target = storageOrNull(storage)
  if (!target) return []
  try {
    const value = JSON.parse(target.getItem(resultKey(projectId)) || '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}

/** Persists successful mutation evidence so impact can be reopened after navigation/reload. */
export function persistSceneAssetActionResult(projectId, result, storage) {
  if (!result || typeof result !== 'object') return null
  const target = storageOrNull(storage)
  if (!target) return result
  try {
    const existing = readSceneAssetActionResults(projectId, target)
    const next = [result, ...existing.filter(item => item?.id !== result.id)].slice(0, 50)
    target.setItem(resultKey(projectId), JSON.stringify(next))
  } catch { /* Persistent evidence is best effort when browser storage is unavailable. */ }
  return result
}
