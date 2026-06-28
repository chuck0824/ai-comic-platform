<template>
  <section
    v-if="node"
    :class="['node-floating-editor', `placement-${placement}`, { collapsed }]"
    :style="style"
    @mousedown.stop
  >
    <header class="floating-head">
      <div class="node-title-wrap">
        <span class="node-type-icon">{{ typeIcon }}</span>
        <div>
          <strong>{{ draft.name || typeLabel }}</strong>
          <small :class="saveStateClass">{{ saveStateText }}</small>
        </div>
      </div>
      <div class="head-actions">
        <button class="icon-button" title="更多操作" @click="moreOpen = !moreOpen">•••</button>
        <button class="icon-button" :title="collapsed ? '展开' : '收起'" @click="collapsed = !collapsed">
          {{ collapsed ? '□' : '−' }}
        </button>
        <button class="icon-button" title="关闭" @click="$emit('close')">×</button>
      </div>
      <div v-if="moreOpen" class="more-menu">
        <button @click="emitAction('duplicate')">复制节点</button>
        <button @click="emitAction('reuse')">复用节点（保留连线）</button>
        <button @click="emitAction('save-asset')">保存为资产</button>
        <button class="danger" @click="emitAction('delete')">删除节点</button>
      </div>
    </header>

    <template v-if="!collapsed">
      <nav v-if="tabs.length > 1" class="floating-tabs" aria-label="节点编辑器页签">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>

      <div class="floating-body">
        <div v-if="activeTab === 'content' && node.type === 'text'" class="form-stack">
          <label class="field-label">
            <span>文本内容</span>
            <textarea
              v-model="draft.prompt"
              rows="8"
              placeholder="输入故事、场景、人物设定或提示词"
              @change="saveTextContent"
            />
          </label>
          <p class="field-hint">内容会同时作为当前文本节点的输出，连接下游节点时自动传递。</p>
        </div>

        <CanvasNodeAgentBox
          v-else-if="activeTab === 'agent' && node.type === 'text'"
          embedded
          :project-id="projectId"
          :node="node"
          :local-mode="localMode"
          @applied="$emit('agent-applied', $event)"
          @close="activeTab = 'content'"
        />

        <div v-else-if="isMediaNode && activeTab === 'generate'" class="form-stack">
          <label v-if="node.type === 'video' || node.type === 'audio'" class="field-label">
            <span>生成模式</span>
            <select v-model="draft.mode" @change="saveDataField('mode')">
              <option v-for="option in modeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label class="field-label">
            <span>{{ node.type === 'audio' ? '文本 / 提示词' : '提示词' }}</span>
            <textarea
              v-model="draft.prompt"
              rows="5"
              :placeholder="promptPlaceholder"
              @input="errors.prompt = ''"
              @change="saveDataField('prompt')"
            />
            <em v-if="errors.prompt" class="field-error">{{ errors.prompt }}</em>
          </label>

          <label v-if="node.type === 'video' && draft.mode === 'image_to_video'" class="field-label">
            <span>首帧图片</span>
            <input v-model="draft.first_frame_url" placeholder="连接图片节点或填写图片地址" @change="saveDataField('first_frame_url')" />
          </label>

          <div class="form-grid">
            <label class="field-label">
              <span>模型</span>
              <select v-model="draft.model_id" @change="saveDataField('model_id')">
                <option v-for="model in modelOptions" :key="model.value" :value="model.value">
                  {{ model.label }}
                </option>
              </select>
            </label>
            <label v-if="node.type !== 'audio'" class="field-label">
              <span>画面比例</span>
              <select v-model="draft.aspect_ratio" @change="saveDataField('aspect_ratio')">
                <option value="9:16">9:16 竖版</option>
                <option value="16:9">16:9 横版</option>
                <option value="1:1">1:1 方形</option>
                <option value="3:4">3:4</option>
                <option value="4:3">4:3</option>
              </select>
            </label>
            <label v-if="node.type === 'video' || node.type === 'audio'" class="field-label">
              <span>时长（秒）</span>
              <input v-model.number="draft.duration" type="number" min="1" max="30" @change="saveDataField('duration')" />
            </label>
            <label v-if="node.type !== 'audio'" class="field-label">
              <span>生成数量</span>
              <input v-model.number="draft.variants" type="number" min="1" max="8" @change="saveDataField('variants')" />
            </label>
            <label v-if="node.type === 'audio'" class="field-label">
              <span>音色</span>
              <select v-model="draft.voice" @change="saveDataField('voice')">
                <option value="默认音色">默认音色</option>
                <option value="温柔女声">温柔女声</option>
                <option value="沉稳男声">沉稳男声</option>
              </select>
            </label>
            <label v-if="node.type === 'audio'" class="field-label">
              <span>语速</span>
              <input v-model.number="draft.speed" type="number" min="0.5" max="2" step="0.1" @change="saveDataField('speed')" />
            </label>
          </div>
        </div>

        <div v-else-if="isMediaNode && activeTab === 'tools'" class="tool-grid">
          <button v-for="tool in toolOptions" :key="tool.label" @click="runTool(tool)">
            <span>{{ tool.icon }}</span>
            <strong>{{ tool.label }}</strong>
            <small>{{ tool.description }}</small>
          </button>
        </div>

        <div v-else-if="isMediaNode && activeTab === 'tasks'" class="task-summary">
          <div class="task-state-dot" :class="node.status"></div>
          <div>
            <strong>{{ statusText }}</strong>
            <p>{{ taskSummaryText }}</p>
          </div>
        </div>

        <div v-else-if="node.type === 'script'" class="special-summary">
          <div class="summary-icon">▦</div>
          <strong>{{ shots.length }} 个镜头</strong>
          <p>镜头字段、提示词合成和批量生成统一在分镜专业编辑器中完成。</p>
          <button class="primary wide" @click="$emit('open-shot-editor', node)">打开专业编辑器 ↗</button>
        </div>

        <div v-else-if="node.type === 'director'" class="special-summary">
          <div class="summary-icon">◫</div>
          <strong>{{ directorSummary }}</strong>
          <p>机位、站位、截图与发送到画布统一在导演台中完成。</p>
          <button class="primary wide" @click="$emit('open-director', node)">打开专业编辑器 ↗</button>
        </div>
      </div>

      <footer v-if="isMediaNode && activeTab === 'generate'" class="floating-footer">
        <button class="secondary" @click="resetDraft">重置</button>
        <button class="primary" :disabled="submitting" @click="submitGenerate">
          {{ submitting ? '提交中…' : primaryLabel }}
        </button>
      </footer>
    </template>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import CanvasNodeAgentBox from './CanvasNodeAgentBox.vue'
