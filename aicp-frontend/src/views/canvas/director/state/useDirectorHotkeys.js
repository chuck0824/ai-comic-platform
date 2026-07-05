/**
 * 导演台键盘快捷键。
 * 注册全局快捷键并在组件卸载时清理。
 */

export const HOTKEY_MAP = {
  // Transform tools
  'KeyW': 'translate', 'KeyG': 'translate',
  'KeyE': 'rotate', 'KeyR': 'rotate',
  // Scale (S conflicts, use shift)
  'KeyS': 'scale',

  // Axis locks
  'KeyX': 'lockX', 'KeyY': 'lockY', 'KeyZ': 'lockZ',

  // Navigation
  'KeyF': 'focus',
  'Digit0': 'cameraView',

  // Timeline
  'Space': 'playPause',
  'ArrowLeft': 'prevFrame',
  'ArrowRight': 'nextFrame',
  'Home': 'jumpStart',
  'End': 'jumpEnd',
  'KeyI': 'insertKeyframe',

  // General
  'KeyZ': 'undo',
  'Delete': 'delete',
  'Escape': 'deselect',

  'KeyS:ctrl': 'save'
}

/**
 * @param {{ setTransformMode, undo, redo, playPause, deleteSelected,
 *           jumpToStart, jumpToEnd, insertKeyframe, save, deselect,
 *           focusSelection, prevFrame, nextFrame }} actions
 * @returns {{ register: Function, unregister: Function }}
 */
export function useDirectorHotkeys(actions) {
  function handler(e) {
    // 在 input/textarea 中不处理
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return

    const ctrl = e.ctrlKey || e.metaKey
    const shift = e.shiftKey

    // Ctrl+Z / Ctrl+Shift+Z
    if (ctrl && e.code === 'KeyZ') {
      e.preventDefault()
      if (shift) actions.redo?.()
      else actions.undo?.()
      return
    }

    // Ctrl+S
    if (ctrl && e.code === 'KeyS') {
      e.preventDefault()
      actions.save?.()
      return
    }

    // Space
    if (e.code === 'Space') {
      e.preventDefault()
      actions.playPause?.()
      return
    }

    // W/E/R/S for transform
    if (!ctrl && ['KeyW', 'KeyG'].includes(e.code)) { actions.setTransformMode?.('translate'); return }
    if (!ctrl && ['KeyE', 'KeyR'].includes(e.code)) { actions.setTransformMode?.('rotate'); return }
    if (!ctrl && e.code === 'KeyS') { actions.setTransformMode?.('scale'); return }

    // F = focus
    if (!ctrl && e.code === 'KeyF') { actions.focusSelection?.(); return }
    // 0 = camera view
    if (ctrl && e.code === 'Digit0') { actions.cameraView?.(); return }

    // Timeline
    if (e.code === 'ArrowLeft') { actions.shiftKey ? actions.prevKeyframe?.() : actions.prevFrame?.(); return }
    if (e.code === 'ArrowRight') { actions.shiftKey ? actions.nextKeyframe?.() : actions.nextFrame?.(); return }
    if (e.code === 'Home') { actions.jumpToStart?.(); return }
    if (e.code === 'End') { actions.jumpToEnd?.(); return }
    if (e.code === 'KeyI') { actions.insertKeyframe?.(); return }

    // Delete
    if (e.code === 'Delete') { actions.deleteSelected?.(); return }
    // Escape
    if (e.code === 'Escape') { actions.deselect?.(); return }
  }

  return {
    register() { window.addEventListener('keydown', handler) },
    unregister() { window.removeEventListener('keydown', handler) }
  }
}
