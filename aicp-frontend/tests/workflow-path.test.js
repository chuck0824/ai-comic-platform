import test from 'node:test'
import assert from 'node:assert/strict'
import {
  listWorkflowLabel,
  listWorkflowProgress,
  normalizeStageKey,
  stageLabel
} from '../src/views/content-project/utils/workflowPath.js'

test('normalizes legacy stage keys to eight-stage keys', () => {
  assert.equal(normalizeStageKey('story_seed'), 'creation_settings')
  assert.equal(normalizeStageKey('content'), 'script_body')
  assert.equal(normalizeStageKey('script_body'), 'script_body')
})

test('stageLabel prefers eight-stage labels', () => {
  assert.equal(stageLabel('story_seed'), '创作设置')
  assert.equal(stageLabel('adaptation'), '改编方案')
})

test('listWorkflowLabel prefers server summary fields', () => {
  assert.equal(listWorkflowLabel({
    last_stage_key: 'story_seed',
    workflow_current_stage_label: '剧本正文'
  }), '剧本正文')
  assert.equal(listWorkflowLabel({
    last_stage_key: 'content',
    workflow_current_stage_key: 'script_body'
  }), '剧本正文')
  assert.equal(listWorkflowLabel({ last_stage_key: 'outline' }), '结构化剧本')
})

test('listWorkflowProgress reads numeric progress', () => {
  assert.equal(listWorkflowProgress({ workflow_progress: 50 }), 50)
  assert.equal(listWorkflowProgress({}), null)
})
