import test from 'node:test'
import assert from 'node:assert/strict'
import { CREATION_CARDS, continuationAction, sortContinueWorking, buildHomeViewModel } from '../src/views/dashboard/homeViewModel.js'

test('creation cards preserve three explicit modes', () => {
  assert.deepEqual(CREATION_CARDS.map(x => x.mode), ['short_drama', 'long_form', 'tvc'])
})

test('continuation action follows current stage', () => {
  assert.equal(continuationAction({ stage: 'content', id: 7 }).path, '/script-gen/7/workspace')
  assert.equal(continuationAction({ stage: 'canvas', canvasProjectUuid: 'canvas_x' }).path, '/canvas/canvas_x')
  assert.equal(continuationAction({ stage: 'editing', canvasProjectUuid: 'c1' }).label, '进入画布')
  assert.equal(continuationAction({ stage: 'storyboard', id: 7 }).label, '进入分镜')
})

test('continuation items sort errors first', () => {
  const items = [
    { stage: 'canvas', updatedAt: '2026-06-30T10:00:00Z', hasErrors: false },
    { stage: 'content', updatedAt: '2026-06-29T10:00:00Z', hasErrors: true }
  ]
  const sorted = sortContinueWorking(items)
  assert.equal(sorted[0].hasErrors, true)
})

test('empty continue-working list marks empty state', () => {
  const result = buildHomeViewModel({ continueWorking: [], canvasSummary: { active: 0, generating: 0, errors: 0 }, metrics: {} })
  assert.equal(result.continueWorkingEmpty, true)
})

test('continue-working defaults to 5 items max', () => {
  const items = Array.from({ length: 10 }, (_, i) => ({ stage: 'canvas', updatedAt: `2026-06-${30 - i}T10:00:00Z`, hasErrors: false }))
  const result = buildHomeViewModel({ continueWorking: items, canvasSummary: {}, metrics: {} })
  assert.equal(result.continueWorking.length, 5)
})
