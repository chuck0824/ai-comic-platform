/**
 * R2-A：草稿串行保存队列 — 忽略过期响应，暴露离页拦截状态。
 */

export function createDraftSaveQueue() {
  let generation = 0
  let chain = Promise.resolve()
  let dirty = false
  let failed = false
  let status = 'idle'

  return {
    markDirty() {
      dirty = true
      if (status !== 'saving') status = 'idle'
    },
    markClean() {
      dirty = false
      failed = false
      status = 'idle'
    },
    clearFailure() {
      failed = false
      if (status === 'failed') status = dirty ? 'idle' : 'idle'
    },
    getStatus() {
      return status
    },
    isDirty() {
      return dirty
    },
    hasFailed() {
      return failed
    },
    shouldBlockLeave() {
      return dirty || failed || status === 'saving'
    },
    /**
     * @param {() => Promise<{persisted?: boolean}>} saveFn
     * @returns {Promise<{persisted?: boolean, skipped?: boolean, stale?: boolean}>}
     */
    enqueue(saveFn) {
      dirty = true
      const token = ++generation
      status = 'saving'
      const run = chain.then(async () => {
        if (token !== generation) {
          return { persisted: true, skipped: true, stale: true }
        }
        try {
          const result = await saveFn()
          if (token !== generation) {
            return { ...(result || {}), skipped: true, stale: true }
          }
          if (result?.persisted === false) {
            failed = true
            status = 'failed'
            return result
          }
          dirty = false
          failed = false
          status = 'saved'
          return result || { persisted: true }
        } catch (error) {
          if (token !== generation) {
            return { persisted: false, skipped: true, stale: true }
          }
          failed = true
          status = 'failed'
          throw error
        }
      })
      chain = run.catch(() => {})
      return run
    }
  }
}
