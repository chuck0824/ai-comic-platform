import request from './request'

export const generationApi = {
  // 任务
  createTask: (data) => request.post('/generation/tasks', data),
  getTask: (taskId) => request.get(`/generation/tasks/${taskId}`),
  cancelTask: (taskId) => request.post(`/generation/tasks/${taskId}/cancel`),
  retryTask: (taskId) => request.post(`/generation/tasks/${taskId}/retry`),

  // 多副本
  createVariants: (data) => request.post('/generation/variants', data),
  getVariants: (parentTaskId) => request.get(`/generation/variants/${parentTaskId}`),
  selectVariant: (variantId) => request.post(`/generation/variants/${variantId}/select`),

  // 全能参考视频
  createOmniReference: (data) => request.post('/generation/video/reference', data),

  // 算力
  estimateCredits: (data) => request.post('/credits/estimate', data),

  // 资产
  getAssetHistory: (params) => request.get('/assets/history', { params }),
  sendAssetToCanvas: (assetId) => request.post(`/assets/${assetId}/send-to-canvas`)
}
