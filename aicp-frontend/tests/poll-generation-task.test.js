import test from 'node:test'
import assert from 'node:assert/strict'

import { pollGenerationTask, pollTimeoutForType } from '../src/views/canvas/utils/pollGenerationTask.js'

test('returns when the generation task succeeds', async () => {
  const responses = [
    { data: { uuid: 't1', status: 'running' } },
    { data: { uuid: 't1', status: 'succeeded', outputAssets: '{"url":"https://cdn.example/a.png"}' } },
  ]
  const task = await pollGenerationTask({
    taskId: 't1',
    getTask: async () => responses.shift(),
    wait: async () => {},
  })
  assert.equal(task.status, 'succeeded')
})

test('surfaces failed tasks and times out while still running', async () => {
  await assert.rejects(() => pollGenerationTask({
    taskId: 't2',
    getTask: async () => ({ data: { status: 'failed', error_message: '模型超时' } }),
    wait: async () => {},
  }), /模型超时/)

  let clock = 0
  await assert.rejects(() => pollGenerationTask({
    taskId: 't3',
    getTask: async () => ({ status: 'running' }),
    timeoutMs: 10,
    now: () => (clock += 11),
    wait: async () => {},
  }), /超时/)
})

test('uses a longer timeout for video generation', () => {
  assert.equal(pollTimeoutForType('video'), 480000)
  assert.equal(pollTimeoutForType('image'), 180000)
})
