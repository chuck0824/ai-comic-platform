// ===== Status Label Maps =====

export const CONTENT_STATUS_LABELS = {
  draft: '草稿',
  reviewing: '审核中',
  needs_revision: '待修改',
  approved: '已通过',
  locked: '已锁稿'
}

export const PRODUCTION_STATUS_LABELS = {
  not_started: '未开始',
  storyboarding: '分镜中',
  canvas_producing: '画布生产中',
  completed: '已完成'
}

export const COMMERCIAL_STATUS_LABELS = {
  not_listed: '未上架',
  listing_review: '上架审核中',
  listed: '已上架',
  delisted: '已下架'
}

export const LIFECYCLE_STATUS_LABELS = {
  active: '活跃',
  archived: '已归档'
}

// ===== Primary Action Labels and Routes =====

export const PRIMARY_LABELS = {
  continue_creation: '继续创作',
  view_review: '查看审核进度',
  resolve_review: '处理审核意见',
  lock_version: '确认锁稿',
  create_storyboard: '制作分镜',
  view_production: '查看生产进度',
  view_result: '查看成果',
  restore: '恢复项目'
}

export function primaryActionRoute(project) {
  switch (project.primary_action) {
    case 'continue_creation': return `/script-gen/${project.id}/workspace`
    case 'view_review':
    case 'resolve_review':
    case 'lock_version': return `/warehouse/${project.id}?tab=review`
    case 'create_storyboard': return `/warehouse/${project.id}?tab=storyboard`
    case 'view_production': return `/warehouse/${project.id}?tab=production`
    case 'view_result': return `/warehouse/${project.id}?tab=production`
    default: return `/warehouse/${project.id}`
  }
}

// ===== Query Builder =====

export function buildWarehouseQuery(filters = {}) {
  const mapping = {
    page: 'page', pageSize: 'page_size', keyword: 'keyword',
    creationMode: 'creation_mode', sourceMode: 'source_mode',
    contentStatus: 'content_status', productionStatus: 'production_status',
    commercialStatus: 'commercial_status', lifecycleStatus: 'lifecycle_status',
    sort: 'sort'
  }
  return Object.entries(mapping).reduce((query, [source, target]) => {
    const value = filters[source]
    if (value !== undefined && value !== null && value !== '') query[target] = value
    return query
  }, {})
}

// ===== Project Card View Model =====

export function projectCardViewModel(project) {
  return {
    statuses: [
      { axis: 'content', value: project.content_status, label: CONTENT_STATUS_LABELS[project.content_status] || project.content_status },
      { axis: 'production', value: project.production_status, label: PRODUCTION_STATUS_LABELS[project.production_status] || project.production_status },
      { axis: 'commercial', value: project.commercial_status, label: COMMERCIAL_STATUS_LABELS[project.commercial_status] || project.commercial_status }
    ],
    primaryLabel: PRIMARY_LABELS[project.primary_action] || '查看详情',
    primaryRoute: primaryActionRoute(project),
    archived: project.lifecycle_status === 'archived'
  }
}

// ===== Creation Mode Labels =====

export const CREATION_MODE_LABELS = {
  short_drama: '短剧',
  long_form: '长剧',
  tvc: 'TVC'
}

export const SOURCE_MODE_LABELS = {
  ai_manual: 'AI创作',
  uploaded: '上传'
}
