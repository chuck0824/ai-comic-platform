import request from './request'

export const assetApi = {
  search: (params) => request.get('/asset/market/search', { params }),
  getModels: (params) => request.get('/asset/market/models', { params }),
  getModelDetail: (id) => request.get(`/asset/market/models/${id}`),
  getCharacters: (params) => request.get('/asset/market/characters', { params }),
  getScenes: (params) => request.get('/asset/market/scenes', { params }),
  getPrompts: (params) => request.get('/asset/market/prompts', { params }),
  getVoices: (params) => request.get('/asset/market/voices', { params }),
  getSounds: (params) => request.get('/asset/market/sounds', { params }),
  applyModel: (id) => request.post(`/asset/market/models/${id}/apply`),
  downloadAsset: (id) => request.post(`/asset/market/assets/${id}/download`),
  favoriteAsset: (id) => request.post(`/asset/market/assets/${id}/favorite`),
  publishAsset: (data) => request.post('/asset/market/publish', data),
  updateAsset: (id, data) => request.put(`/asset/market/assets/${id}`, data),
  getMyAssets: () => request.get('/asset/market/my/assets'),
  getMyFavorites: () => request.get('/asset/market/my/favorites'),
  getMyDownloads: () => request.get('/asset/market/my/downloads')
}
