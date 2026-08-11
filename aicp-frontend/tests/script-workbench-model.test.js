import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import * as workbenchModel from '../src/views/content-project/workbench/scriptWorkbenchModel.js'
import { useScriptWorkbench } from '../src/views/content-project/workbench/useScriptWorkbench.js'
import {
  STAGES,
  createWorkbenchState,
  evaluateActionPrecondition,
  requestStageTransition,
  updateStageTransitionProgress,
  completeStageTransition,
  canNavigateToStage,
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
  assert.deepEqual(STAGES.map(stage => stage.label), [
    '创作设置', '小说上传', '小说分析', '改编方案',
    '结构化文字剧本', '剧本正文', '审核修订', '文字分镜'
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

test('generation preserves before-after diff through result display, accept, and discard audit paths', () => {
  const acceptedState = createWorkbenchState()
  beginGeneration(acceptedState, { id: 'diff-accept', model: { id: 'qwen-plus' }, estimatedPoints: 12 })
  const acceptedResult = finishGeneration(acceptedState, 'diff-accept', {
    actualPoints: 10,
    artifact: { path: '04-改编方案/改编方案.md', version: 4, impact: '分集结构待更新' },
    diff: { summary: '增强首集冲突', before: '主角回家。', after: '主角回家却发现债主堵门。', changes: ['高压开场'] }
  })
  assert.deepEqual(acceptedResult.diff, {
    summary: '增强首集冲突', before: '主角回家。', after: '主角回家却发现债主堵门。', changes: ['高压开场']
  })
  assert.deepEqual(workbenchModel.generationDiffView(acceptedResult), {
    visible: true,
    summary: '增强首集冲突',
    before: '主角回家。',
    after: '主角回家却发现债主堵门。',
    changes: ['高压开场']
  })
  const accepted = acceptGeneration(acceptedState, 'diff-accept')
  assert.deepEqual(accepted.diff, acceptedResult.diff)
  assert.deepEqual(acceptedState.results[0].diff, acceptedResult.diff)

  const discardedState = createWorkbenchState()
  beginGeneration(discardedState, { id: 'diff-discard', model: { id: 'qwen-plus' }, estimatedPoints: 12 })
  finishGeneration(discardedState, 'diff-discard', {
    artifact: { path: '04-改编方案/改编方案.md', version: 5 },
    diff: acceptedResult.diff
  })
  const discarded = discardGeneration(discardedState, 'diff-discard')
  assert.equal(discarded.artifact, null)
  assert.deepEqual(discarded.diff, acceptedResult.diff)

  const drawerSource = readFileSync(new URL('../src/views/content-project/components/ActionResultDrawer.vue', import.meta.url), 'utf8')
  assert.match(drawerSource, /generationDiffView/)
  assert.match(drawerSource, /修订前/)
  assert.match(drawerSource, /修订后/)
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

test('stage navigation authorizes only explicitly entered stages regardless of display status', () => {
  const state = createWorkbenchState()
  state.stages[2].status = 'completed'
  state.stages[3].status = 'error'

  assert.equal(canNavigateToStage(state, 'novel_analysis'), false)
  assert.equal(canNavigateToStage(state, 'adaptation'), false)
  assert.equal(canNavigateToStage(state, 'creation_settings'), true)
})

test('stage transition rejects invalid, skipped, and stale persistence completions', () => {
  const state = createWorkbenchState()

  const invalid = requestStageTransition(state, 'not-a-stage')
  assert.equal(invalid.status, 'error')
  assert.equal(state.activeStage, 'creation_settings')

  const skipped = requestStageTransition(state, 'novel_analysis')
  assert.equal(skipped.status, 'error')
  assert.equal(state.activeStage, 'creation_settings')

  const completion = completeStageTransition(state, { persisted: true })
  assert.equal(completion.status, 'error')
  assert.equal(state.activeStage, 'creation_settings')
})

test('generation requires an explicitly selected model and only selected demos are free', () => {
  const state = createWorkbenchState()
  const missingModel = beginGeneration(state, { id: 'no-model' })

  assert.deepEqual(missingModel, {
    allowed: false,
    code: 'MODEL_REQUIRED',
    title: '请选择模型',
    message: '选择可用模型后才能开始生成。',
    targetAction: 'select_generation_model'
  })
  assert.equal(state.tasks.length, 0)

  const demo = beginGeneration(state, { id: 'selected-demo', model: { id: 'demo-text', demo: true }, estimatedPoints: 9 })
  assert.equal(demo.estimatedPoints, 0)
})

test('generation lifecycle is idempotent and prevents terminal-state rewrites or double charges', () => {
  const state = createWorkbenchState()
  beginGeneration(state, { id: 'task-idempotent', model: { id: 'qwen-plus' }, estimatedPoints: 12 })
  finishGeneration(state, 'task-idempotent', {
    artifact: { path: '06-剧本正文/EP01.md', version: 1 }, actualPoints: 9
  })
  const accepted = acceptGeneration(state, 'task-idempotent')

  const finishedAgain = finishGeneration(state, 'task-idempotent', {
    artifact: { path: '06-剧本正文/EP02.md', version: 2 }, actualPoints: 99
  })
  const acceptedAgain = acceptGeneration(state, 'task-idempotent')
  const discardedAfterAccept = discardGeneration(state, 'task-idempotent')

  assert.equal(accepted.actualPoints, 9)
  assert.equal(finishedAgain.allowed, false)
  assert.equal(acceptedAgain.allowed, false)
  assert.equal(discardedAfterAccept.allowed, false)
  assert.equal(state.results[0].artifact.path, '06-剧本正文/EP01.md')
  assert.equal(state.artifacts.length, 1)
  assert.equal(state.pointsRecords.length, 1)
})

test('paid generation never substitutes an estimate when actual credits are absent', () => {
  const state = createWorkbenchState()
  beginGeneration(state, { id: 'actual-required', model: { id: 'qwen-plus' }, estimatedPoints: 12 })
  finishGeneration(state, 'actual-required', {
    status: 'completed', artifact: { path: '/content-units/17/versions/176', version: 176 }
  })
  assert.equal(state.tasks[0].actualPoints, null)
  const accepted = acceptGeneration(state, 'actual-required')
  assert.equal(accepted.actualPoints, null)
})

test('overall progress counts completed stages and reaches 100 only after final completion', () => {
  const state = createWorkbenchState()
  assert.equal(typeof workbenchModel.getOverallProgress, 'function')
  assert.equal(typeof workbenchModel.completeFinalStage, 'function')
  assert.equal(workbenchModel.getOverallProgress(state), 0)

  for (const stage of STAGES.slice(1)) {
    requestStageTransition(state, stage.key)
    completeStageTransition(state, { persisted: true })
  }

  assert.equal(workbenchModel.getOverallProgress(state), 88)
  workbenchModel.completeFinalStage(state, { persisted: true })
  assert.equal(workbenchModel.getOverallProgress(state), 100)
})

test('non-demo generation requires a positive point estimate while explicit demos remain free', () => {
  const invalidEstimates = [undefined, Number.NaN, 0, -3]

  for (const estimatedPoints of invalidEstimates) {
    const state = createWorkbenchState()
    const rejected = beginGeneration(state, {
      id: `invalid-${String(estimatedPoints)}`,
      model: { id: 'qwen-plus' },
      estimatedPoints
    })
    assert.deepEqual(rejected, {
      allowed: false,
      code: 'POINT_ESTIMATE_REQUIRED',
      title: '请确认积分预估',
      message: '非演示模型需要大于 0 的预估积分后才能开始生成。',
      targetAction: 'set_generation_point_estimate'
    })
    assert.equal(state.tasks.length, 0)
  }

  const paidState = createWorkbenchState()
  const paid = beginGeneration(paidState, {
    id: 'positive-estimate', model: { id: 'qwen-plus' }, estimatedPoints: 7
  })
  assert.equal(paid.estimatedPoints, 7)
  assert.equal(paidState.tasks.length, 1)

  const demoState = createWorkbenchState()
  const demo = beginGeneration(demoState, {
    id: 'free-demo', model: { id: 'demo-text', demo: true }, estimatedPoints: 0
  })
  assert.equal(demo.estimatedPoints, 0)
  assert.equal(demoState.tasks.length, 1)
})

function reachFinalStage(state) {
  for (const stage of STAGES.slice(1)) {
    requestStageTransition(state, stage.key)
    completeStageTransition(state, { persisted: true })
  }
}

test('final-stage adapter persists before completing the final stage', async () => {
  const calls = []
  const workbench = useScriptWorkbench({
    persistFinalStage: async stageKey => {
      calls.push(stageKey)
      return { persisted: true, message: '最终阶段已保存' }
    }
  })
  reachFinalStage(workbench.state)
  assert.equal(workbench.progress.value, 88)

  const completed = await workbench.completeFinalStageWithPersistence()

  assert.deepEqual(calls, ['text_storyboard'])
  assert.equal(completed.key, 'text_storyboard')
  assert.equal(workbench.progress.value, 100)
})

test('final-stage adapter keeps progress at 88 when persistence fails', async () => {
  const workbench = useScriptWorkbench({
    persistFinalStage: async () => ({ persisted: false, message: '最终阶段保存失败' })
  })
  reachFinalStage(workbench.state)

  const rejected = await workbench.completeFinalStageWithPersistence()

  assert.equal(rejected.allowed, false)
  assert.equal(rejected.code, 'FINAL_STAGE_PERSISTENCE_FAILED')
  assert.equal(workbench.state.completedStages.includes('text_storyboard'), false)
  assert.equal(workbench.progress.value, 88)
})
