import { computed, reactive, ref } from 'vue'
import {
  STAGES,
  acceptGeneration,
  beginGeneration,
  canNavigateToStage,
  completeFinalStage as completeFinalStageModel,
  completeStageTransition,
  createWorkbenchState,
  discardGeneration,
  evaluateActionPrecondition,
  finishGeneration,
  getOverallProgress,
  navigateToEnteredStage,
  requestStageTransition,
  updateGenerationProgress,
  updateStageTransitionProgress
} from './scriptWorkbenchModel.js'

function finalStagePersistenceFailure(message) {
  return {
    allowed: false,
    code: 'FINAL_STAGE_PERSISTENCE_FAILED',
    title: '最终阶段保存失败',
    message: message || '最终阶段保存失败，请重试。',
    targetAction: 'retry_final_stage_persistence'
  }
}

/** Persists the terminal stage through an injected adapter before completing its pure-model state. */
export async function completeFinalStageWithPersistence(state, persistFinalStage) {
  const finalStage = STAGES.at(-1).key
  if (state.activeStage !== finalStage) {
    return {
      allowed: false,
      code: 'FINAL_STAGE_REQUIRED',
      title: '请先进入最终阶段',
      message: '仅文字分镜阶段可以完成整个创作流程。',
      targetAction: 'focus_text_storyboard'
    }
  }
  try {
    if (typeof persistFinalStage !== 'function') return finalStagePersistenceFailure('最终阶段保存服务不可用。')
    const response = await persistFinalStage(finalStage)
    if (response?.persisted !== true) return finalStagePersistenceFailure(response?.message)
    return completeFinalStageModel(state, { persisted: true }) || finalStagePersistenceFailure(response.message)
  } catch (error) {
    return finalStagePersistenceFailure(error?.message)
  }
}

/** Vue adapter around the pure workbench state; callers provide real persistence. */
export function useScriptWorkbench({ persistStage, persistFinalStage } = {}) {
  const state = reactive(createWorkbenchState())
  const guidance = ref(null)
  const generationTask = ref(null)
  const resultTaskId = ref(null)

  const activeStage = computed(() => state.activeStage)
  const stages = computed(() => state.stages)
  const progress = computed(() => getOverallProgress(state))
  const generationTaskRecord = computed(() => generationTask.value
    ? state.tasks.find(task => task.id === generationTask.value) || null
    : null)
  const result = computed(() => resultTaskId.value
    ? state.results.find(item => item.taskId === resultTaskId.value) || null
    : null)

  function runAction(context, action) {
    const condition = evaluateActionPrecondition(context, action)
    guidance.value = condition.allowed ? null : condition
    return condition
  }

  function begin(input) {
    const task = beginGeneration(state, input)
    if (!task?.id) {
      guidance.value = task?.allowed === false ? task : null
      return task
    }
    generationTask.value = task.id
    return task
  }

  function updateGeneration(taskId, update) {
    return updateGenerationProgress(state, taskId, update)
  }

  function finish(taskId, outcome) {
    const item = finishGeneration(state, taskId, outcome)
    generationTask.value = null
    resultTaskId.value = taskId
    return item
  }

  function accept(taskId = resultTaskId.value) {
    const condition = runAction({ generationResult: state.results.find(item => item.taskId === taskId) }, 'accept_generation')
    if (!condition.allowed) return condition
    const record = acceptGeneration(state, taskId)
    if (record) resultTaskId.value = null
    return record
  }

  function discard(taskId = resultTaskId.value) {
    const item = discardGeneration(state, taskId)
    if (item) resultTaskId.value = null
    return item
  }

  async function transition(targetStage) {
    const request = requestStageTransition(state, targetStage)
    if (request.status !== 'persisting') return request
    updateStageTransitionProgress(state, 15)
    try {
      if (!persistStage) throw new Error('阶段保存服务不可用')
      const response = await persistStage(targetStage)
      updateStageTransitionProgress(state, 85)
      return completeStageTransition(state, { persisted: response?.persisted === true, message: response?.message })
    } catch (error) {
      return completeStageTransition(state, { persisted: false, message: error?.message || '保存失败，请重试。' })
    }
  }

  function completeFinalStage() {
    return completeFinalStageWithPersistence(state, persistFinalStage)
  }

  function navigate(targetStage) {
    return navigateToEnteredStage(state, targetStage)
  }

  return {
    state, activeStage, stages, progress, guidance, generationTaskRecord, result,
    runAction, beginGeneration: begin, updateGenerationProgress: updateGeneration,
    finishGeneration: finish, acceptGeneration: accept, discardGeneration: discard,
    transition, completeFinalStageWithPersistence: completeFinalStage,
    navigate, canNavigateToStage: targetStage => canNavigateToStage(state, targetStage)
  }
}
