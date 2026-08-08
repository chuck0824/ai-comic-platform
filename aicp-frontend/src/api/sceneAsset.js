import request from './request'

function snakeKey(key) {
  return key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`)
}

function camelKey(key) {
  return key.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())
}

function mapKeys(value, keyMapper) {
  if (Array.isArray(value)) return value.map(item => mapKeys(item, keyMapper))
  if (value === null || typeof value !== 'object') return value
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [keyMapper(key), mapKeys(item, keyMapper)]))
}

export const toSceneAssetPayload = value => mapKeys(value ?? {}, snakeKey)
export const fromSceneAssetResponse = value => mapKeys(value?.data ?? value ?? {}, camelKey)

async function mapped(call) {
  return fromSceneAssetResponse(await call())
}

const base = projectId => `/content-projects/${projectId}/scene-assets`

/** Scene assets are camelCase in Vue and snake_case only at this HTTP boundary. */
export const sceneAssetApi = {
  list: (projectId, filters = {}) => mapped(() => request.get(base(projectId), { params: toSceneAssetPayload(filters) })),
  create: (projectId, draft) => mapped(() => request.post(base(projectId), toSceneAssetPayload(draft))),
  get: (projectId, assetId) => mapped(() => request.get(`${base(projectId)}/${assetId}`)),
  update: (projectId, assetId, draft) => mapped(() => request.patch(`${base(projectId)}/${assetId}`, toSceneAssetPayload(draft))),
  createFromLocation: (projectId, draft) => mapped(() => request.post(`${base(projectId)}/from-location`, toSceneAssetPayload(draft))),
  createVariant: (projectId, assetId, draft) => mapped(() => request.post(`${base(projectId)}/${assetId}/variants`, toSceneAssetPayload(draft))),
  updateVariant: (projectId, assetId, variantId, draft) => mapped(() => request.patch(`${base(projectId)}/${assetId}/variants/${variantId}`, toSceneAssetPayload(draft))),
  restore: (projectId, assetId, versionId, draft = {}) => mapped(() => request.post(`${base(projectId)}/${assetId}/versions/${versionId}/restore`, toSceneAssetPayload(draft))),
  archive: (projectId, assetId) => mapped(() => request.post(`${base(projectId)}/${assetId}/archive`)),
  impact: (projectId, assetId) => mapped(() => request.get(`${base(projectId)}/${assetId}/impact`)),
  markdown: (projectId, assetId) => mapped(() => request.get(`${base(projectId)}/${assetId}/markdown`))
}
