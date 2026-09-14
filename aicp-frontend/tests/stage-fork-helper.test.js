import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildForkStageRequest,
  shouldForkBeforeEdit
} from '../src/views/content-project/workbench/stageForkHelper.js'

test('shouldForkBeforeEdit only for COMPLETED or LOCKED', () => {
  assert.equal(shouldForkBeforeEdit('COMPLETED'), true)
  assert.equal(shouldForkBeforeEdit('LOCKED'), true)
  assert.equal(shouldForkBeforeEdit('IN_PROGRESS'), false)
  assert.equal(shouldForkBeforeEdit('NOT_STARTED'), false)
  assert.equal(shouldForkBeforeEdit('POSSIBLY_STALE'), false)
  assert.equal(shouldForkBeforeEdit('REGEN_REQUIRED'), false)
  assert.equal(shouldForkBeforeEdit(undefined), false)
})

test('buildForkStageRequest uses snake_case fields', () => {
  assert.deepEqual(buildForkStageRequest({
    checkpointRevision: 3,
    adoptedContentVersionId: 99,
    projectRevision: 7
  }), {
    checkpoint_revision: 3,
    base_adopted_content_version_id: 99,
    project_revision: 7
  })
})
