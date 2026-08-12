/**
 * 画布节点 Registry — 侧栏、双击菜单、Flow 节点、默认尺寸共用。
 */
export const NODE_TYPE_META = {
  script: {
    type: 'script',
    icon: 'Film',
    label: '剧本',
    short: '分镜拆解与批量生成',
    desc: '剧本拆解、资产管理、批量生图/视频',
    group: '编排节点',
    accent: '#818cf8',
    width: 340,
    height: 280,
  },
  prompt: {
    type: 'prompt',
    icon: 'ChatLineRound',
    label: 'Prompt',
    short: '可复用提示词',
    desc: '独立提示词，可连到下游生成节点',
    group: '编排节点',
    accent: '#38bdf8',
    width: 300,
    height: 200,
  },
  character: {
    type: 'character',
    icon: 'User',
    label: '角色',
    short: '外观与一致性',
    desc: '角色设定、外观与一致性参考',
    group: '编排节点',
    accent: '#f472b6',
    width: 280,
    height: 220,
  },
  scene: {
    type: 'scene',
    icon: 'PictureFilled',
    label: '场景',
    short: '环境与氛围',
    desc: '场景描述、环境与氛围设定',
    group: '编排节点',
    accent: '#34d399',
    width: 280,
    height: 220,
  },
  image: {
    type: 'image',
    icon: 'Picture',
    label: '图片',
    short: '文生图 / 上传',
    desc: '上传图片或图像模型生成',
    group: '媒体节点',
    accent: '#a78bfa',
    width: 420,
    height: 300,
  },
  video: {
    type: 'video',
    icon: 'VideoCamera',
    label: '视频',
    short: '文生视频 / 图生视频',
    desc: '上传视频或视频模型生成',
    group: '媒体节点',
    accent: '#fb7185',
    width: 420,
    height: 300,
  },
  audio: {
    type: 'audio',
    icon: 'Headset',
    label: '音频',
    short: 'TTS / 音乐 / 音效',
    desc: '上传音频、音乐、音效或 TTS',
    group: '媒体节点',
    accent: '#fbbf24',
    width: 300,
    height: 240,
  },
  model: {
    type: 'model',
    icon: 'Cpu',
    label: '模型',
    short: '选型与能力参数',
    desc: '选择模型与能力参数，供下游节点复用',
    group: '模型与输出',
    accent: '#94a3b8',
    width: 280,
    height: 190,
  },
  output: {
    type: 'output',
    icon: 'Download',
    label: '输出',
    short: '交付出口',
    desc: '汇总上游结果，标记交付出口',
    group: '模型与输出',
    accent: '#2dd4bf',
    width: 260,
    height: 170,
  },
  text: {
    type: 'text',
    icon: 'Document',
    label: '文本',
    short: '自由文本 / Agent',
    desc: '手动输入或大语言模型生成',
    group: '画布工具',
    accent: '#64748b',
    width: 480,
    height: 360,
  },
  director: {
    type: 'director',
    icon: 'VideoCameraFilled',
    label: '导演台',
    short: '3D 构图与截图',
    desc: '轻量 3D 构图、机位截图、发送到画布',
    group: '画布工具',
    accent: '#60a5fa',
    width: 280,
    height: 220,
  },
}

/** 侧栏 / 创建菜单展示顺序 */
export const NODE_TYPE_ORDER = [
  'script', 'prompt', 'character', 'scene',
  'image', 'video', 'audio',
  'model', 'output',
  'text', 'director',
]

export const NODE_TYPES = NODE_TYPE_ORDER.map((type) => {
  const meta = NODE_TYPE_META[type]
  return {
    type: meta.type,
    icon: meta.icon,
    label: meta.label,
    desc: meta.desc,
    short: meta.short,
    group: meta.group,
    accent: meta.accent,
  }
})

export function getNodeMeta(type) {
  return NODE_TYPE_META[type] || {
    type: type || 'unknown',
    icon: 'Box',
    label: '节点',
    short: '',
    desc: '',
    group: '其他',
    accent: '#71717a',
    width: 240,
    height: 180,
  }
}

export function getNodeSize(type) {
  const meta = getNodeMeta(type)
  return { width: meta.width, height: meta.height }
}

export function getNodeAccent(type) {
  return getNodeMeta(type).accent
}