import {
  buildNodeDraft,
  buildTaskParameters,
  readNodeData,
  validateNodeDraft,
} from '../utils/nodeEditorData'

const props = defineProps({
  node: { type: Object, required: true },
  style: { type: Object, default: () => ({}) },
  placement: { type: String, default: 'right' },
  shots: { type: Array, default: () => [] },
  projectId: { type: String, required: true },
  localMode: { type: Boolean, default: false },
})

const emit = defineEmits([
  'close', 'update', 'generate', 'tool', 'open-shot-editor', 'open-director',
  'duplicate', 'reuse', 'save-asset', 'delete', 'agent-applied',
])

const draft = reactive({})
const errors = reactive({})
const activeTab = ref('generate')
const collapsed = ref(false)
const moreOpen = ref(false)
const submitting = ref(false)
const saveState = ref('saved')

const nodeId = computed(() => props.node?.uuid || String(props.node?.id || ''))
const isMediaNode = computed(() => ['image', 'video', 'audio'].includes(props.node.type))
const typeLabel = computed(() => ({
  text: '文本节点', image: '图片节点', video: '视频节点', audio: '音频节点',
  script: '脚本节点', director: '导演台节点',
}[props.node.type] || '节点'))
const typeIcon = computed(() => ({ text: '▤', image: '▧', video: '▶', audio: '♫', script: '▦', director: '◫' }[props.node.type] || '◇'))
const tabs = computed(() => {
  if (props.node.type === 'text') return [{ key: 'content', label: '内容' }, { key: 'agent', label: 'AI 助手' }]
  if (isMediaNode.value) return [{ key: 'generate', label: '生成' }, { key: 'tools', label: '工具' }, { key: 'tasks', label: '任务' }]
  return []
})
const modeOptions = computed(() => props.node.type === 'video'
  ? [
      { label: '文生视频', value: 'video' },
      { label: '图生视频', value: 'image_to_video' },
      { label: '首尾帧视频', value: 'first_last_frame' },
      { label: '视频延展', value: 'video_extend' },
    ]
  : [
      { label: '文字转语音', value: 'tts' },
      { label: '生成音乐', value: 'music' },
      { label: '生成音效', value: 'sfx' },
    ])
