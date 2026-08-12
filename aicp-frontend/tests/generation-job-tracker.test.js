import test from 'node:test'
import assert from 'node:assert/strict'
import { trackGenerationJob } from '../src/views/content-project/workbench/generationJobTracker.js'
import {
  createGenerationDecisionGuard,
  loadAcceptedGeneration,
  loadUnitWorkspaceContent,
  persistGenerationDecision,
  runGuardedGenerationDecision
} from '../src/views/content-project/workbench/generationResultPersistence.js'

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

test('terminal job keeps authoritative actual credits and actionable failure details', async () => {
  const completed = await trackGenerationJob({
    job: { id: 76, status: 'completed', actual_credits: 7, result_version_id: 176, artifact_ref: '/content-units/17/versions/176' },
    getJob: async () => { throw new Error('should not poll') }
  })
  assert.equal(completed.actual_credits, 7)
  assert.equal(completed.result_version_id, 176)

  await assert.rejects(() => trackGenerationJob({
    job: { id: 77, status: 'failed', error_code: 'SCHEMA_VALIDATION_FAILED', error_message: '生成结果结构校验失败' },
    getJob: async () => { throw new Error('should not poll') }
  }), error => error.code === 'SCHEMA_VALIDATION_FAILED' && /结构校验/.test(error.message))
})

test('accept and discard persist on the server before changing local audit state', async () => {
  const calls = []
  const workbench = {
    acceptGeneration: id => { calls.push(`local-accept:${id}`); return { taskId: id } },
    discardGeneration: id => { calls.push(`local-discard:${id}`); return { taskId: id } }
  }
  const api = {
    acceptGenerationJob: async id => { calls.push(`server-accept:${id}`); return { data: { data: { result_version_id: 176 } } } },
    discardGenerationJob: async id => { calls.push(`server-discard:${id}`); return { data: { data: { result_version_id: 177 } } } }
  }
  const refreshed = []

  await persistGenerationDecision({ decision: 'accept', serverJobId: 76, localTaskId: 'task-76', api, workbench, refresh: async response => refreshed.push(response.result_version_id) })
  await persistGenerationDecision({ decision: 'discard', serverJobId: 77, localTaskId: 'task-77', api, workbench, refresh: async () => refreshed.push('discard-refresh') })

  assert.deepEqual(calls, ['server-accept:76', 'local-accept:task-76', 'server-discard:77', 'local-discard:task-77'])
  assert.deepEqual(refreshed, [176])
})

test('failed server decision leaves the local generated result untouched', async () => {
  let localCalls = 0
  const result = await persistGenerationDecision({
    decision: 'accept', serverJobId: 78, localTaskId: 'task-78',
    api: { acceptGenerationJob: async () => { throw Object.assign(new Error('候选版本已丢弃'), { code: 'GENERATION_RESULT_DISCARDED' }) } },
    workbench: { acceptGeneration: () => { localCalls += 1 } }
  })
  assert.equal(result.ok, false)
  assert.equal(result.code, 'GENERATION_RESULT_DISCARDED')
  assert.equal(localCalls, 0)
})

test('accepted result refreshes from the unit current version instead of the manual draft', async () => {
  const loaded = await loadAcceptedGeneration({
    response: { target_id: 17, result_version_id: 176 },
    listUnits: async () => [{ id: 17, unit_type: 'script_body', current_version_id: 176, revision: 4 }],
    listVersions: async () => [
      { id: 175, status: 'draft', content_json: '{"scenes":["旧草稿"]}' },
      { id: 176, status: 'accepted', content_json: '{"scenes":["新候选"]}' }
    ]
  })

  assert.equal(loaded.unit.current_version_id, 176)
  assert.deepEqual(loaded.content, { scenes: ['新候选'] })
})

test('workspace reload prefers the authoritative current named version and only falls back without one', async () => {
  let draftCalls = 0
  const current = await loadUnitWorkspaceContent({
    unit: { id: 17, current_version_id: 176 },
    listVersions: async () => [{ id: 176, status: 'accepted', content_json: '{"scenes":["已采用"]}' }],
    getDraft: async () => { draftCalls += 1; return { content_json: '{"scenes":["旧草稿"]}' } }
  })
  const fallback = await loadUnitWorkspaceContent({
    unit: { id: 18, current_version_id: null },
    listVersions: async () => { throw new Error('should not list') },
    getDraft: async () => { draftCalls += 1; return { revision: 3, content_json: '{"scenes":["手工草稿"]}' } }
  })
  assert.deepEqual(current.content, { scenes: ['已采用'] })
  assert.deepEqual(fallback.content, { scenes: ['手工草稿'] })
  assert.equal(draftCalls, 1)
})

test('decision guard deduplicates double clicks and same disposition is locally idempotent', async () => {
  const guard = createGenerationDecisionGuard()
  let remoteCalls = 0
  let release
  const remote = new Promise(resolve => { release = resolve })
  const first = guard.run('task-80', 'accept', async () => { remoteCalls += 1; await remote; return { ok: true } })
  const second = guard.run('task-80', 'accept', async () => { remoteCalls += 1; return { ok: false } })
  release()
  assert.strictEqual(first, second)
  assert.equal((await second).ok, true)
  assert.equal(remoteCalls, 1)

  const idempotent = await persistGenerationDecision({
    decision: 'accept', serverJobId: 80, localTaskId: 'task-80',
    api: { acceptGenerationJob: async () => ({ data: { data: { result_disposition: 'accepted' } } }) },
    workbench: { acceptGeneration: () => ({ allowed: false, code: 'GENERATION_ALREADY_RECORDED' }) }
  })
  assert.equal(idempotent.ok, true)
})

test('decision guard rejects accept-discard races in both directions without invoking the second action', async () => {
  for (const [firstDecision, secondDecision] of [['accept', 'discard'], ['discard', 'accept']]) {
    const guard = createGenerationDecisionGuard()
    const calls = []
    let release
    const blocked = new Promise(resolve => { release = resolve })
    const first = guard.run('task-race', firstDecision, async decision => {
      calls.push(decision)
      await blocked
      return { ok: true }
    })
    const conflict = await guard.run('task-race', secondDecision, async decision => {
      calls.push(decision)
      return { ok: true }
    })
    assert.deepEqual(conflict, {
      ok: false,
      code: 'IN_FLIGHT_CONFLICT',
      message: '该生成结果正在执行相反操作，请等待当前操作完成'
    })
    assert.deepEqual(calls, [firstDecision])
    release()
    await first
  }
})

test('guarded workspace decision publishes conflict guidance even when the drawer ignores its promise', async () => {
  const guard = createGenerationDecisionGuard()
  const serverCalls = []
  const guidance = []
  let releaseAccept
  const acceptBlocked = new Promise(resolve => { releaseAccept = resolve })
  const accept = runGuardedGenerationDecision({
    guard, taskId: 'task-visible-conflict', decision: 'accept',
    execute: async decision => { serverCalls.push(decision); await acceptBlocked; return { ok: true } },
    onFailure: result => guidance.push(result)
  })

  void runGuardedGenerationDecision({
    guard, taskId: 'task-visible-conflict', decision: 'discard',
    execute: async decision => { serverCalls.push(decision); return { ok: true } },
    onFailure: result => guidance.push(result)
  })
  await new Promise(resolve => setImmediate(resolve))

  assert.deepEqual(serverCalls, ['accept'])
  assert.equal(guidance.length, 1)
  assert.equal(guidance[0].code, 'IN_FLIGHT_CONFLICT')
  releaseAccept()
  await accept
})
