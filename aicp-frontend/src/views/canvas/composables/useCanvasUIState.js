/**
 * Canvas ↔ Director 导航状态保持。
 * 进入导演台前保存 UI 状态；返回后恢复。
 */

const STORAGE_KEY = 'canvas_ui_state'

export function useCanvasUIState() {
  return {
    write(state) {
      try {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
          activeShotUnitId: state.activeShotUnitId || null,
          activeNodeId: state.activeNodeId || null,
          activeTab: state.activeTab || 'content',
          scrollPosition: state.scrollPosition || 0
        }))
      } catch { /* noop */ }
    },

    read() {
      try {
        return JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '{}')
      } catch {
        return {}
      }
    },

    clear() {
      try { sessionStorage.removeItem(STORAGE_KEY) } catch { /* noop */ }
    }
  }
}
