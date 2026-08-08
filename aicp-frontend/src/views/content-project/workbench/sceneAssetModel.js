const REQUIRED_FIELDS = {
  name: '场景名称不能为空',
  spaceType: '空间类型不能为空',
  reusability: '复用级别不能为空',
  realityType: '现实类型不能为空'
}

const VISUAL_FIELDS = new Set([
  'spaceType', 'realityType', 'worldLocationRef', 'layout', 'materials', 'palette',
  'lighting', 'landmarks', 'fixedProps', 'movableProps', 'entrancesExits',
  'references', 'prompts', 'variants'
])

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function camelKey(key) {
  return key.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())
}

function camelize(value) {
  if (Array.isArray(value)) return value.map(camelize)
  if (!isRecord(value)) return value
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [camelKey(key), camelize(item)]))
}

function stableValue(value) {
  if (Array.isArray(value)) return `[${value.map(stableValue).join(',')}]`
  if (!isRecord(value)) return JSON.stringify(value)
  return `{${Object.keys(value).sort().map(key => `${key}:${stableValue(value[key])}`).join(',')}}`
}

function nonBlank(value) {
  return typeof value === 'string' && value.trim().length > 0
}

/** Converts the project scene-asset API record into the Vue camelCase model. */
export function normalizeSceneAsset(raw) {
  const source = raw?.data ?? raw ?? {}
  const asset = camelize(source)
  return {
    ...asset,
    master: isRecord(asset.master) ? asset.master : {},
    variants: Array.isArray(asset.variants) ? asset.variants : []
  }
}

/** Returns field-level validation messages; an empty object means the draft is valid. */
export function validateSceneAssetDraft(draft = {}) {
  const errors = {}
  for (const [field, message] of Object.entries(REQUIRED_FIELDS)) {
    if (!nonBlank(draft[field])) errors[field] = message
  }
  return errors
}

/**
 * Resolves a master/variant for UI display and prepares the binding payload that
 * the storyboard endpoint turns into its signed historical snapshot.
 */
export function mergeSceneAssetVariant(master = {}, variant = {}) {
  const normalizedMaster = normalizeSceneAsset(master)
  const base = normalizedMaster.master && Object.keys(normalizedMaster.master).length
    ? { ...normalizedMaster, ...normalizedMaster.master }
    : normalizedMaster
  const resolvedVariant = camelize(variant)
  const masterVersion = base.currentVersionNo ?? base.version ?? 0
  const assetVersionId = base.currentVersionId ?? base.assetVersionId ?? base.versionId ?? base.version ?? null
  const sceneOverride = {}
  if (resolvedVariant.lightingDelta != null) sceneOverride.lighting = resolvedVariant.lightingDelta
  for (const key of ['time', 'eventState', 'prompts', 'references']) {
    if (resolvedVariant[key] != null) sceneOverride[key] = resolvedVariant[key]
  }

  const resolved = {
    ...base,
    ...sceneOverride,
    masterId: base.id ?? null,
    masterVersion,
    variantId: resolvedVariant.id ?? null,
    variantVersion: resolvedVariant.version ?? 0,
    bindingPayload: {
      sceneAssetId: base.id ?? null,
      sceneAssetVersionId: assetVersionId,
      sceneVariantId: resolvedVariant.id ?? null,
      sceneVariantVersion: resolvedVariant.version ?? 0,
      sceneOverride
    }
  }

  // This mirrors the stable portion of the server-created snapshot. The backend
  // remains the sole issuer of finalPromptFragment and fingerprint.
  resolved.bindingSnapshot = {
    master: {
      id: base.stableId ?? base.id ?? null,
      name: base.name ?? '',
      version: masterVersion,
      path: base.path ?? '',
      fixedProps: base.fixedProps ?? []
    },
    variant: {
      id: resolvedVariant.id ?? null,
      name: resolvedVariant.name ?? '',
      version: resolvedVariant.version ?? 0
    },
    sceneOverride,
    continuityRules: base.continuityRules ?? []
  }
  return resolved
}

/** Classifies only visual or continuity changes as downstream-staling changes. */
export function classifySceneAssetChange(before = {}, after = {}) {
  const flattenForChange = value => {
    const asset = normalizeSceneAsset(value)
    const { master, ...identity } = asset
    return { ...identity, ...master }
  }
  const previous = flattenForChange(before)
  const next = flattenForChange(after)
  const keys = new Set([...Object.keys(previous), ...Object.keys(next)])
  const affectedScopes = []
  let visualChange = false
  for (const key of keys) {
    if (stableValue(previous[key]) === stableValue(next[key])) continue
    if (key === 'continuityRules') {
      affectedScopes.push('continuity')
    } else if (VISUAL_FIELDS.has(key)) {
      visualChange = true
      if (!affectedScopes.includes('visual')) affectedScopes.push('visual')
    }
  }
  const stale = visualChange || affectedScopes.includes('continuity')
  return { visualChange, downstreamStatus: stale ? 'STALE' : 'CURRENT', affectedScopes }
}

export { camelize }
