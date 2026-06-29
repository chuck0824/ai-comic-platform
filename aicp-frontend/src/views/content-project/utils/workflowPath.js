export function currentStage(stages = []) {
  return stages.find(stage => stage.status === 'current') ||
    stages.find(stage => !['completed', 'skipped', 'optional'].includes(stage.status)) || null
}

export function primaryAction(stages = []) {
  return currentStage(stages)?.primary_action || '返回项目'
}

export function stageLabel(key) {
  const labels = {
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
  return labels[key] || key
}

export function stageStatusVariant(status) {
  return {
    completed: 'success',
    current: 'primary',
    pending: 'info',
    optional: 'warning',
    skipped: '',
    locked: 'danger'
  }[status] || 'info'
}
