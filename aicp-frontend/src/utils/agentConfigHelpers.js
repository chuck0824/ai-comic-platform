// Agent 配置中心 — 纯函数工具（共享于配置中心和业务页面轻量入口）

export const createWizardState = () => ({
  step: 'blueprint',
  blueprintId: null,
  identity: { name: '', description: '' },
  parameters: {},
  editablePrompt: '',
  successfulTestRunId: null
})

export const canPublish = (draft) =>
  draft?.valid === true && Boolean(draft.successfulTestRunId)

export const bindingSourceLabel = (source) =>
  ({
    PROJECT: '项目默认',
    USER: '用户默认',
    SYSTEM: '系统默认',
    TEMPORARY: '单次调整'
  })[source] || '未解析'

export const filterDefinitions = (rows, { roleType, status }) => {
  let result = rows
  if (roleType) {
    result = result.filter((r) => r.roleType === roleType)
  }
  if (status) {
    result = result.filter((r) => r.lifecycleStatus === status)
  }
  return result
}

export const versionStatusLabel = (status) =>
  ({
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档'
  })[status] || status

export const lifecycleStatusLabel = (status) =>
  ({
    ACTIVE: '启用',
    ARCHIVED: '已归档'
  })[status] || status

export const roleTypeLabel = (role) =>
  ({
    HOOK: '钩子',
    SCREENWRITER: '编剧',
    STORYBOARD: '分镜',
    DIRECTOR: '导演'
  })[role] || role

export const testRunStatusLabel = (status) =>
  ({
    PENDING: '等待中',
    RUNNING: '运行中',
    SUCCEEDED: '成功',
    FAILED: '失败'
  })[status] || status

export const nextWizardStep = (current) => {
  const steps = ['blueprint', 'identity', 'configure', 'test']
  const idx = steps.indexOf(current)
  return idx >= 0 && idx < steps.length - 1 ? steps[idx + 1] : null
}

export const isWizardComplete = (state) =>
  state.step === 'test' && state.successfulTestRunId !== null

export const wizardStepLabel = (step) =>
  ({
    blueprint: '选择框架',
    identity: '基本信息',
    configure: '配置参数',
    test: '试跑与发布'
  })[step] || step
