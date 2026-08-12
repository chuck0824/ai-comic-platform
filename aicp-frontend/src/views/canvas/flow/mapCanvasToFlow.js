/**
 * Map AICP canvas domain model ↔ Vue Flow graph elements.
 */

export function nodeKey(node) {
  if (!node) return ''
  return node.uuid || String(node.id)
}

export function nodeWidth(nodeOrType) {
  const type = typeof nodeOrType === 'string' ? nodeOrType : nodeOrType?.type
  const saved = typeof nodeOrType === 'object' ? Number(nodeOrType?.width) : 0
  if (saved > 0) return saved
  return {
    script: 340,
    director: 280,
    text: 560,
    image: 520,
    video: 520,
    audio: 520
  }[type] || 200
}

export function toFlowNodes(domainNodes = []) {
  return domainNodes.map((n) => {
    const id = nodeKey(n)
    return {
      id,
      type: 'aicp',
      position: { x: Number(n.x) || 0, y: Number(n.y) || 0 },
      data: {
        raw: n,
        nodeType: n.type || 'text',
        name: n.name || n.label || `${n.type || '节点'}`,
        status: n.status || 'ready'
      },
      style: { width: `${nodeWidth(n)}px` },
      dragHandle: '.node-header',
      connectable: true,
      sourcePosition: 'right',
      targetPosition: 'left'
    }
  })
}

export function toFlowEdges(domainConnections = [], domainNodes = []) {
  const idSet = new Set(domainNodes.map(nodeKey))
  const byNumeric = new Map()
  domainNodes.forEach((n) => {
    if (n?.id != null) byNumeric.set(String(n.id), nodeKey(n))
  })

  function resolveEndpoint(ref) {
    if (ref == null) return null
    const s = String(ref)
    if (idSet.has(s)) return s
    return byNumeric.get(s) || s
  }

  return domainConnections
    .map((c) => {
      const source = resolveEndpoint(c.source_node_id ?? c.sourceNodeId ?? c.source)
      const target = resolveEndpoint(c.target_node_id ?? c.targetNodeId ?? c.target)
      if (!source || !target) return null
      return {
        id: String(c.uuid || c.id || `${source}-${target}`),
        source,
        target,
        sourceHandle: 'out',
        targetHandle: 'in',
        type: 'default',
        animated: false,
        data: { raw: c }
      }
    })
    .filter(Boolean)
}
