import { ref, onBeforeUnmount } from 'vue'
import { contentProjectApi } from '@/api/contentProject'

/**
 * M1: Composable for triggering and polling V7 generation jobs.
 */
export function useGeneration(projectId) {
  const generating = ref(false)
  const genError = ref('')
  const currentJob = ref(null)
  let pollTimer = null

  onBeforeUnmount(() => {
    if (pollTimer) clearInterval(pollTimer)
  })

  async function triggerGeneration(jobType, targetType, targetId, selectedVersions = {}, strategy = '') {
    generating.value = true
    genError.value = ''
    try {
      const res = await fetch('/api/v1/generation-jobs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': `${projectId.value}-${jobType}-${Date.now()}`
        },
        body: JSON.stringify({
          job_type: jobType,
          target_type: targetType,
          target_id: targetId,
          selected_versions: selectedVersions,
          strategy,
          schema_version: 'v1'
        })
      })
      if (!res.ok) {
        const err = await res.json()
        throw new Error(err.message || '生成任务创建失败')
      }
      const data = await res.json()
      currentJob.value = data.data
      startPolling(data.data.id)
      return data.data
    } catch (e) {
      genError.value = e.message
      generating.value = false
      return null
    }
  }

  function startPolling(jobId) {
    if (pollTimer) clearInterval(pollTimer)
    pollTimer = setInterval(async () => {
      try {
        const res = await fetch(`/api/v1/generation-jobs/${jobId}`)
        const data = await res.json()
        const job = data.data
        currentJob.value = job
        if (['completed', 'failed', 'cancelled', 'partial_completed'].includes(job.status)) {
          clearInterval(pollTimer)
          pollTimer = null
          generating.value = false
          if (job.status === 'failed') {
            genError.value = '生成失败：' + (job.error_code || '未知错误')
          }
        }
      } catch (e) {
        // silent poll error
      }
    }, 2000)
  }

  function cancelGeneration() {
    if (pollTimer) clearInterval(pollTimer)
    pollTimer = null
    generating.value = false
    if (currentJob.value?.id) {
      fetch(`/api/v1/generation-jobs/${currentJob.value.id}/cancel`, { method: 'POST' }).catch(() => {})
    }
  }

  return {
    generating,
    genError,
    currentJob,
    triggerGeneration,
    cancelGeneration
  }
}
