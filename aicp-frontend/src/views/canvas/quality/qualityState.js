/**
 * 质量报告前端状态。
 */

/**
 * 根据质量问题推断跳转目标。
 */
export function issueTarget(issue) {
  if (issue.source_track_id) {
    return { route: 'director', nodeId: issue.source_node_id, trackId: issue.source_track_id, timeMs: issue.start_ms }
  }
  if (issue.source_node_id) {
    return { route: 'canvas', nodeId: issue.source_node_id, timeMs: issue.start_ms }
  }
  return { route: null, nodeId: null, timeMs: issue.start_ms }
}

/**
 * 质量状态对应的标签颜色。
 */
export function qualityStatusColor(status) {
  return { PASS: 'success', WARN: 'warning', BLOCK: 'danger' }[status] || 'info'
}

/**
 * 严重度对应的标签颜色。
 */
export function severityColor(severity) {
  return { INFO: 'info', WARN: 'warning', ERROR: 'danger' }[severity] || 'info'
}
