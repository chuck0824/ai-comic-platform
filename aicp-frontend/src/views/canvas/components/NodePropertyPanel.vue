<template>
  <div v-if="node" class="property-panel">
    <div class="panel-header">
      <span><el-icon :size="14"><component :is="nodeIcon" /></el-icon> {{ nodeLabel }}</span>
      <el-button size="small" text @click="$emit('close')"><el-icon><Close /></el-icon></el-button>
    </div>

    <div class="panel-body">
      <div class="prop-group">
        <label>节点名称</label>
        <el-input v-model="localName" size="small" @change="save('name', localName)" />
      </div>

      <div class="prop-group">
        <label>坐标</label>
        <div class="flex gap-sm">
          <el-input v-model="localX" size="small" placeholder="X" @change="save('x', Number(localX))" />
          <el-input v-model="localY" size="small" placeholder="Y" @change="save('y', Number(localY))" />
        </div>
      </div>

      <div class="prop-group">
        <label>状态</label>
        <el-select v-model="localStatus" size="small" @change="save('status', localStatus)" style="width:100%">
          <el-option label="就绪" value="ready" />
          <el-option label="处理中" value="processing" />
          <el-option label="已完成" value="completed" />
          <el-option label="失败" value="failed" />
        </el-select>
      </div>

      <!-- Script node: shots preview -->
      <div v-if="node.type === 'script' && shots" class="prop-group">
        <label>分镜预览 ({{ shots.length }} 镜头)</label>
        <el-button size="small" type="primary" @click="$emit('openShotEditor')">
          全屏编辑分镜表
        </el-button>
      </div>

      <div class="prop-group">
        <label>生成 Prompt / 内容</label>
        <el-input v-model="localPrompt" type="textarea" :rows="3" size="small"
                  @change="saveData('prompt', localPrompt)" />
      </div>

      <div v-if="node.type === 'image' || node.type === 'video' || node.type === 'audio'" class="prop-group">
        <label>模型</label>
        <el-select v-model="localModelId" size="small" @change="saveData('model_id', localModelId)" style="width:100%">
          <el-option v-if="node.type === 'image'" label="Seedream 5.0" value="seedream-5.0" />
          <el-option v-if="node.type === 'image'" label="Flux 1.1 Pro" value="flux-1.1-pro" />
          <el-option v-if="node.type === 'video'" label="Seedance 2.0" value="seedance-2.0" />
          <el-option v-if="node.type === 'video'" label="Kling 1.6" value="kling-1.6" />
          <el-option v-if="node.type === 'audio'" label="Volcano TTS" value="volcano-tts" />
        </el-select>
      </div>

      <div v-if="node.type === 'image' || node.type === 'video'" class="prop-group">
        <label>画面比例</label>
        <el-select v-model="localAspectRatio" size="small" @change="saveData('aspect_ratio', localAspectRatio)" style="width:100%">
          <el-option label="9:16 竖版" value="9:16" />
          <el-option label="16:9 横版" value="16:9" />
          <el-option label="1:1 方形" value="1:1" />
        </el-select>
      </div>

      <div v-if="node.type === 'image' || node.type === 'video'" class="prop-group">
        <label>生成数量</label>
        <el-input-number v-model="localVariants" :min="1" :max="8" size="small"
                         @change="saveData('variants', localVariants)" />
      </div>

      <div v-if="node.type === 'video' || node.type === 'audio'" class="prop-group">
        <label>时长（秒）</label>
        <el-input-number v-model="localDuration" :min="1" :max="30" size="small"
                         @change="saveData('duration', localDuration)" />
      </div>

      <div v-if="node.type === 'image' || node.type === 'video'" class="prop-group">
        <el-button size="small" type="primary" class="mt-sm" @click="$emit('generate', node)">
          <el-icon><component :is="node.type === 'image' ? 'Picture' : 'VideoCamera'" /></el-icon> {{ node.type === 'image' ? '生成图片' : '生成视频' }}
        </el-button>
      </div>

      <!-- Actions -->
      <div class="prop-group actions">
        <el-button size="small" @click="$emit('duplicate', node)"><el-icon><CopyDocument /></el-icon> 复制节点</el-button>
        <el-button size="small" type="danger" @click="$emit('delete', node)"><el-icon><Delete /></el-icon> 删除</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'

