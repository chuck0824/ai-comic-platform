/**
 * R2-A 检查点投影适配：把服务端 stage-checkpoints 投影成工作台可用的阶段状态。
 * 仅在 scriptStageFeatures().stageTruthEnabled 时由调用方启用。
 */

import { STAGES } from './scriptWorkbenchModel.js'

const STAGE_KEYS = STAGES.map(stage => stage.key)

function stageField(stage, camel, snake = camel.replace(/[A-Z]/g, m => `_${m.toLowerCase()}`)) {
  return stage?.[camel] ?? stage?.[snake]
}

/** 服务端七态 → 侧栏 status */
export function mapCheckpointStateToRailStatus(state, isActive) {
  if (isActive) return 'current'
  switch (state) {
    case 'COMPLETED':
    case 'LOCKED':
      return state === 'LOCKED' ? 'locked' : 'completed'
    case 'BLOCKED':
      return 'error'
    case 'POSSIBLY_STALE':
      return 'possibly_stale'
    case 'REGEN_REQUIRED':
      return 'regen_required'
    case 'IN_PROGRESS':
      return 'in_progress'
    default:
      return 'pending'
  }
}

export function projectionToStageStatuses(projection) {
  const stages = projection?.stages || []
  const completed = []
  const entered = []
  const byKey = {}
  for (const stage of stages) {
    const key = stageField(stage, 'stageKey', 'stage_key')
    const state = stage.state
    byKey[key] = stage
    if (state === 'COMPLETED' || state === 'LOCKED') {
      completed.push(key)
      entered.push(key)
    } else if (state === 'IN_PROGRESS' || state === 'BLOCKED'
        || state === 'POSSIBLY_STALE' || state === 'REGEN_REQUIRED') {
      entered.push(key)
    }
  }
  // 保证至少包含第一个已进入阶段的顺序前缀（导航需要）
  const orderedEntered = STAGE_KEYS.filter(key => entered.includes(key))
  return {
    completedStages: STAGE_KEYS.filter(key => completed.includes(key)),
    enteredStages: orderedEntered.length ? orderedEntered : [STAGE_KEYS[0]],
    stagesByKey: byKey,
    projectRevision: projection?.project_revision ?? projection?.projectRevision,
    lastStageKey: projection?.last_stage_key ?? projection?.lastStageKey,
    stageTruthEnabled: projection?.stage_truth_enabled ?? projection?.stageTruthEnabled
  }
}

/** 将服务端投影写入 workbench state（破坏性更新 completed/entered/statuses） */
export function applyStageTruthProjection(state, projection, activeStage) {
  const mapped = projectionToStageStatuses(projection)
  const safeActive = STAGE_KEYS.includes(activeStage)
    ? activeStage
    : (mapped.lastStageKey && STAGE_KEYS.includes(mapped.lastStageKey) ? mapped.lastStageKey : STAGE_KEYS[0])
  state.activeStage = safeActive
  state.completedStages = [...mapped.completedStages]
  const entered = new Set(mapped.enteredStages)
  entered.add(safeActive)
  state.enteredStages = STAGE_KEYS.filter(key => entered.has(key))
  state.stages = STAGES.map(stage => {
    const checkpoint = mapped.stagesByKey[stage.key]
    const serverState = checkpoint?.state
    return {
      ...stage,
      status: mapCheckpointStateToRailStatus(serverState, stage.key === safeActive),
      checkpointRevision: stageField(checkpoint, 'checkpointRevision', 'checkpoint_revision') ?? 0,
      serverState: serverState || 'NOT_STARTED',
      allowedActions: checkpoint?.allowed_actions || checkpoint?.allowedActions || []
    }
  })
  state.stageTruth = {
    enabled: true,
    projectRevision: mapped.projectRevision,
    stagesByKey: mapped.stagesByKey,
    lastStageKey: mapped.lastStageKey
  }
  return state
}

export function resolveWorkspaceStageFromTruth({ enteredStages = [], lastStageKey, queryStage } = {}) {
  const entered = STAGE_KEYS.filter(key => enteredStages.includes(key))
  const fallback = STAGE_KEYS.includes(lastStageKey) ? lastStageKey : (entered.at(-1) || STAGE_KEYS[0])
  const maxIndex = Math.max(STAGE_KEYS.indexOf(fallback), 0)
  const queryIndex = STAGE_KEYS.indexOf(queryStage)
  if (queryIndex >= 0 && entered.includes(queryStage) && queryIndex <= maxIndex + 1) {
    // 允许进入已 entered 的阶段；越级仍拦截
    return STAGE_KEYS[queryIndex]
  }
  if (queryIndex >= 0 && entered.includes(queryStage)) return queryStage
  return fallback
}

export function buildStageTransitionPayload({
  sourceStageKey,
  targetStageKey,
  action,
  projectRevision,
  sourceCheckpointRevision,
  targetCheckpointRevision,
  contentUnitId,
  contentUnitRevision,
  warningAcknowledgements = []
}) {
  return {
    source_stage_key: sourceStageKey,
    target_stage_key: targetStageKey ?? null,
    action: action || undefined,
    project_revision: projectRevision,
    source_checkpoint_revision: sourceCheckpointRevision,
    target_checkpoint_revision: targetCheckpointRevision,
    content_unit_id: contentUnitId,
    content_unit_revision: contentUnitRevision,
    warning_acknowledgements: warningAcknowledgements
  }
}

export function checkpointRevisionOf(state, stageKey) {
  const stage = state.stages?.find(item => item.key === stageKey)
  if (stage?.checkpointRevision != null) return stage.checkpointRevision
  const row = state.stageTruth?.stagesByKey?.[stageKey]
  return stageField(row, 'checkpointRevision', 'checkpoint_revision') ?? 0
}
