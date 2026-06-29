import test from 'node:test'
import assert from 'node:assert/strict'
import { currentStage, primaryAction } from '../src/views/content-project/utils/workflowPath.js'

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
