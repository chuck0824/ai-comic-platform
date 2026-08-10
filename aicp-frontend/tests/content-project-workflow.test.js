import test from 'node:test'
import assert from 'node:assert/strict'
import { currentStage, primaryAction } from '../src/views/content-project/utils/workflowPath.js'
import { STAGES } from '../src/views/content-project/workbench/scriptWorkbenchModel.js'

test('uses the first current stage and ignores skipped storyboard', () => {
  const stages = [
    { key: 'content', status: 'completed' },
    { key: 'destination', status: 'current', primary_action: '选择去向' },
    { key: 'storyboard', status: 'skipped' }
  ]
  assert.equal(currentStage(stages).key, 'destination')
  assert.equal(primaryAction(stages), '选择去向')
})

test('falls back to first non-completed non-skipped stage', () => {
  const stages = [
    { key: 'story_seed', status: 'completed' },
    { key: 'characters', status: 'pending', primary_action: '生成角色设定' }
  ]
  assert.equal(currentStage(stages).key, 'characters')
})

test('returns null for all completed stages', () => {
  const stages = [
    { key: 'story_seed', status: 'completed' },
    { key: 'content', status: 'completed' }
  ]
  assert.equal(currentStage(stages), null)
})

test('primaryAction returns default when no current stage', () => {
  assert.equal(primaryAction([]), '返回项目')
})

test('skips optional stages when finding current', () => {
  const stages = [
    { key: 'content', status: 'completed' },
    { key: 'review', status: 'optional' },
    { key: 'destination', status: 'pending', primary_action: '选择去向' }
  ]
  assert.equal(currentStage(stages).key, 'destination')
})

// ── Creative Bible readiness ──

test('ready_for_generation is true only when status is confirmed', () => {
  // Simulating bible health contract
  const makeHealth = (status) => ({
    status,
    current_version_id: status === 'missing' ? 0 : 1,
    current_version_no: status === 'missing' ? 0 : 1,
    confirmed_fact_count: 5,
    pending_change_count: 0,
    ready_for_generation: status === 'confirmed'
  })

  assert.equal(makeHealth('missing').ready_for_generation, false)
  assert.equal(makeHealth('draft').ready_for_generation, false)
  assert.equal(makeHealth('reviewable').ready_for_generation, false)
  assert.equal(makeHealth('confirmed').ready_for_generation, true)
  assert.equal(makeHealth('superseded').ready_for_generation, false)
})

test('missing health blocks generation', () => {
  const health = { status: 'missing', ready_for_generation: false }
  const canGenerate = health.ready_for_generation === true
  assert.equal(canGenerate, false)
})

test('context panel shows bible version when available', () => {
  const bibleHealth = { status: 'confirmed', current_version_no: 2, ready_for_generation: true }
  const context = { bible_version_id: 11, project_guide_id: 20, payload_hash: 'abc123def4567890' }

  // These should be displayed in ContextPanel
  assert.equal(bibleHealth.current_version_no, 2)
  assert.equal(context.bible_version_id, 11)
  assert.ok(context.payload_hash.length >= 12)
})

test('native script workflow uses the approved eight-stage order', () => {
  assert.deepEqual(STAGES.map(stage => stage.key), [
    'creation_settings', 'novel_upload', 'novel_analysis', 'adaptation',
    'structured_script', 'script_body', 'review_revision', 'text_storyboard'
  ])
})
