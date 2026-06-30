import request from './request'

// ============================================================
// AI 资产市场 API (V2)
// ============================================================

// ---- 公共市场 ----
export function listMarket(params) {
  return request.get('/asset/market/listings', { params })
}
export function getListingDetail(id) {
  return request.get(`/asset/market/listings/${id}`)
}
export function claimListing(id) {
  return request.post(`/asset/market/listings/${id}/claim`)
}
export function favoriteListing(id) {
  return request.put(`/asset/market/listings/${id}/favorite`)
}
export function unfavoriteListing(id) {
  return request.delete(`/asset/market/listings/${id}/favorite`)
}

// ---- Workspace 资产库 ----
export function listLibrary(params) {
  return request.get('/asset/library', { params })
}
export function createAsset(body) {
  return request.post('/asset/library', body)
}
export function getLibraryAsset(id) {
  return request.get(`/asset/library/${id}`)
}
export function editLibraryAsset(id, body) {
  return request.put(`/asset/library/${id}`, body)
}
export function createAssetVersion(id, body) {
  return request.post(`/asset/library/${id}/versions`, body)
}
export function archiveAsset(id, rowVersion) {
  return request.post(`/asset/library/${id}/archive`, null, { params: { rowVersion } })
}
export function publishAsset(id, body) {
  return request.post(`/asset/library/${id}/publish`, body)
}
export function unlistAsset(id, rowVersion) {
  return request.post(`/asset/library/${id}/unlist`, null, { params: { rowVersion } })
}
export function applyAsset(id, body) {
  return request.post(`/asset/library/${id}/applications`, body)
}
export function undoApplication(id, body) {
  return request.post(`/asset/applications/${id}/undo`, body)
}

// ---- 企业审批 ----
export function requestPublish(id, body) {
  return request.post(`/asset/library/${id}/publish-requests`, body)
}
export function listPublishRequests(params) {
  return request.get('/asset/publish-requests', { params })
}
export function getPublishRequest(id) {
  return request.get(`/asset/publish-requests/${id}`)
}
export function approveRequest(id, body) {
  return request.post(`/asset/publish-requests/${id}/approve`, body)
}
export function rejectRequest(id, body) {
  return request.post(`/asset/publish-requests/${id}/reject`, body)
}
export function cancelRequest(id) {
  return request.post(`/asset/publish-requests/${id}/cancel`)
}

// ============================================================
// 旧接口兼容包装 (deprecated, 将在前端迁移完成后移除)
// ============================================================

/** @deprecated Use listMarket({ ...params, type: 'checkpoint' }) */
export const getModels = (params) => listMarket({ ...params, type: 'checkpoint' })
/** @deprecated Use listMarket({ ...params, type: 'character' }) */
export const getCharacters = (params) => listMarket({ ...params, type: 'character' })
/** @deprecated Use listMarket({ ...params, type: 'scene' }) */
export const getScenes = (params) => listMarket({ ...params, type: 'scene' })
/** @deprecated Use listMarket({ ...params, type: 'prompt' }) */
export const getPrompts = (params) => listMarket({ ...params, type: 'prompt' })
/** @deprecated Voice/BGM not available in this release */
export const getVoices = () => Promise.resolve({ data: { code: 0, data: { items: [], pagination: { total: 0 } } } })
/** @deprecated Voice/BGM not available in this release */
export const getSounds = () => Promise.resolve({ data: { code: 0, data: { items: [], pagination: { total: 0 } } } })
/** @deprecated Use claimListing(id) */
export const downloadAsset = (id) => claimListing(id)

// 保持旧 assetApi 对象导出以兼容可能存在的引用
export const assetApi = {
  search: (params) => listMarket(params),
  getModels,
  getModelDetail: (id) => getListingDetail(id),
  getCharacters,
  getScenes,
  getPrompts,
  getVoices,
  getSounds,
  applyModel: (id) => applyAsset(id, { project_id: null, target_type: 'PROJECT', idempotency_key: crypto.randomUUID?.() ?? `${Date.now()}` }),
  downloadAsset,
  favoriteAsset: (id) => favoriteListing(id),
  publishAsset: (data) => publishAsset(data.asset_id, data),
  updateAsset: (id, data) => editLibraryAsset(id, data),
  getMyAssets: () => listLibrary({}),
  getMyFavorites: () => listMarket({ claimed: true }),
  getMyDownloads: () => listLibrary({ sourceType: 'MARKET_CLAIMED' })
}
