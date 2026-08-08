export const STAGES = [
  { key: 'creation_settings', label: '创作设置' },
  { key: 'novel_upload', label: '小说上传' },
  { key: 'novel_analysis', label: '小说分析' },
  { key: 'adaptation', label: '改编方案' },
  { key: 'structured_script', label: '结构化剧本' },
  { key: 'script_body', label: '剧本正文' },
  { key: 'review_revision', label: '审阅与修订' },
  { key: 'text_storyboard', label: '文字分镜' }
]

const STAGE_KEYS = new Set(STAGES.map(stage => stage.key))

function result(allowed = true, code = null, title = '', message = '', targetAction = null) {
  return { allowed, code, title, message, targetAction }
}

function findTask(state, taskId) {
  return state.tasks.find(task => task.id === taskId) || null
}

function findResult(state, taskId) {
  return state.results.find(item => item.taskId === taskId) || null
}

function isDemoModel(model = {}) {
  return model.demo === true || model.isDemo === true || String(model.id || model.modelId || '').startsWith('demo')
}

function generationModel(model = {}) {
  return {
    id: model.id ?? model.modelId ?? 'demo-text',
    name: model.name ?? model.modelName ?? model.id ?? model.modelId ?? '演示模型',
    demo: isDemoModel(model)
  }
}

function stageIndex(key) {
  return STAGES.findIndex(stage => stage.key === key)
}

function clampPercentage(value) {
  return Math.max(0, Math.min(100, Number(value) || 0))
}

function refreshStageStatuses(state) {
  const activeIndex = stageIndex(state.activeStage)
  state.stages.forEach((stage, index) => {
    if (stage.key === state.activeStage) stage.status = 'current'
    else if (state.enteredStages.includes(stage.key) && index < activeIndex) stage.status = 'completed'
    else if (stage.status !== 'error') stage.status = 'pending'
  })
}

/** Returns an explanation for unmet business conditions without disabling an action. */
export function evaluateActionPrecondition(context = {}, action) {
  if (context.projectArchived) {
    return result(false, 'PROJECT_ARCHIVED', '项目已归档', '归档项目仅可查看，不能执行此操作。', 'view_project')
  }
  if (action === 'ai_continue' && !context.selectedBlockId) {
    return result(false, 'SCRIPT_BLOCK_REQUIRED', '请先选择正文块', '选择动作、对白或旁白正文块后才能执行此操作。', 'focus_script_blocks')
  }
  if (['generate', 'begin_generation'].includes(action) && !context.model) {
    return result(false, 'MODEL_REQUIRED', '请选择模型', '选择可用模型后才能开始生成。', 'select_generation_model')
  }
  if (action === 'novel_analysis' && !context.novelUploaded) {
    return result(false, 'NOVEL_UPLOAD_REQUIRED', '请先上传小说', '上传并解析小说文件后才能开始分析。', 'focus_novel_upload')
  }
  if (action === 'accept_generation' && !context.generationResult?.artifact) {
    return result(false, 'GENERATION_RESULT_REQUIRED', '暂无可采用结果', '等待生成任务产出有效结果后再采用。', 'focus_generation_result')
  }
  return result()
}

/** Creates the serializable source of truth consumed by the workbench and feedback UI. */
export function createWorkbenchState() {
  const initialStage = STAGES[0].key
  return {
    activeStage: initialStage,
    enteredStages: [initialStage],
    stages: STAGES.map(stage => ({ ...stage, status: stage.key === initialStage ? 'current' : 'pending' })),
    transition: null,
    tasks: [],
    results: [],
    artifacts: [],
    pointsRecords: [],
    demoTaskIds: []
  }
}

/** Starts a persistence-backed stage transition without moving the active stage yet. */
export function requestStageTransition(state, targetStage) {
  if (!STAGE_KEYS.has(targetStage)) {
    state.transition = { targetStage, percentage: 0, status: 'error', message: '未知创作阶段' }
    return state.transition
  }
  state.transition = { targetStage, percentage: 0, status: 'persisting', message: '正在保存阶段进度…' }
  return state.transition
}

