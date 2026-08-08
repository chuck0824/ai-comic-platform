import test from 'node:test'
import assert from 'node:assert/strict'
import {
  STAGES,
  createWorkbenchState,
  evaluateActionPrecondition,
  requestStageTransition,
  updateStageTransitionProgress,
  completeStageTransition,
  beginGeneration,
  updateGenerationProgress,
  finishGeneration,
  acceptGeneration,
  discardGeneration
} from '../src/views/content-project/workbench/scriptWorkbenchModel.js'

test('workbench uses the approved eight-stage creative order', () => {
  assert.deepEqual(STAGES.map(stage => stage.key), [
    'creation_settings', 'novel_upload', 'novel_analysis', 'adaptation',
    'structured_script', 'script_body', 'review_revision', 'text_storyboard'
  ])
})

test('all actions stay clickable and explain unmet conditions', () => {
  assert.deepEqual(
    evaluateActionPrecondition({ selectedBlockId: null }, 'ai_continue'),
    {
      allowed: false,
      code: 'SCRIPT_BLOCK_REQUIRED',
      title: '请先选择正文块',
      message: '选择动作、对白或旁白正文块后才能执行此操作。',
      targetAction: 'focus_script_blocks'
    }
  )
})

test('transition reaches next stage only after persistence succeeds', () => {
  const state = createWorkbenchState()
  requestStageTransition(state, 'novel_upload')
  assert.equal(state.activeStage, 'creation_settings')
  assert.equal(state.transition.percentage, 0)
  updateStageTransitionProgress(state, 64)
  assert.equal(state.transition.percentage, 64)
  completeStageTransition(state, { persisted: true })
  assert.equal(state.activeStage, 'novel_upload')
})

test('failed stage persistence leaves the current stage active with transition feedback', () => {
  const state = createWorkbenchState()
  requestStageTransition(state, 'novel_upload')
  completeStageTransition(state, { persisted: false, message: '保存失败' })
  assert.equal(state.activeStage, 'creation_settings')
  assert.equal(state.transition.status, 'error')
  assert.equal(state.transition.message, '保存失败')
})

test('generation tracks selected model, estimate, progress, task, and failure details', () => {
  const state = createWorkbenchState()
  const task = beginGeneration(state, {
    id: 'task-7', model: { id: 'qwen-plus', name: 'Qwen Plus' }, estimatedPoints: 18, subtask: '分析人物关系'
  })
  assert.deepEqual(task, {
    id: 'task-7', status: 'running', modelId: 'qwen-plus', modelName: 'Qwen Plus',
    estimatedPoints: 18, actualPoints: null, progress: 0, subtask: '分析人物关系',
    cancelable: true, error: null
  })
  updateGenerationProgress(state, 'task-7', { percentage: 73, subtask: '生成章节结构' })
  assert.equal(state.tasks[0].progress, 73)
  assert.equal(state.tasks[0].subtask, '生成章节结构')
  finishGeneration(state, 'task-7', { status: 'failed', error: '模型超时', actualPoints: 2 })
  assert.equal(state.tasks[0].status, 'failed')
  assert.equal(state.tasks[0].error, '模型超时')
  assert.equal(state.tasks[0].actualPoints, 2)
})

test('accepting a generation records artifact, task, points, and impact', () => {
  const state = createWorkbenchState()
  beginGeneration(state, { id: 'task-8', model: { id: 'qwen-plus' }, estimatedPoints: 12 })
  finishGeneration(state, 'task-8', {
    status: 'completed', actualPoints: 10,
    artifact: { path: '06-剧本正文/EP01.md', version: 3, impact: '正文块已更新' }
  })
  const accepted = acceptGeneration(state, 'task-8')
  assert.deepEqual(accepted, {
    taskId: 'task-8', artifactPath: '06-剧本正文/EP01.md', artifactVersion: 3,
    estimatedPoints: 12, actualPoints: 10, impact: '正文块已更新'
  })
  assert.deepEqual(state.pointsRecords, [accepted])
})

test('discarding a generation preserves its task record without changing an artifact', () => {
  const state = createWorkbenchState()
  beginGeneration(state, { id: 'task-9', model: { id: 'demo-text', demo: true }, estimatedPoints: 99 })
  finishGeneration(state, 'task-9', {
    status: 'completed', actualPoints: 0,
    artifact: { path: '06-剧本正文/EP01.md', version: 4 }
  })
  discardGeneration(state, 'task-9')
  assert.equal(state.tasks[0].status, 'discarded')
  assert.equal(state.tasks[0].artifact, null)
  assert.equal(state.tasks[0].estimatedPoints, 0)
  assert.equal(state.pointsRecords.length, 0)
})
