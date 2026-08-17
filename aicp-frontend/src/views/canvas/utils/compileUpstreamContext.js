import { readNodeData, readNodePreviewUrl } from './nodeEditorData.js'

const CONTEXT_TYPES = new Set(['prompt', 'character', 'scene', 'model', 'text', 'image'])

function nodeKey(node) {
  if (!node) return ''
  return node.uuid || String(node.id ?? '')
}

function matchesRef(node, ref) {
  if (!node || ref == null) return false
  const value = String(ref)
  return node.uuid === value || String(node.id) === value
}

function connectionEnds(connection) {
  return {
    source: connection?.source_node_id ?? connection?.sourceNodeId ?? connection?.source,
    target: connection?.target_node_id ?? connection?.targetNodeId ?? connection?.target,
  }
}

function incomingSources(node, nodes = [], connections = []) {
  return connections
    .map(connectionEnds)
    .filter(({ target }) => matchesRef(node, target))
    .map(({ source }) => nodes.find(item => matchesRef(item, source)))
    .filter(Boolean)
}

function collectUpstream(node, nodes = [], connections = [], visited = new Set(), depth = 0) {
  if (!node || depth > 4) return []
  const key = nodeKey(node)
  if (!key || visited.has(key)) return []
  visited.add(key)
  const collected = []
  for (const source of incomingSources(node, nodes, connections)) {
    if (CONTEXT_TYPES.has(source.type)) collected.push(source)
    collected.push(...collectUpstream(source, nodes, connections, visited, depth + 1))
  }
  return collected
}

function uniqueNodes(nodes) {
  const seen = new Set()
  return nodes.filter(node => {
    const key = nodeKey(node)
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function joinParts(parts) {
  return parts.map(part => String(part || '').trim()).filter(Boolean).join('。')
}

function describeCharacter(data) {
  const name = data.name ? `角色「${data.name}」` : '角色'
  return joinParts([
    name,
    data.appearance ? `外观：${data.appearance}` : '',
    data.personality ? `性格：${data.personality}` : '',
    data.prompt,
  ])
}

function describeScene(data) {
  const name = data.name ? `场景「${data.name}」` : '场景'
  return joinParts([
    name,
    data.environment ? `环境：${data.environment}` : '',
    data.atmosphere ? `氛围：${data.atmosphere}` : '',
    data.prompt,
  ])
}

function describePrompt(data) {
  return String(data.prompt || data.content || '').trim()
}

export function compileUpstreamContext(node, nodes = [], connections = []) {
  const local = readNodeData(node)
  const sources = uniqueNodes(collectUpstream(node, nodes, connections))
  const fragments = []
  const labels = []
  let modelId = ''
  let firstFrameUrl = local.first_frame_url || ''
  let referenceUrl = local.reference_url || ''

  for (const source of sources) {
    const data = readNodeData(source)
    if (source.type === 'character') {
      const text = describeCharacter(data)
      if (text) {
        fragments.push(text)
        labels.push(data.name ? `角色「${data.name}」` : '角色')
      }
      if (!referenceUrl) referenceUrl = data.reference_url || readNodePreviewUrl(source)
    } else if (source.type === 'scene') {
      const text = describeScene(data)
      if (text) {
        fragments.push(text)
        labels.push(data.name ? `场景「${data.name}」` : '场景')
      }
      if (!referenceUrl) referenceUrl = data.reference_url || readNodePreviewUrl(source)
    } else if (source.type === 'prompt' || source.type === 'text') {
      const text = describePrompt(data)
      if (text) {
        fragments.push(text)
        labels.push(source.type === 'text' ? '文本' : 'Prompt')
      }
    } else if (source.type === 'model' && data.model_id) {
      modelId = data.model_id
      labels.push(`模型 ${data.model_id}`)
    } else if (source.type === 'image') {
      const preview = readNodePreviewUrl(source)
      if (preview && !firstFrameUrl) firstFrameUrl = preview
      if (preview && !referenceUrl) referenceUrl = preview
      labels.push('图片参考')
    }
  }

  const localPrompt = String(local.prompt || local.content || '').trim()
  const compiledPrompt = [...fragments, localPrompt].filter(Boolean).join('\n')

  return {
    prompt: compiledPrompt,
    compiled_prompt: compiledPrompt,
    local_prompt: localPrompt,
    model_id: modelId || local.model_id || '',
    first_frame_url: firstFrameUrl,
    reference_url: referenceUrl,
    sources: labels,
    hasUpstream: sources.length > 0,
  }
}