const props = defineProps({
  node: { type: Object, default: null },
  shots: { type: Array, default: () => [] }
})
const emit = defineEmits(['close', 'update', 'openShotEditor', 'generate', 'duplicate', 'delete'])

const localName = ref('')
const localX = ref(0)
const localY = ref(0)
const localStatus = ref('ready')
const localPrompt = ref('')
const localModelId = ref('')
const localAspectRatio = ref('9:16')
const localVariants = ref(1)
const localDuration = ref(5)

// 简易 debounce，避免快速连续 blur-change 导致重复 API 调用
let _debounceTimers = {}
function debounce(key, fn, delay = 300) {
  if (_debounceTimers[key]) clearTimeout(_debounceTimers[key])
  _debounceTimers[key] = setTimeout(fn, delay)
}

const nodeIcon = computed(() => {
  const icons = {
    script: 'Film', image: 'Picture', video: 'VideoCamera', audio: 'Headset', text: 'EditPen',
    character: 'User', scene: 'PictureFilled', prompt: 'ChatLineRound',
    model: 'Cpu', output: 'Download', reference: 'Camera', workflow: 'SetUp', storyboard: 'Film',
    director: 'VideoCameraFilled'
  }
  return icons[props.node?.type] || 'Box'
})
const nodeLabel = computed(() => props.node?.name || props.node?.type || '节点')

watch(() => props.node, (n) => {
  if (!n) return
  localName.value = n.name || ''
  localX.value = n.x || 0
  localY.value = n.y || 0
  localStatus.value = n.status || 'ready'
  try {
    const data = typeof n.input_data === 'string' ? JSON.parse(n.input_data) : (n.input_data || n.data || {})
    localPrompt.value = data.prompt || ''
    localModelId.value = data.model_id || defaultModelId(n.type)
    localAspectRatio.value = data.aspect_ratio || '9:16'
    localVariants.value = data.variants || 1
    localDuration.value = data.duration || 5
  } catch {
    localPrompt.value = ''
    localModelId.value = defaultModelId(n.type)
    localAspectRatio.value = '9:16'
    localVariants.value = 1
    localDuration.value = 5
  }
}, { immediate: true })

function save(field, value) {
  debounce('save_' + field, () => emit('update', { [field]: value }), field === 'name' ? 400 : 150)
}
function saveData(field, value) {
  debounce('saveData_' + field, () => {
    try {
      const data = typeof props.node.input_data === 'string'
        ? JSON.parse(props.node.input_data)
        : (props.node.input_data || props.node.data || {})
      data[field] = value
      emit('update', { data })
    } catch { emit('update', { data: { [field]: value } }) }
  }, 300)
}

function defaultModelId(type) {
  return { image: 'seedream-5.0', video: 'seedance-2.0', audio: 'volcano-tts' }[type] || ''
}
</script>

<style scoped>
.property-panel {
  width: 280px; background: #1a1a2e; border-left: 1px solid #333;
  display: flex; flex-direction: column; height: 100%; overflow: hidden;
}
.panel-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 16px; border-bottom: 1px solid #333; font-weight: 600; font-size: 14px;
}
.panel-body { padding: 16px; overflow-y: auto; flex: 1; }
.prop-group { margin-bottom: 16px; }
.prop-group label { display: block; font-size: 12px; color: #888; margin-bottom: 6px; }
.flex { display: flex; } .gap-sm { gap: 8px; }
.mt-sm { margin-top: 8px; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
</style>
