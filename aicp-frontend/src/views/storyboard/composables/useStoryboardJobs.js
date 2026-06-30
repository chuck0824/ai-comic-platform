import { ref, onBeforeUnmount } from 'vue'
import { storyboardV2Api } from '@/api/storyboardV2'

export function useStoryboardJobs() {
  const activeJob = ref(null)
  const jobProgress = ref(0)
  const jobStage = ref('')
  let eventSource = null
  let pollTimer = null

  function watchJob(jobId) {
    closeEventSource()

    // Try SSE first
    try {
      const baseUrl = '/api/v1'
      const token = localStorage.getItem('access_token')
      eventSource = new EventSource(`${baseUrl}/storyboard-jobs/${jobId}/events?token=${token}`)
      eventSource.onmessage = (e) => {
        try {
          const data = JSON.parse(e.data)
          activeJob.value = data
          jobProgress.value = data.progressPercent || data.percent || 0
          jobStage.value = data.currentStage || data.stage || ''
          if (['succeeded', 'failed', 'partial', 'cancelled'].includes(data.status)) {
            closeEventSource()
          }
        } catch (_) { /* ignore parse errors */ }
      }
      eventSource.onerror = () => {
        closeEventSource()
        startPolling(jobId)
      }
    } catch (_) {
      startPolling(jobId)
    }
  }

  function startPolling(jobId) {
    pollTimer = setInterval(async () => {
      try {
        const res = await storyboardV2Api.getJob(jobId)
        const data = res.data
        activeJob.value = data
        jobProgress.value = data.progressPercent || 0
        jobStage.value = data.currentStage || ''
        if (['succeeded', 'failed', 'partial', 'cancelled'].includes(data.status)) {
          clearInterval(pollTimer)
          pollTimer = null
        }
      } catch (_) {
        clearInterval(pollTimer)
        pollTimer = null
      }
    }, 2000)
  }

  function closeEventSource() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  function cancelPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  onBeforeUnmount(() => {
    closeEventSource()
    cancelPolling()
  })

  return {
    activeJob,
    jobProgress,
    jobStage,
    watchJob,
    cancelPolling
  }
}