const modelOptions = computed(() => ({
  image: [{ label: 'Seedream 5.0', value: 'seedream-5.0' }, { label: 'Flux 1.1 Pro', value: 'flux-1.1-pro' }],
  video: [{ label: 'Seedance 2.0', value: 'seedance-2.0' }, { label: 'Kling 1.6', value: 'kling-1.6' }],
  audio: [{ label: 'Volcano TTS', value: 'volcano-tts' }],
}[props.node.type] || []))
const toolOptions = computed(() => ({
  image: [
    { label: '发送到视频', description: '创建下游视频节点', icon: '→', creates: 'video' },
    { label: '图像编辑', description: '局部修改与重绘', icon: '✎', taskType: 'image' },
    { label: '多图参考融合', description: '融合多张参考图', icon: '▧', taskType: 'image' },
    { label: '高清放大', description: '提升分辨率与细节', icon: '↗', taskType: 'image' },
  ],
  video: [
    { label: '配音', description: '创建下游音频节点', icon: '♫', creates: 'audio' },
    { label: '字幕', description: '识别人声生成字幕', icon: '字', taskType: 'audio' },
    { label: '视频剪辑', description: '进入视频处理任务', icon: '✂', taskType: 'video' },
    { label: '视频高清', description: '提升视频清晰度', icon: '↗', taskType: 'video' },
  ],
  audio: [
    { label: '转字幕', description: '识别音频并生成字幕', icon: '字', taskType: 'audio' },
    { label: '音频截取', description: '选择需要保留的片段', icon: '✂', taskType: 'audio' },
    { label: '音频变速', description: '调整播放速度', icon: '↯', taskType: 'audio' },
  ],
}[props.node.type] || []))
const promptPlaceholder = computed(() => ({
  image: '描述画面主体、场景、构图、光线和风格',
  video: '描述画面动态、人物动作和运镜',
  audio: '输入旁白、对白、音乐或音效要求',
}[props.node.type] || '输入提示词'))
const primaryLabel = computed(() => ({ image: '生成图片', video: '生成视频', audio: '生成音频' }[props.node.type] || '执行生成'))
const saveStateText = computed(() => ({ saving: '保存中', saved: '已保存', error: '保存失败' }[saveState.value]))
const saveStateClass = computed(() => `save-${saveState.value}`)
const statusText = computed(() => ({ ready: '准备就绪', processing: '生成中', completed: '已完成', failed: '生成失败' }[props.node.status] || '等待配置'))
const taskSummaryText = computed(() => props.node.status === 'failed' ? '保留当前参数，可返回生成页修改后重试。' : '任务状态与结果会回写到当前节点。')
const directorSummary = computed(() => {
  const director = readNodeData(props.node).director || {}
  return `${director.elements?.length || 0} 个元素 · ${director.shots?.length || 0} 个截图`
})

watch(nodeId, resetDraft, { immediate: true })

function resetDraft() {
  Object.keys(draft).forEach(key => delete draft[key])
  Object.assign(draft, buildNodeDraft(props.node))
  Object.keys(errors).forEach(key => delete errors[key])
  activeTab.value = props.node.type === 'text' ? 'content' : 'generate'
  moreOpen.value = false
  saveState.value = 'saved'
}

