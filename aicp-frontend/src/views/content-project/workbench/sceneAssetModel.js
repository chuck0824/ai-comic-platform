const REQUIRED_FIELDS = {
  name: '场景名称不能为空',
  spaceType: '空间类型不能为空',
  reusability: '复用级别不能为空',
  realityType: '现实类型不能为空'
}

const VISUAL_FIELDS = new Set([
  'spaceType', 'realityType', 'worldLocationRef', 'layout', 'materials', 'palette',
  'lighting', 'landmarks', 'fixedProps', 'movableProps', 'entrancesExits',
  'references', 'prompts'
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
  const assetVersionId = base.currentVersionId ?? base.assetVersionId ?? base.versionId ?? null
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
  const fieldErrors = {}
  if (assetVersionId == null) fieldErrors.sceneAssetVersionId = '请选择包含资产版本主键的场景版本'
  if (base.id == null) fieldErrors.sceneAssetId = '请选择场景资产'
  if (resolvedVariant.id == null) fieldErrors.sceneVariantId = '请选择场景变体'
  if (resolvedVariant.version == null) fieldErrors.sceneVariantVersion = '请选择场景变体版本'
  resolved.bindingState = { submittable: Object.keys(fieldErrors).length === 0, fieldErrors }

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
    if (key === 'variants') continue
    if (key === 'continuityRules') {
      affectedScopes.push('continuity')
    } else if (VISUAL_FIELDS.has(key)) {
      visualChange = true
      if (!affectedScopes.includes('visual')) affectedScopes.push('visual')
    }
  }
  const variantScopes = classifyVariantDeltas(previous.variants, next.variants)
  if (variantScopes.includes('visual')) {
    visualChange = true
    if (!affectedScopes.includes('visual')) affectedScopes.push('visual')
  }
  if (variantScopes.includes('continuity') && !affectedScopes.includes('continuity')) {
    affectedScopes.push('continuity')
  }
  const stale = visualChange || affectedScopes.includes('continuity')
  return { visualChange, downstreamStatus: stale ? 'STALE' : 'CURRENT', affectedScopes }
}

function classifyVariantDeltas(before, after) {
  const previous = new Map((Array.isArray(before) ? before : []).filter(isRecord).map(item => [item.id, item]))
  const next = new Map((Array.isArray(after) ? after : []).filter(isRecord).map(item => [item.id, item]))
  const scopes = []
  for (const id of new Set([...previous.keys(), ...next.keys()])) {
    const left = previous.get(id)
    const right = next.get(id)
    // Adding/removing options, labels, tags, notes, and version bookkeeping does
    // not invalidate historical consumers. Compare only paired semantic deltas.
    if (!left || !right) continue
    if (['lightingDelta', 'prompts', 'references'].some(key => stableValue(left[key]) !== stableValue(right[key]))) {
      scopes.push('visual')
    }
    if (['eventState', 'time', 'continuityRules'].some(key => stableValue(left[key]) !== stableValue(right[key]))) {
      scopes.push('continuity')
    }
  }
  return [...new Set(scopes)]
}

export { camelize }
