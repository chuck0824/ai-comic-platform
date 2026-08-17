const SUCCESS = new Set(['succeeded', 'completed'])
const FAILURE = new Set(['failed', 'canceled', 'cancelled'])

function unwrapTask(payload) {
  return payload?.data ?? payload ?? {}
}

export async function pollGenerationTask({
  taskId,
  getTask,
  intervalMs = 1500,
  timeoutMs = 180000,
  wait = (ms) => new Promise(resolve => setTimeout(resolve, ms)),
  now = () => Date.now(),
} = {}) {
  if (!taskId) throw new Error('缺少任务编号')
  const started = now()
  while (true) {
    const task = unwrapTask(await getTask(taskId))
    const status = task.status
    if (SUCCESS.has(status)) return task
    if (FAILURE.has(status)) {
      throw new Error(task.errorMessage || task.error_message || '生成失败')
    }
    if (now() - started >= timeoutMs) {
      throw new Error('生成超时，请稍后刷新画布查看结果')
    }
    await wait(intervalMs)
  }
}

export function pollTimeoutForType(type) {
  return { video: 480000, image: 180000, audio: 120000 }[type] || 180000
}
