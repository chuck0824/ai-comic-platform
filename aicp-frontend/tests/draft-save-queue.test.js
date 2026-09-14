import test from 'node:test'
import assert from 'node:assert/strict'
import { createDraftSaveQueue } from '../src/views/content-project/workbench/draftSaveQueue.js'

test('serial queue drops stale responses when a newer save is enqueued', async () => {
  const queue = createDraftSaveQueue()
  let resolveFirst
  const firstGate = new Promise(resolve => { resolveFirst = resolve })

  const first = queue.enqueue(async () => {
    await firstGate
    return { persisted: true, id: 1 }
  })
  const second = queue.enqueue(async () => ({ persisted: true, id: 2 }))

  resolveFirst()
  const [a, b] = await Promise.all([first, second])
  assert.equal(a.stale, true)
  assert.equal(a.skipped, true)
  assert.equal(b.id, 2)
  assert.equal(b.persisted, true)
  assert.equal(queue.isDirty(), false)
  assert.equal(queue.getStatus(), 'saved')
})

test('failed save blocks leave until cleared', async () => {
  const queue = createDraftSaveQueue()
  queue.markDirty()
  assert.equal(queue.shouldBlockLeave(), true)

  const result = await queue.enqueue(async () => ({ persisted: false, message: 'conflict' }))
  assert.equal(result.persisted, false)
  assert.equal(queue.hasFailed(), true)
  assert.equal(queue.shouldBlockLeave(), true)

  queue.clearFailure()
  queue.markDirty()
  assert.equal(queue.hasFailed(), false)

  await queue.enqueue(async () => ({ persisted: true }))
  assert.equal(queue.shouldBlockLeave(), false)
  assert.equal(queue.getStatus(), 'saved')
})

test('thrown error marks failed and rethrows', async () => {
  const queue = createDraftSaveQueue()
  await assert.rejects(
    () => queue.enqueue(async () => { throw new Error('boom') }),
    /boom/
  )
  assert.equal(queue.hasFailed(), true)
  assert.equal(queue.shouldBlockLeave(), true)
})
