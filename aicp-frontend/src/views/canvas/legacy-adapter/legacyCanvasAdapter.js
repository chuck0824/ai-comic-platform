/**
 * 旧画布数据投影适配器。
 * 将旧节点形状映射为新类型，不做持久化修改。
 */

/**
 * 将旧 reference 节点投影为新类型。
 * 包含 director 键的 JSON → director；否则标记 needsConfirmation。
 *
 * @param {{ type: string, data?: object, inputData?: string }} node
 * @returns {{ type: string, needsConfirmation: boolean }}
 */
export function projectLegacyNode(node) {
  if (node.type !== 'reference') return { type: node.type, needsConfirmation: false }

  let parsed
  try {
    parsed = typeof node.data === 'string'
      ? JSON.parse(node.data)
      : (node.data || JSON.parse(node.inputData || '{}'))
  } catch {
    return { type: 'reference', needsConfirmation: true }
  }

  if (parsed.director) {
    return { type: 'director', needsConfirmation: false }
  }

  return { type: 'reference', needsConfirmation: true }
}

/**
 * 获取节点对应的显示类型（用于旧数据兼容渲染）。
 */
export function displayType(node) {
  return projectLegacyNode(node).type
}
