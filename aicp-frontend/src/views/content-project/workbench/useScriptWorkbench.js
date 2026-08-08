import { computed, reactive, ref } from 'vue'
import {
  STAGES,
  acceptGeneration,
  beginGeneration,
  canNavigateToStage,
  completeStageTransition,
  createWorkbenchState,
  discardGeneration,
  evaluateActionPrecondition,
  finishGeneration,
  navigateToEnteredStage,
  requestStageTransition,
  updateGenerationProgress,
  updateStageTransitionProgress
} from './scriptWorkbenchModel'

/** Vue adapter around the pure workbench state; callers provide real persistence. */
export function useScriptWorkbench({ persistStage } = {}) {
  const state = reactive(createWorkbenchState())
  const guidance = ref(null)
  const generationTask = ref(null)
  const resultTaskId = ref(null)

  const activeStage = computed(() => state.activeStage)
  const stages = computed(() => state.stages)
  const progress = computed(() => Math.round((state.enteredStages.length / STAGES.length) * 100))
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
    requestStageTransition(state, targetStage)
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

  function navigate(targetStage) {
    return navigateToEnteredStage(state, targetStage)
  }

  return {
    state, activeStage, stages, progress, guidance, generationTaskRecord, result,
    runAction, beginGeneration: begin, updateGenerationProgress: updateGeneration,
    finishGeneration: finish, acceptGeneration: accept, discardGeneration: discard,
    transition, navigate, canNavigateToStage: targetStage => canNavigateToStage(state, targetStage)
  }
}
