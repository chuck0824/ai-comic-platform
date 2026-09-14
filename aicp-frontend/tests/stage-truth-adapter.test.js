import test from 'node:test'
import assert from 'node:assert/strict'
import { createWorkbenchState } from '../src/views/content-project/workbench/scriptWorkbenchModel.js'
import {
  applyStageTruthProjection,
  buildStageTransitionPayload,
  mapCheckpointStateToRailStatus,
  projectionToStageStatuses,
  resolveWorkspaceStageFromTruth
} from '../src/views/content-project/workbench/stageTruthAdapter.js'

const sampleProjection = {
  project_id: 1,
  project_revision: 3,
  last_stage_key: 'adaptation',
  stage_truth_enabled: true,
  stages: [
    { stage_key: 'creation_settings', state: 'COMPLETED', checkpoint_revision: 1 },
    { stage_key: 'novel_upload', state: 'COMPLETED', checkpoint_revision: 1 },
    { stage_key: 'novel_analysis', state: 'POSSIBLY_STALE', checkpoint_revision: 2 },
    { stage_key: 'adaptation', state: 'IN_PROGRESS', checkpoint_revision: 0 },
    { stage_key: 'structured_script', state: 'NOT_STARTED', checkpoint_revision: 0 },
    { stage_key: 'script_body', state: 'NOT_STARTED', checkpoint_revision: 0 },
    { stage_key: 'review_revision', state: 'NOT_STARTED', checkpoint_revision: 0 },
    { stage_key: 'text_storyboard', state: 'NOT_STARTED', checkpoint_revision: 0 }
  ]
}

test('maps checkpoint states to rail statuses', () => {
  assert.equal(mapCheckpointStateToRailStatus('COMPLETED', false), 'completed')
  assert.equal(mapCheckpointStateToRailStatus('LOCKED', false), 'locked')
  assert.equal(mapCheckpointStateToRailStatus('POSSIBLY_STALE', false), 'possibly_stale')
  assert.equal(mapCheckpointStateToRailStatus('REGEN_REQUIRED', false), 'regen_required')
  assert.equal(mapCheckpointStateToRailStatus('IN_PROGRESS', true), 'current')
})

test('projects completed and entered stages from server truth', () => {
  const mapped = projectionToStageStatuses(sampleProjection)
  assert.deepEqual(mapped.completedStages, ['creation_settings', 'novel_upload'])
  assert.ok(mapped.enteredStages.includes('novel_analysis'))
  assert.ok(mapped.enteredStages.includes('adaptation'))
  assert.equal(mapped.enteredStages.includes('script_body'), false)
})

test('applies projection onto workbench state', () => {
  const state = createWorkbenchState()
  applyStageTruthProjection(state, sampleProjection, 'adaptation')
  assert.equal(state.activeStage, 'adaptation')
  assert.equal(state.stageTruth.enabled, true)
  assert.equal(state.stages.find(s => s.key === 'novel_analysis').status, 'possibly_stale')
  assert.equal(state.stages.find(s => s.key === 'adaptation').status, 'current')
})

test('blocks URL jump into not-entered stages', () => {
  const mapped = projectionToStageStatuses(sampleProjection)
  assert.equal(resolveWorkspaceStageFromTruth({
    enteredStages: mapped.enteredStages,
    lastStageKey: 'adaptation',
    queryStage: 'script_body'
  }), 'adaptation')
  assert.equal(resolveWorkspaceStageFromTruth({
    enteredStages: mapped.enteredStages,
    lastStageKey: 'adaptation',
    queryStage: 'novel_upload'
  }), 'novel_upload')
})

test('builds snake_case transition payload', () => {
  const payload = buildStageTransitionPayload({
    sourceStageKey: 'adaptation',
    targetStageKey: 'structured_script',
    projectRevision: 3,
    sourceCheckpointRevision: 0,
    targetCheckpointRevision: 0,
    warningAcknowledgements: ['DURATION_VARIANCE']
  })
  assert.equal(payload.source_stage_key, 'adaptation')
  assert.equal(payload.target_stage_key, 'structured_script')
  assert.equal(payload.project_revision, 3)
  assert.deepEqual(payload.warning_acknowledgements, ['DURATION_VARIANCE'])
})
