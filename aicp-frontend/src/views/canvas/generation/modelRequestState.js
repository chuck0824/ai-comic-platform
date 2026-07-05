/**
 * 模型请求预览与确认状态。
 */

/**
 * 检查是否可以确认提交。
 * 预览指纹必须与当前一致且积分必须有效。
 */
export function canConfirm({ previewFingerprint, currentFingerprint, estimatedCredits }) {
  return previewFingerprint === currentFingerprint && Number.isFinite(estimatedCredits)
}

/**
 * 从预览中提取参考摘要（按角色分组）。
 */
export function referenceSummary(preview) {
  const groups = {}
  for (const img of preview.images || []) {
    if (!groups[img.role]) groups[img.role] = []
    groups[img.role].push(img)
  }
  for (const vid of preview.videos || []) {
    if (!groups[vid.role]) groups[vid.role] = []
    groups[vid.role].push(vid)
  }
  return Object.entries(groups).map(([role, refs]) => ({ role, count: refs.length }))
}

/**
 * 获取推荐的模型和原因（从预览中提取）。
 */
export function modelRecommendation(preview) {
  return {
    modelId: preview.modelId,
    adapterVersion: preview.adapterVersion,
    estimatedCredits: preview.estimatedCredits,
    warnings: preview.warnings || []
  }
}
