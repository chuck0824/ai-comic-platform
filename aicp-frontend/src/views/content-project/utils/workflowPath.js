export function currentStage(stages = []) {
  return stages.find(stage => stage.status === 'current') ||
    stages.find(stage => !['completed', 'skipped', 'optional'].includes(stage.status)) || null
}

export function primaryAction(stages = []) {
  return currentStage(stages)?.primary_action || '返回项目'
}

const LEGACY_LABELS = {
  story_seed: '故事种子',
  import_review: '导入审核',
  characters: '角色设定',
  synopsis: '梗概',
  outline: '大纲',
  content: '正文',
  review: '审核',
  destination: '内容去向',
  storyboard: '分镜'
}

const EIGHT_STAGE_LABELS = {
  creation_settings: '创作设置',
  novel_upload: '小说上传',
  novel_analysis: '小说分析',
  adaptation: '改编方案',
  structured_script: '结构化剧本',
  script_body: '剧本正文',
  review_revision: '审核修订',
  text_storyboard: '文字分镜'
}

const LEGACY_TO_EIGHT = {
  story_seed: 'creation_settings',
  import_review: 'novel_upload',
  characters: 'novel_analysis',
  synopsis: 'novel_analysis',
  outline: 'structured_script',
  content: 'script_body',
  review: 'review_revision',
  destination: 'text_storyboard',
  storyboard: 'text_storyboard'
}

export function normalizeStageKey(key) {
  if (!key) return 'creation_settings'
  return LEGACY_TO_EIGHT[key] || key
}

export function stageLabel(key) {
  const normalized = normalizeStageKey(key)
  return EIGHT_STAGE_LABELS[normalized] || LEGACY_LABELS[key] || key
}

/** 列表卡片展示：优先服务端检查点摘要。 */
export function listWorkflowLabel(project = {}) {
  if (project.workflow_current_stage_label) return project.workflow_current_stage_label
  if (project.workflow_current_stage_key) return stageLabel(project.workflow_current_stage_key)
  return stageLabel(project.last_stage_key)
}

export function listWorkflowProgress(project = {}) {
  const value = project.workflow_progress
  return Number.isFinite(Number(value)) ? Number(value) : null
}

export function stageStatusVariant(status) {
  return {
    completed: 'success',
    current: 'primary',
    pending: 'info',
    optional: 'warning',
    skipped: '',
    locked: 'danger',
    possibly_stale: 'warning',
    regen_required: 'danger'
  }[String(status || '').toLowerCase()] || 'info'
}
