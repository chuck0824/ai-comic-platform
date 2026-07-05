/**
 * 命令模式 Undo/Redo 栈。
 * 最大 100 步，连续 TRANSFORM 操作在 500ms 内合并。
 */

const MAX_DEPTH = 100
const MERGE_WINDOW_MS = 500

export const COMMAND_TYPES = [
  'ADD_OBJECT', 'REMOVE_OBJECT', 'TRANSFORM',
  'CHANGE_KEYFRAME', 'CHANGE_PROPERTY',
  'ADD_TRACK', 'REMOVE_TRACK', 'APPLY_PRESET'
]

export function createUndoStack(maxDepth = MAX_DEPTH) {
  let undo = []
  let redo = []

  return {
    push(command) {
      // 连续同类型 TRANSFORM 在合并窗口内合并
      if (command.type === 'TRANSFORM' && undo.length > 0) {
        const last = undo[undo.length - 1]
        if (last.type === 'TRANSFORM'
            && last.objectId === command.objectId
            && (command.timestamp - last.timestamp) < MERGE_WINDOW_MS) {
          undo[undo.length - 1] = { ...last, after: command.after, timestamp: command.timestamp }
          return
        }
      }
      undo.push(command)
      if (undo.length > maxDepth) undo.shift()
      redo = []
    },

    undo() {
      if (undo.length === 0) return null
      const cmd = undo.pop()
      redo.push(cmd)
      return cmd
    },

    redo() {
      if (redo.length === 0) return null
      const cmd = redo.pop()
      undo.push(cmd)
      return cmd
    },

    clear() { undo = []; redo = [] },
    depth() { return undo.length },
    canUndo() { return undo.length > 0 },
    canRedo() { return redo.length > 0 },
    peek() { return undo[undo.length - 1] },
    history() { return [...undo] }
  }
}