async function emitUpdate(updates) {
  saveState.value = 'saving'
  try {
    emit('update', { node: props.node, updates })
    saveState.value = 'saved'
  } catch {
    saveState.value = 'error'
  }
}

function saveDataField(field) {
  emitUpdate({ data: { ...readNodeData(props.node), [field]: draft[field] } })
}

function saveTextContent() {
  emitUpdate({ data: { ...readNodeData(props.node), prompt: draft.prompt, content: draft.prompt }, status: 'ready' })
}

function submitGenerate() {
  Object.assign(errors, validateNodeDraft(props.node.type, draft))
  if (Object.keys(errors).some(key => errors[key])) return
  const parameters = buildTaskParameters(draft)
  submitting.value = true
  emit('generate', {
    node: props.node,
    data: { ...readNodeData(props.node), ...parameters },
    action: {
      label: primaryLabel.value,
      taskType: props.node.type,
      modelId: draft.model_id,
      parameters,
    },
  })
  window.setTimeout(() => { submitting.value = false }, 450)
}

function runTool(tool) {
  emit('tool', { node: props.node, action: { ...tool, modelId: draft.model_id, parameters: buildTaskParameters(draft) } })
}

function emitAction(name) {
  moreOpen.value = false
  emit(name, props.node)
}
</script>

