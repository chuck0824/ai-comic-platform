<template>
  <div
    :class="['flow-node', 'node-' + data.nodeType, { selected }]"
    :style="{ '--node-accent': accent }"
    @contextmenu.prevent="$emit('node-context', $event, data.raw)"
  >
    <Handle
      id="in"
      type="target"
      :position="Position.Left"
      :connectable="true"
      class="flow-handle in"
    />

    <div class="node-shell">
      <div class="node-header">
        <span class="node-title">
          <span class="type-badge">
            <el-icon :size="13"><component :is="iconComp" /></el-icon>
          </span>
          <span class="title-text">{{ data.name }}</span>
        </span>
        <div class="node-meta">
          <span class="type-chip">{{ typeLabel }}</span>
          <span v-if="data.nodeType !== 'text'" :class="['node-status', statusClass]">{{ statusText }}</span>
        </div>
      </div>

      <div class="node-body">
        <div v-if="data.nodeType === 'script'" class="preview script">
          <div class="script-steps">
            <span>确认镜头</span><span>整理资产</span><span>提示词</span><span>批量生成</span>
          </div>
          <div class="hint">剧本 / 分镜生产节点</div>
          <el-button type="primary" size="small" @click.stop="$emit('open-shot', data.raw)">打开专业编辑器 ↗</el-button>
        </div>
        <div v-else-if="data.nodeType === 'image'" class="preview media">
          <img v-if="previewUrl" :src="previewUrl" alt="" />
          <div v-else class="ph"><el-icon><Picture /></el-icon> 图片预览</div>
        </div>
        <div v-else-if="data.nodeType === 'video'" class="preview media">
          <div class="ph video">▶ 视频预览</div>
        </div>
        <div v-else-if="data.nodeType === 'director'" class="preview director">
          <div class="mini-stage"><span /><span /><span /></div>
          <el-button type="primary" size="small" @click.stop="$emit('open-director', data.raw)">打开专业编辑器 ↗</el-button>
        </div>
        <div v-else-if="data.nodeType === 'audio'" class="preview audio">
          <div class="bars"><i /><i /><i /><i /><i /></div>
        </div>
        <div v-else-if="data.nodeType === 'prompt'" class="preview text">
          {{ promptText || '输入 Prompt，连到下游图片 / 视频 / 音频节点' }}
        </div>
        <div v-else-if="isMetaPreview" class="preview meta">
          <div class="meta-kicker">{{ metaKicker }}</div>
          <div class="meta-title">{{ metaTitle }}</div>
          <div class="meta-desc">{{ metaSummary }}</div>
        </div>
        <div v-else class="preview text">
          {{ promptText || '文本节点 — 点击在浮动编辑器中编辑' }}
        </div>
      </div>
    </div>

    <Handle
      id="out"
      type="source"
      :position="Position.Right"
      :connectable="true"
      class="flow-handle out"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import {
  Document, Picture, VideoCamera, Headset, Film, VideoCameraFilled,
  ChatLineRound, User, PictureFilled, Cpu, Download, Box
} from '@element-plus/icons-vue'
import { getNodeMeta } from '../nodeRegistry'

const props = defineProps({
  id: String,
  data: { type: Object, required: true },
  selected: Boolean
})

defineEmits(['open-shot', 'open-director', 'node-context'])

const ICON_MAP = {
  text: Document,
  prompt: ChatLineRound,
  character: User,
  scene: PictureFilled,
  image: Picture,
  video: VideoCamera,
  audio: Headset,
  model: Cpu,
  output: Download,
  script: Film,
  director: VideoCameraFilled
}

const meta = computed(() => getNodeMeta(props.data.nodeType))
const accent = computed(() => meta.value.accent)
const typeLabel = computed(() => meta.value.label)
const iconComp = computed(() => ICON_MAP[props.data.nodeType] || Box)
const isMetaPreview = computed(() =>
  ['character', 'scene', 'model', 'output'].includes(props.data.nodeType))

const statusText = computed(() => {
  const map = {
    ready: '就绪', pending: '排队', running: '生成中', completed: '完成',
    failed: '失败', draft: '草稿', editing: '编辑中'
  }
  return map[props.data.status] || props.data.status || '就绪'
})

const statusClass = computed(() => {
  const s = props.data.status
  if (s === 'completed') return 'ok'
  if (s === 'failed') return 'err'
  if (s === 'running' || s === 'pending') return 'run'
  return 'idle'
})

function readData(raw) {
  if (!raw) return {}
  if (raw.input_data && typeof raw.input_data === 'string') {
    try { return JSON.parse(raw.input_data) } catch { /* ignore */ }
  }
  if (raw.data && typeof raw.data === 'object') return raw.data
  return {}
}

const previewUrl = computed(() => {
  const d = readData(props.data.raw)
  return d.preview_url || d.image_url || d.url || d.reference_url || ''
})

const promptText = computed(() => {
  const d = readData(props.data.raw)
  return d.prompt || d.content || ''
})

const metaKicker = computed(() => {
  if (props.data.nodeType === 'character') return '角色设定'
  if (props.data.nodeType === 'scene') return '场景设定'
  if (props.data.nodeType === 'model') return '模型配置'
  if (props.data.nodeType === 'output') return '交付出口'
  return typeLabel.value
})

const metaTitle = computed(() => {
  const d = readData(props.data.raw)
  if (props.data.nodeType === 'model') return d.model_id || '未选择模型'
  if (props.data.nodeType === 'output') return d.title || props.data.name || '交付出口'
  return d.name || props.data.name || `未命名${typeLabel.value}`
})

