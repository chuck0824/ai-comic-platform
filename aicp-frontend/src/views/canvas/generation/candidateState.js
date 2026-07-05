/**
 * 生成候选状态管理。
 */

/**
 * 检查是否可以确认模型请求。
 * @param {{ previewFingerprint?: string, currentFingerprint?: string, estimatedCredits?: number }} state
 */
export function canConfirm(state) {
  return state.previewFingerprint === state.currentFingerprint
    && Number.isFinite(state.estimatedCredits)
}

/**
 * 从预览中提取参考摘要。
 * @param {{ references?: Array<{ role: string, assetId: number }> }} preview
 * @returns {Array<{ role: string }>}
 */
export function referenceSummary(preview) {
  return (preview.references || []).map(ref => ({ role: ref.role }))
}

/**
 * 检查节点当前选中的候选。
 * @param {Array} candidates - 候选列表
 * @returns {object|null} 当前选中的候选
 */
export function selectedCandidate(candidates) {
  return candidates.find(c => c.isSelected) || null
}