<style scoped>
.node-floating-editor {
  position: absolute;
  z-index: 90;
  width: 440px;
  max-width: calc(100% - 32px);
  max-height: calc(100% - 32px);
  display: flex;
  flex-direction: column;
  overflow: visible;
  color: #e5e7eb;
  background: rgba(18, 24, 38, .98);
  border: 1px solid #3b4256;
  border-radius: 16px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, .5), 0 0 0 1px rgba(129, 140, 248, .08);
  backdrop-filter: blur(18px);
}
.node-floating-editor::before {
  content: '';
  position: absolute;
  width: 14px;
  height: 14px;
  background: #121826;
  border: 1px solid #3b4256;
  transform: rotate(45deg);
  z-index: -1;
}
.placement-right::before { left: -8px; top: 44px; border-top: 0; border-right: 0; }
.placement-left::before { right: -8px; top: 44px; border-bottom: 0; border-left: 0; }
.placement-bottom::before { top: -8px; left: 44px; border-bottom: 0; border-right: 0; }
.placement-top::before { bottom: -8px; left: 44px; border-top: 0; border-left: 0; }
.node-floating-editor.collapsed { width: 320px; }
.floating-head {
  position: relative;
  min-height: 58px;
  padding: 10px 12px 10px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #2d3548;
}
.collapsed .floating-head { border-bottom: 0; }
.node-title-wrap { display: flex; align-items: center; gap: 10px; min-width: 0; }
.node-title-wrap strong { display: block; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.node-title-wrap small { display: block; margin-top: 2px; color: #34d399; font-size: 10px; }
.node-title-wrap small.save-saving { color: #fbbf24; }
.node-title-wrap small.save-error { color: #f87171; }
.node-type-icon { width: 30px; height: 30px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 9px; color: #c7d2fe; background: #252d48; }
.head-actions { display: flex; align-items: center; gap: 3px; }
.icon-button { width: 30px; height: 30px; border: 0; border-radius: 8px; color: #aeb8cb; background: transparent; cursor: pointer; }
.icon-button:hover { color: #fff; background: #293247; }
.more-menu { position: absolute; right: 70px; top: 46px; z-index: 5; min-width: 190px; overflow: hidden; border: 1px solid #3b4256; border-radius: 10px; background: #111827; box-shadow: 0 14px 36px rgba(0,0,0,.45); }
.more-menu button { width: 100%; padding: 9px 12px; border: 0; color: #dbe4f4; background: transparent; text-align: left; cursor: pointer; }
.more-menu button:hover { background: #222c40; }
.more-menu .danger { color: #fca5a5; }
.floating-tabs { display: flex; gap: 20px; min-height: 43px; padding: 0 16px; border-bottom: 1px solid #2d3548; }
.floating-tabs button { position: relative; border: 0; color: #8e9ab0; background: transparent; font-weight: 700; cursor: pointer; }
.floating-tabs button.active { color: #a5b4fc; }
.floating-tabs button.active::after { content: ''; position: absolute; left: 0; right: 0; bottom: -1px; height: 2px; background: #818cf8; }
.floating-body { min-height: 120px; padding: 16px; overflow: auto; }
.form-stack { display: grid; gap: 13px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.field-label { display: grid; gap: 6px; color: #9ca8bd; font-size: 11px; font-style: normal; }
.field-label textarea, .field-label input, .field-label select {
  box-sizing: border-box;
  width: 100%;
  border: 1px solid #3b465c;
  border-radius: 9px;
  outline: none;
  color: #eef2ff;
  background: #0f1726;
  padding: 9px 10px;
  font: inherit;
  font-size: 12px;
}
.field-label textarea { resize: vertical; min-height: 90px; line-height: 1.65; }
.field-label textarea:focus, .field-label input:focus, .field-label select:focus { border-color: #818cf8; box-shadow: 0 0 0 3px rgba(99,102,241,.15); }
.field-hint { margin: 0; color: #778399; font-size: 11px; line-height: 1.6; }
.field-error { color: #fca5a5; font-style: normal; font-size: 11px; }
.floating-footer { display: flex; justify-content: flex-end; gap: 10px; padding: 12px 16px 14px; border-top: 1px solid #2d3548; }
.primary, .secondary { min-height: 36px; padding: 0 18px; border-radius: 9px; font-weight: 700; cursor: pointer; }
.primary { border: 1px solid #4f78ff; color: #fff; background: linear-gradient(180deg, #4f8cff, #3478ec); box-shadow: 0 7px 18px rgba(52,120,236,.22); }
.primary:hover { filter: brightness(1.08); }
.primary:disabled { opacity: .55; cursor: wait; }
.secondary { border: 1px solid #3b465c; color: #cbd5e1; background: #1a2233; }
.wide { width: 100%; margin-top: 8px; }
.tool-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.tool-grid button { min-height: 88px; padding: 12px; display: grid; grid-template-columns: 28px 1fr; grid-template-rows: auto auto; column-gap: 8px; border: 1px solid #33405a; border-radius: 11px; color: #dbe5f5; background: #151e30; text-align: left; cursor: pointer; }
.tool-grid button:hover { border-color: #6477a8; background: #1a263b; }
.tool-grid span { grid-row: 1 / 3; width: 28px; height: 28px; display: grid; place-items: center; border-radius: 8px; color: #c7d2fe; background: #28324d; }
.tool-grid strong { font-size: 12px; }
.tool-grid small { color: #7f8ca3; font-size: 10px; }
.task-summary { display: flex; align-items: flex-start; gap: 12px; min-height: 110px; padding: 16px; border: 1px solid #33405a; border-radius: 12px; background: #111a2a; }
.task-summary p { color: #8794a9; line-height: 1.6; }
.task-state-dot { width: 11px; height: 11px; margin-top: 4px; border-radius: 50%; background: #64748b; }
.task-state-dot.processing { background: #f59e0b; box-shadow: 0 0 10px rgba(245,158,11,.6); }
.task-state-dot.completed { background: #34d399; }
.task-state-dot.failed { background: #f87171; }
.special-summary { display: grid; justify-items: center; gap: 9px; padding: 12px 8px 6px; text-align: center; }
.special-summary p { max-width: 340px; margin: 0; color: #8794a9; font-size: 12px; line-height: 1.7; }
.summary-icon { width: 54px; height: 54px; display: grid; place-items: center; border-radius: 15px; color: #c7d2fe; background: #252f4a; font-size: 24px; }
@media (max-width: 900px) {
  .node-floating-editor { width: 380px; }
  .form-grid, .tool-grid { grid-template-columns: 1fr; }
}
</style>
