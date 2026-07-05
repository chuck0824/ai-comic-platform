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

  // ===== R3: 模型适配器预览与提交 =====
  /** 提交前预览：能力、路由、费用 */
  previewModelRequest: (nodeId, data) =>
    request.post(`/canvas/nodes/${nodeId}/model-requests/preview`, data),
  /** 确认后提交：冻结快照并创建任务 */
  submitModelRequest: (nodeId, data) =>
    request.post(`/canvas/nodes/${nodeId}/model-requests`, data),
  /** 导演包模型请求预览 */
  previewDirectorModelRequest: (revisionId) =>
    request.post(`/director-revisions/${revisionId}/model-requests/preview`),
  /** 提交导演包生成任务 */
  submitDirectorModelRequest: (revisionId, data) =>
    request.post(`/director-revisions/${revisionId}/model-requests`, data),

  // ===== R2/R3: Blender 预演 =====
  /** 创建 Blender 预演任务 */
  createPreviewRender: (revisionId, idempotencyKey) =>
    request.post(`/director-revisions/${revisionId}/preview-renders`, null,
      { headers: { 'Idempotency-Key': idempotencyKey } }),

  // 资产
  getAssetHistory: (params) => request.get('/assets/history', { params }),
  sendAssetToCanvas: (assetId) => request.post(`/assets/${assetId}/send-to-canvas`)
}
