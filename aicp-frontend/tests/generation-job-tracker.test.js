import test from 'node:test'
import assert from 'node:assert/strict'
import { trackGenerationJob } from '../src/views/content-project/workbench/generationJobTracker.js'

test('tracks queued through running to completed and preserves the real artifact response', async () => {
  const responses = [
    { id: 71, status: 'running', progress: 55, poll_after_ms: 1 },
    { id: 71, status: 'completed', progress: 100, result_path: 'vault/06-剧本正文/第1集.md', actual_credits: 18 }
  ]
  const progress = []
  const result = await trackGenerationJob({
    job: { id: 71, status: 'queued', poll_after_ms: 1 },
    getJob: async () => responses.shift(),
    onProgress: update => progress.push(update),
    wait: async () => {}
  })
  assert.equal(result.status, 'completed')
  assert.equal(result.result_path, 'vault/06-剧本正文/第1集.md')
  assert.deepEqual(progress.map(item => item.percentage), [10, 55, 100])
  assert.match(progress[1].subtask, /生成/)
})

test('failed and cancelled generation jobs surface terminal details instead of success', async () => {
  await assert.rejects(() => trackGenerationJob({
    job: { id: 72, status: 'queued' },
    getJob: async () => ({ id: 72, status: 'failed', error_code: 'MODEL_TIMEOUT', error_message: '模型超时' }),
    wait: async () => {}
  }), error => error.code === 'MODEL_TIMEOUT' && /模型超时/.test(error.message))

  await assert.rejects(() => trackGenerationJob({
    job: { id: 73, status: 'queued' },
    getJob: async () => ({ id: 73, status: 'cancelled' }),
    wait: async () => {}
  }), error => error.code === 'GENERATION_CANCELLED' && /取消/.test(error.message))
})

test('generation polling supports local cancellation and actionable timeout', async () => {
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(() => trackGenerationJob({
    job: { id: 74, status: 'queued' }, getJob: async () => ({ id: 74, status: 'queued' }),
    signal: controller.signal, wait: async () => {}
  }), error => error.code === 'GENERATION_CANCELLED')

  let clock = 0
  await assert.rejects(() => trackGenerationJob({
    job: { id: 75, status: 'queued' }, getJob: async () => ({ id: 75, status: 'running' }),
    timeoutMs: 10, now: () => (clock += 11), wait: async () => {}
  }), error => error.code === 'GENERATION_POLL_TIMEOUT' && error.targetAction === 'retry_generation_status')
})
