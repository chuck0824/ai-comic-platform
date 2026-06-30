/**
 * Pure helpers for canvas project views.
 * No side effects, no API calls — just data transforms and validation.
 */

export const STATUS_LABELS = {
  draft: '草稿',
  editing: '编辑中',
  generating: '生成中',
  composing: '合成中',
  completed: '已完成',
  archived: '已归档'
}

export const PURPOSE_LABELS = {
  official: '正式方案',
  alternative: '备选方案',
  experiment: '实验方案'
}

export const SEVERITY_COLORS = {
  blocking: 'danger',
  warning: 'warning',
  info: 'info',
  none: ''
}

/**
 * Build the canvas editor route.
 */
export function canvasRoute(canvas) {
  if (!canvas?.uuid) return '/canvas-projects'
  return `/canvas/${canvas.uuid}`
}

/**
 * Serialize filter state to API query params.
 * Omits empty/default values to keep URLs clean.
 */
export function buildQueryParams({ page, pageSize, status, mode, keyword, contentProjectId } = {}) {
  const params = {}
  if (page && page > 1) params.page = page
  if (pageSize && pageSize !== 20) params.page_size = pageSize
  if (status) params.status = status
  if (mode) params.creation_mode = mode
  if (keyword) params.keyword = keyword
  if (contentProjectId) params.content_project_id = contentProjectId
  return params
}

/**
 * Validate canvas creation draft. Returns array of missing field keys.
 */
export function validateCanvasDraft(draft) {
  const required = [
    'name', 'contentProjectId', 'productionUnitType',
    'productionUnitId', 'sourceContentVersionId',
    'sourceStoryboardVersionId', 'purpose'
  ]
  return required.filter(f => !draft[f])
}

/**
 * Build deterministic idempotency key.
 */
export function buildIdempotencyKey(userId, contentProjectId, productionUnitId,
                                     contentVersionId, storyboardVersionId, purpose) {
  return `canvas-create:${userId}:${contentProjectId}:${productionUnitId}:${contentVersionId}:${storyboardVersionId}:${purpose}`
}

/**
 * Determine which actions are available for a canvas based on its status.
 */
export function canvasActions(canvas) {
  const status = canvas?.status
  return {
    canEdit: status && status !== 'archived' && status !== 'completed',
    canCopy: status && status !== 'archived',
    canMove: status && status !== 'archived' && status !== 'completed',
    canArchive: status && status !== 'archived' && status !== 'completed',
    canRestore: status === 'archived',
    canDelete: status && status !== 'completed' && status !== 'archived'
  }
}

/**
 * Resolve workspace tab from route query.
 */
export function workspaceTab(tabParam) {
  return tabParam === 'canvas' ? 'canvas' : 'workflow'
}

/**
 * Build breadcrumb items for canvas pages.
 */
export function buildBreadcrumb(page, { projectName, canvasName, projectId, referrer } = {}) {
  const home = { label: '首页', path: '/' }

  if (page === 'canvas-center') {
    return [home, { label: '画布项目中心', path: null }]
  }

  if (page === 'canvas-editor') {
    if (referrer === 'project-canvas' && projectName && projectId) {
      return [
        home,
        { label: '剧本创作', path: '/script-gen' },
        { label: projectName, path: `/script-gen/${projectId}/workspace?tab=canvas` },
        { label: canvasName || '画布编辑器', path: null }
      ]
    }
    // Default: from global center
    return [
      home,
      { label: '画布项目中心', path: '/canvas-projects' },
      { label: canvasName || '画布编辑器', path: null }
    ]
  }

  return [home]
}
