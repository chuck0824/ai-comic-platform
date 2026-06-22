import request from './request'

export const scriptApi = {
  // 生成
  genQuick: (data) => request.post('/script/gen/quick', data),
  genTopic: (data) => request.post('/script/gen/topic', data),
  genSynopsis: (data) => request.post('/script/gen/synopsis', data),
  genOutline: (data) => request.post('/script/gen/outline', data),
  genEpisode: (data) => request.post('/script/gen/episode', data),
  genStoryboard: (data) => request.post('/script/gen/storyboard', data),
  genPromotion: (data) => request.post('/script/gen/promotion', data),
  upgradeStoryboard: (data) => request.post('/script/gen/storyboard/upgrade', data),
  getTaskStatus: (taskId) => request.get(`/script/gen/task/${taskId}`),
  getTaskHistory: (params) => request.get('/script/gen/tasks', { params }),

  // 仓库
  getScripts: (params) => request.get('/script/repo/scripts', { params }),
  getScript: (id) => request.get(`/script/repo/scripts/${id}`),
  createScript: (data) => request.post('/script/repo/scripts', data),
  updateScript: (id, data) => request.put(`/script/repo/scripts/${id}`, data),
  deleteScript: (id) => request.delete(`/script/repo/scripts/${id}`),
  updateTags: (id, data) => request.put(`/script/repo/scripts/${id}/tags`, data),
  updateStatus: (id, status) => request.put(`/script/repo/scripts/${id}/status`, { status }),
  getVersions: (id) => request.get(`/script/repo/scripts/${id}/versions`),
  createVersion: (id, data) => request.post(`/script/repo/scripts/${id}/versions`, data),
  restoreVersion: (id, vid) => request.post(`/script/repo/scripts/${id}/versions/${vid}/restore`),

  // 资产
  getAssets: (params) => request.get('/script/repo/assets', { params }),
  createCharacter: (data) => request.post('/script/repo/assets/character', data),
  createScene: (data) => request.post('/script/repo/assets/scene', data),
  updateMaturity: (type, id, level) =>
    request.put(`/script/repo/assets/${type}/${id}/maturity`, { maturity_level: level }),
  lockAsset: (type, id) => request.put(`/script/repo/assets/${type}/${id}/lock`),

  // 上传
  uploadScript: (formData) => request.post('/script/repo/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  getUploadStatus: (scriptId) => request.get(`/script/repo/upload/${scriptId}/status`),

  // 钩子
  getHooks: (scriptId) => request.get(`/script/repo/scripts/${scriptId}/hooks`),
  generateHooks: (scriptId, episodeId) =>
    request.post(`/script/repo/scripts/${scriptId}/episodes/${episodeId}/hooks/generate`),
  generateAllHooks: (scriptId) =>
    request.post(`/script/repo/scripts/${scriptId}/hooks/generate-all`),
  updateHook: (hookId, data) => request.put(`/script/repo/hooks/${hookId}`, data),

  // 角色提取
  extractCharacters: (scriptId) => request.get(`/script/repo/scripts/${scriptId}/characters`),
  saveCharacters: (scriptId) => request.post(`/script/repo/scripts/${scriptId}/characters/save`),

  // Prompt 模板
  getPrompts: (params) => request.get('/script/prompts', { params }),
  savePrompt: (data) => request.post('/script/prompts', data)
}
