const TYPE_DEFAULTS = {
  text: { prompt: '', content: '' },
  prompt: { prompt: '', tags: '' },
  character: {
    name: '',
    appearance: '',
    personality: '',
    prompt: '',
    reference_url: '',
  },
  scene: {
    name: '',
    environment: '',
    atmosphere: '',
    prompt: '',
    reference_url: '',
  },
  image: {
    prompt: '',
    model_id: 'seedream-5.0',
    aspect_ratio: '9:16',
    variants: 1,
    mode: 'image',
  },
  video: {
    prompt: '',
    model_id: 'seedance-2.0',
    aspect_ratio: '9:16',
    duration: 5,
    variants: 1,
    mode: 'video',
  },
  audio: {
    prompt: '',
    model_id: 'volcano-tts',
    duration: 5,
    speed: 1,
    voice: '默认音色',
    mode: 'tts',
  },
  model: {
    model_id: 'seedream-5.0',
    capability: 'image',
    notes: '',
  },
  output: {
    title: '',
    format: 'package',
    notes: '',
  },
  script: { prompt: '' },
  director: {},
}

const TASK_FIELDS = [
  'prompt',
  'model_id',
  'aspect_ratio',
  'duration',
  'variants',
  'mode',
  'voice',
  'speed',
  'reference_url',
  'first_frame_url',
]

export function readNodeData(node) {
  if (!node) return {}
  try {
    const raw = node.input_data ?? node.inputData ?? node.data ?? {}
    return typeof raw === 'string' ? JSON.parse(raw || '{}') : { ...raw }
  } catch {
    return {}
  }
}

export function readNodeOutput(node) {
  if (!node) return {}
  try {
    const raw = node.output_data ?? node.outputData ?? {}
    return typeof raw === 'string' ? JSON.parse(raw || '{}') : { ...raw }
  } catch {
    return {}
  }
}

function pickPreviewUrl(data) {
  if (!data || typeof data !== 'object') return ''
  return data.preview_url || data.image_url || data.url || data.reference_url || ''
}

export function readNodePreviewUrl(node) {
  return pickPreviewUrl(readNodeData(node)) || pickPreviewUrl(readNodeOutput(node))
}

export function slashCommandForNode(type) {
  return {
    image: 'generate-image',
    video: 'generate-video',
    audio: 'generate-audio',
  }[type] || 'generate-text'
}

export function buildNodeDraft(node) {
  const data = readNodeData(node)
  return {
    ...(TYPE_DEFAULTS[node?.type] || {}),
    ...data,
    name: node?.name || '',
  }
}

export function buildTaskParameters(draft) {
  return TASK_FIELDS.reduce((parameters, key) => {
    if (draft[key] !== undefined && draft[key] !== '') parameters[key] = draft[key]
    return parameters
  }, {})
}

export function validateNodeDraft(type, draft, options = {}) {
  const localPrompt = String(draft.prompt || '').trim()
  const compiledPrompt = String(options.compiledPrompt || '').trim()
  if (['image', 'video', 'audio'].includes(type) && !localPrompt && !compiledPrompt) {
    return { prompt: type === 'audio' ? '请输入文本或提示词' : '请输入提示词' }
  }
  if (type === 'prompt' && !String(draft.prompt || '').trim()) {
    return { prompt: '请输入 Prompt 内容' }
  }
  if (type === 'model' && !String(draft.model_id || '').trim()) {
    return { model_id: '请选择模型' }
  }
  return {}
}

export function shouldSelectNode(currentNodeId, targetNodeId) {
  return String(currentNodeId || '') !== String(targetNodeId || '')
}
