function body(response) { return response?.data?.data ?? response?.data ?? response ?? {} }

function terminalError(code, message, status = 'failed', targetAction = 'retry_generation') {
  return Object.assign(new Error(message), { code, status, targetAction })
}

function normalizedStatus(job = {}) { return String(job.status || '').toLowerCase() }
function progressFor(job, status) {
  if (status === 'completed') return 100
  const explicit = Number(job.progress ?? job.percentage)
  if (Number.isFinite(explicit)) return Math.max(0, Math.min(status === 'running' || status === 'processing' ? 95 : 20, explicit))
  return ['running', 'processing'].includes(status) ? 50 : 10
}
function subtaskFor(status) {
  if (status === 'completed') return '生成已完成，正在整理产物'
  if (['running', 'processing'].includes(status)) return '模型正在生成内容'
  return '生成任务已排队'
}
const defaultWait = (delay, signal) => new Promise((resolve, reject) => {
  const timer = setTimeout(resolve, delay)
  signal?.addEventListener('abort', () => {
    clearTimeout(timer)
    reject(terminalError('GENERATION_CANCELLED', '生成任务已取消。', 'cancelled'))
  }, { once: true })
})

/** Polls the authoritative generation job until one truthful terminal state is observed. */
export async function trackGenerationJob({
  job, getJob, onProgress = () => {}, signal,
  timeoutMs = 120000, pollIntervalMs = 2000, wait = defaultWait, now = Date.now
}) {
  if (!job?.id || typeof getJob !== 'function') throw terminalError('GENERATION_JOB_MISSING', '缺少可跟踪的生成任务。')
  const startedAt = now()
  let current = body(job)
  while (true) {
    if (signal?.aborted) throw terminalError('GENERATION_CANCELLED', '生成任务已取消。', 'cancelled')
    const status = normalizedStatus(current)
    onProgress({ percentage: progressFor(current, status), subtask: subtaskFor(status), status, job: current })
    if (status === 'completed') return current
    if (status === 'cancelled') throw terminalError('GENERATION_CANCELLED', '生成任务已取消。', 'cancelled')
    if (status === 'failed') {
      throw terminalError(current.error_code || current.errorCode || 'GENERATION_FAILED', current.error_message || current.errorMessage || '生成任务失败。')
    }
    if (status === 'partial_completed') throw terminalError('GENERATION_PARTIAL_FAILED', '生成任务仅部分完成，请查看失败详情后重试。')
    if (now() - startedAt > timeoutMs) {
      throw terminalError('GENERATION_POLL_TIMEOUT', '生成仍在进行，跟踪已超时。请稍后重新查询任务状态。', 'failed', 'retry_generation_status')
    }
    const delay = Number(current.poll_after_ms ?? current.pollAfterMs ?? pollIntervalMs)
    await wait(Number.isFinite(delay) && delay >= 0 ? delay : pollIntervalMs, signal)
    current = body(await getJob(job.id))
  }
}