export function updateStageTransitionProgress(state, percentage) {
  if (!state.transition || state.transition.status !== 'persisting') return null
  state.transition.percentage = clampPercentage(percentage)
  return state.transition
}

/** Applies a requested transition only when the caller confirms persistence succeeded. */
export function completeStageTransition(state, outcome = {}) {
  if (!state.transition) return null
  if (!outcome.persisted) {
    state.transition.status = 'error'
    state.transition.message = outcome.message || '保存失败，请重试。'
    return state.transition
  }
  const targetStage = state.transition.targetStage
  state.activeStage = targetStage
  if (!state.enteredStages.includes(targetStage)) state.enteredStages.push(targetStage)
  refreshStageStatuses(state)
  state.transition.percentage = 100
  state.transition.status = 'completed'
  state.transition.message = outcome.message || '阶段已保存'
  return state.transition
}

/** A rail may navigate only to a stage the user has already entered. */
export function canNavigateToStage(state, targetStage) {
  return STAGE_KEYS.has(targetStage) && state.enteredStages.includes(targetStage)
}

export function navigateToEnteredStage(state, targetStage) {
  if (!canNavigateToStage(state, targetStage)) return false
  state.activeStage = targetStage
  refreshStageStatuses(state)
  return true
}

/** Begins one task record shared by progress, result, acceptance, and discard. */
export function beginGeneration(state, input = {}) {
  const model = generationModel(input.model)
  const task = {
    id: input.id ?? `generation-${state.tasks.length + 1}`,
    status: 'running',
    modelId: model.id,
    modelName: model.name,
    estimatedPoints: model.demo ? 0 : Number(input.estimatedPoints) || 0,
    actualPoints: null,
    progress: 0,
    subtask: input.subtask || '正在准备生成任务',
    cancelable: input.cancelable !== false,
    error: null
  }
  state.tasks.push(task)
  if (model.demo) state.demoTaskIds.push(task.id)
  return task
}

export function updateGenerationProgress(state, taskId, update = {}) {
  const task = findTask(state, taskId)
  if (!task || task.status !== 'running') return null
  task.progress = clampPercentage(update.percentage ?? update.progress)
  if (update.subtask != null) task.subtask = update.subtask
  if (update.cancelable != null) task.cancelable = update.cancelable
  return task
}

/** Stores completed or failed output as a result; it never treats it as accepted output. */
export function finishGeneration(state, taskId, outcome = {}) {
  const task = findTask(state, taskId)
  if (!task) return null
  const status = outcome.status || 'completed'
  task.status = status
  task.cancelable = false
  task.progress = status === 'completed' ? 100 : task.progress
  task.actualPoints = state.demoTaskIds.includes(taskId) ? 0 : (outcome.actualPoints ?? task.estimatedPoints)
  task.error = outcome.error ?? null
  const existing = findResult(state, taskId)
  const item = {
    taskId,
    status,
    artifact: outcome.artifact ?? null,
    error: task.error,
    impact: outcome.artifact?.impact ?? outcome.impact ?? null
  }
  if (existing) Object.assign(existing, item)
  else state.results.push(item)
  return item
}

/** Accepts successful output and records the artifact plus actual points once. */
export function acceptGeneration(state, taskId) {
  const task = findTask(state, taskId)
  const generated = findResult(state, taskId)
  if (!task || !generated || generated.status !== 'completed' || !generated.artifact) return null
  const record = {
    taskId,
    artifactPath: generated.artifact.path ?? null,
    artifactVersion: generated.artifact.version ?? null,
    estimatedPoints: task.estimatedPoints,
    actualPoints: task.actualPoints,
    impact: generated.impact
  }
  task.status = 'accepted'
  task.artifact = generated.artifact
  generated.status = 'accepted'
  state.artifacts.push(record)
  state.pointsRecords.push(record)
  return record
}

/** Discarding keeps audit/task history but never changes a downstream artifact. */
export function discardGeneration(state, taskId) {
  const task = findTask(state, taskId)
  const generated = findResult(state, taskId)
  if (!task || !generated) return null
  task.status = 'discarded'
  task.artifact = null
  generated.status = 'discarded'
  generated.artifact = null
  return generated
}
