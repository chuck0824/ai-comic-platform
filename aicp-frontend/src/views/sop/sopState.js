export const RESULT_LABELS = {
  PASS: '通过',
  WARNING: '告警',
  BLOCKED: '阻断',
  NOT_READY: '待配置',
  ERROR: '检查异常',
}

export const RESULT_COLORS = {
  PASS: 'success',
  WARNING: 'warning',
  BLOCKED: 'danger',
  NOT_READY: 'info',
  ERROR: 'danger',
}

export const SEVERITY_LABELS = {
  P0: 'P0-阻断',
  P1: 'P1-严重',
  P2: 'P2-告警',
  P3: 'P3-提示',
}

export const SEVERITY_COLORS = {
  P0: 'danger',
  P1: 'danger',
  P2: 'warning',
  P3: 'info',
}

export const OVERALL_STATUS_LABELS = {
  GREEN: '已通过',
  YELLOW: '有告警',
  RED: '已阻断',
}

export const OVERALL_STATUS_COLORS = {
  GREEN: 'success',
  YELLOW: 'warning',
  RED: 'danger',
}

export const WORK_ORDER_STATUS_LABELS = {
  OPEN: '待分配',
  ASSIGNED: '已分配',
  FIXING: '修复中',
  PENDING_REVIEW: '待审核',
  PASSED: '已通过',
  REOPENED: '已重开',
  CANCELED: '已取消',
}

export const WORK_ORDER_STATUS_COLORS = {
  OPEN: 'info',
  ASSIGNED: 'warning',
  FIXING: 'warning',
  PENDING_REVIEW: '',
  PASSED: 'success',
  REOPENED: 'danger',
  CANCELED: 'info',
}

// Valid transitions for each status
const VALID_TRANSITIONS = {
  OPEN: ['ASSIGNED', 'CANCELED'],
  ASSIGNED: ['FIXING', 'CANCELED'],
  FIXING: ['PENDING_REVIEW'],
  PENDING_REVIEW: ['PASSED', 'REOPENED'],
  REOPENED: ['FIXING', 'CANCELED'],
  PASSED: [],
  CANCELED: [],
}

export function canTransitionWorkOrder(fromStatus, toStatus) {
  const allowed = VALID_TRANSITIONS[fromStatus]
  return allowed ? allowed.includes(toStatus) : false
}

export function mapSopReport(report = {}) {
  const results = report.results || []
  const by = (result) => results.filter((item) => item.result === result)
  return {
    ...report,
    groups: {
      passed: by('PASS'),
      warnings: by('WARNING'),
      blocked: by('BLOCKED'),
      notReady: by('NOT_READY'),
      errors: by('ERROR'),
    },
    canEnterProduction: report.gateAllowed === true && report.status !== 'STALE',
  }
}

export function serializeSopScope(scope) {
  const value = {}
  if (scope.contentUnitId) value.content_unit_id = scope.contentUnitId
  if (scope.canvasProjectId) value.canvas_project_id = scope.canvasProjectId
  return value
}

export function overallStatusLabel(status) {
  return OVERALL_STATUS_LABELS[status] || '--'
}

export function overallStatusColor(status) {
  return OVERALL_STATUS_COLORS[status] || 'info'
}

export function resultLabel(result) {
  return RESULT_LABELS[result] || '--'
}

export function resultColor(result) {
  return RESULT_COLORS[result] || 'info'
}

export function severityLabel(severity) {
  return SEVERITY_LABELS[severity] || '--'
}

export function severityColor(severity) {
  return SEVERITY_COLORS[severity] || 'info'
}

export function workOrderStatusLabel(status) {
  return WORK_ORDER_STATUS_LABELS[status] || '--'
}

export function workOrderStatusColor(status) {
  return WORK_ORDER_STATUS_COLORS[status] || 'info'
}