const metaSummary = computed(() => {
  const d = readData(props.data.raw)
  if (props.data.nodeType === 'character') {
    return d.appearance || d.personality || d.prompt || '填写外观、性格与一致性 Prompt'
  }
  if (props.data.nodeType === 'scene') {
    return d.environment || d.atmosphere || d.prompt || '填写环境、氛围与场景 Prompt'
  }
  if (props.data.nodeType === 'model') {
    const cap = ({ image: '图像', video: '视频', audio: '音频', llm: '文本' }[d.capability] || '图像')
    return `能力：${cap}${d.notes ? ` · ${d.notes}` : ''}`
  }
  if (props.data.nodeType === 'output') {
    const format = ({
      package: '素材包', image: '图片', video: '视频', audio: '音频', manifest: '清单'
    }[d.format] || d.format || '素材包')
    return `格式：${format}${d.notes ? ` · ${d.notes}` : ''}`
  }
  return d.prompt || meta.value.short
})
</script>

<style scoped>
.flow-node {
  position: relative;
  color: #e4e4e7;
  min-height: 120px;
  overflow: visible;
}
.flow-node.selected .node-shell {
  border-color: var(--node-accent);
  box-shadow:
    0 0 0 2px color-mix(in srgb, var(--node-accent) 35%, transparent),
    0 10px 28px rgba(0, 0, 0, .4);
}
.node-shell {
  background: #1a1d2a;
  border: 1px solid #353b4d;
  border-radius: 12px;
  overflow: hidden;
  min-height: 120px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, .35);
}
.node-shell::before {
  content: '';
  display: block;
  height: 3px;
  background: linear-gradient(90deg, var(--node-accent), color-mix(in srgb, var(--node-accent) 20%, transparent));
}
.node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  background: #222636;
  cursor: grab;
  border-bottom: 1px solid #32384a;
  font-size: 12px;
  font-weight: 600;
}
.node-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.type-badge {
  width: 22px;
  height: 22px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: var(--node-accent);
  background: color-mix(in srgb, var(--node-accent) 16%, transparent);
}
.title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  font-weight: 400;
  flex-shrink: 0;
}
.type-chip {
  font-size: 10px;
  color: color-mix(in srgb, var(--node-accent) 85%, #fff);
  background: color-mix(in srgb, var(--node-accent) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--node-accent) 28%, transparent);
  padding: 1px 6px;
  border-radius: 999px;
}
.node-status {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  background: #3f3f46;
}
.node-status.ok { background: #14532d; color: #86efac; }
.node-status.err { background: #7f1d1d; color: #fecaca; }
.node-status.run { background: #713f12; color: #fde68a; }
.node-body { padding: 10px 12px; font-size: 12px; }
.preview.media {
  min-height: 100px;
  border-radius: 8px;
  overflow: hidden;
  background: #111827;
}
.preview.media img {
  width: 100%;
  height: 140px;
  object-fit: cover;
  display: block;
}
.ph {
  min-height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #71717a;
}
.script-steps {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.script-steps span {
  background: #2b3144;
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 10px;
  color: #a1a1aa;
}
.hint { color: #71717a; margin-bottom: 8px; }
.mini-stage {
  height: 72px;
  border-radius: 8px;
  background: linear-gradient(180deg, #0f172a, #1e293b);
  margin-bottom: 8px;
  position: relative;
  overflow: hidden;
}
.mini-stage span { position: absolute; border-radius: 4px; background: #64748b; }
.mini-stage span:nth-child(1) { left: 28%; bottom: 18px; width: 14px; height: 34px; }
.mini-stage span:nth-child(2) { right: 28%; bottom: 20px; width: 28px; height: 18px; background: #10b981; }
.mini-stage span:nth-child(3) { left: 50%; top: 18px; width: 18px; height: 12px; background: #3b82f6; transform: translateX(-50%); }
.bars {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 48px;
}
.bars i {
  width: 6px;
  background: var(--node-accent);
  border-radius: 2px;
  display: block;
  animation: pulse 1s ease-in-out infinite alternate;
}
.bars i:nth-child(1) { height: 40%; }
.bars i:nth-child(2) { height: 70%; animation-delay: .1s; }
.bars i:nth-child(3) { height: 100%; animation-delay: .2s; }
.bars i:nth-child(4) { height: 55%; animation-delay: .15s; }
.bars i:nth-child(5) { height: 30%; animation-delay: .25s; }
@keyframes pulse { to { opacity: .55; transform: scaleY(.7); } }
.text {
  color: #a1a1aa;
  line-height: 1.5;
  white-space: pre-wrap;
  max-height: 120px;
  overflow: hidden;
}
.preview.meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 72px;
  padding: 8px 10px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--node-accent) 8%, #12151f);
  border: 1px solid color-mix(in srgb, var(--node-accent) 18%, transparent);
}
.meta-kicker {
  font-size: 10px;
  letter-spacing: .06em;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--node-accent) 80%, #fff);
}
.meta-title { color: #e4e4e7; font-weight: 600; }
.meta-desc {
  color: #a1a1aa;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.flow-handle {
  width: 14px !important;
  height: 14px !important;
  border: 2px solid #fff !important;
  background: var(--node-accent) !important;
  z-index: 5 !important;
  cursor: crosshair !important;
}
.flow-handle.in {
  background: color-mix(in srgb, var(--node-accent) 55%, #10b981) !important;
}
.flow-handle::after {
  content: '';
  position: absolute;
  inset: -10px;
  border-radius: 50%;
}
</style>
