/**
 * 交付清单前端状态。
 */

/**
 * 检查是否可以创建交付清单。
 */
export function canCreateManifest(project) {
  const units = project?.units || []
  return project?.mode === 'PRODUCTION'
    && units.length > 0
    && units.every(unit => unit.adopted)
}

/**
 * 交付清单状态对应的标签类型。
 */
export function manifestStatusType(status) {
  return { DRAFT: 'info', FINALIZED: 'success', PACKAGING: 'warning', READY: 'success', FAILED: 'danger' }[status] || 'info'
}

/**
 * 支持的外部交换格式。
 */
export const EXCHANGE_FORMATS = [
  { id: 'zip', label: 'ZIP 素材包', ext: '.zip' },
  { id: 'edl', label: 'EDL (CMX3600)', ext: '.edl' },
  { id: 'fcpxml', label: 'FCPXML 1.9', ext: '.fcpxml' }
]
