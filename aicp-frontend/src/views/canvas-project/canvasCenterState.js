/**
 * Canvas 项目中心有限状态机 —— 纯函数 reducer。
 * 消除无限骨架屏：超时、账户中心不可用、空数据和错误各有唯一终态。
 */
export const CENTER_STATE_KINDS = Object.freeze({
  LOADING: 'loading',
  READY: 'ready',
  EMPTY: 'empty',
  ERROR: 'error',
  DEGRADED: 'degraded'
})

const REQUEST_TIMEOUT_MS = 8000

/**
 * @param {{ loading: boolean, items?: any[], code?: number|null, cachedItems?: any[] }} input
 * @returns {{ kind: string, readOnly: boolean, items: any[] }}
 */
export function resolveCanvasCenterState({ loading, items = [], code, cachedItems = [] }) {
  if (loading) return { kind: CENTER_STATE_KINDS.LOADING, readOnly: false, items: [] }
  if (code === 41012) return { kind: CENTER_STATE_KINDS.DEGRADED, readOnly: true, items: cachedItems }
  if (code) return { kind: CENTER_STATE_KINDS.ERROR, readOnly: true, items: [] }
  if (!items.length) return { kind: CENTER_STATE_KINDS.EMPTY, readOnly: false, items: [] }
  return { kind: CENTER_STATE_KINDS.READY, readOnly: false, items }
}

/**
 * 包装异步请求，超时后返回 degraded 状态而不是永久 loading。
 * @param {() => Promise<any>} fetcher
 * @returns {Promise<{ kind: string, readOnly: boolean, items: any[] }>}
 */
export async function fetchWithTimeoutGuard(fetcher) {
  let timer
  const timeout = new Promise(resolve => {
    timer = setTimeout(() => resolve({
      ok: false, code: 41012, items: [], message: '请求超时'
    }), REQUEST_TIMEOUT_MS)
  })

  try {
    const result = await Promise.race([fetcher(), timeout])
    clearTimeout(timer)
    return result
  } catch (e) {
    clearTimeout(timer)
    throw e
  }
}

/**
 * 将最近成功的项目摘要缓存到 localStorage。
 * 不缓存鉴权失败或上游不可用结果。
 */
const RECENT_CACHE_KEY = 'canvas_recent_projects'

export function cacheRecentProjects(items) {
  try {
    if (Array.isArray(items) && items.length) {
      const summaries = items.slice(0, 20).map(({ uuid, name, updated_at, status, mode }) => ({
        uuid, name, updated_at, status, mode
      }))
      localStorage.setItem(RECENT_CACHE_KEY, JSON.stringify(summaries))
    }
  } catch { /* quota exceeded, silently skip */ }
}

export function getCachedProjects() {
  try {
    return JSON.parse(localStorage.getItem(RECENT_CACHE_KEY) || '[]')
  } catch {
    return []
  }
}
