<template>
  <div class="director-workspace-v2">
    <!-- Top bar -->
    <div class="dw-topbar">
      <el-button text @click="goBack">← 返回 Canvas</el-button>
      <span class="dw-shot-label">SHOT · {{ document.fps }}fps · {{ document.durationMs }}ms</span>
      <span v-if="dirty" class="dw-dirty">未保存</span>
      <span class="dw-revision" v-if="currentRevisionId">冻结版本 #{{ currentRevisionId }}</span>
      <div class="dw-actions">
        <el-button size="small" @click="handleValidate">校验</el-button>
        <el-button size="small" type="primary" :disabled="validationErrors.length > 0" @click="handleFreeze">冻结</el-button>
      </div>
    </div>

    <div class="dw-body">
      <!-- Left: Scene tree -->
      <aside class="dw-left">
        <div class="dw-panel-title">场景树</div>
        <div class="dw-scene-tree">
          <div v-for="obj in document.objects" :key="obj.id"
               :class="['dw-tree-item', { selected: selectedObjectId === obj.id }]"
               @click="selectObject(obj.id)">
            <span>{{ objectIcon(obj.type) }}</span>
            <span>{{ obj.name }}</span>
            <span class="dw-tree-badge">{{ obj.type }}</span>
          </div>
          <div v-if="!document.objects.length" class="dw-empty">暂无对象</div>
        </div>
        <div class="dw-preset-section">
          <div class="dw-panel-title">预设</div>
          <el-select size="small" placeholder="相机预设" @change="applyCameraPreset">
            <el-option v-for="p in cameraPresets" :key="p.id" :label="p.label" :value="p.id" />
          </el-select>
          <el-select size="small" placeholder="动作预设" @change="applyActionPreset" style="margin-top:4px">
            <el-option v-for="p in actionPresets" :key="p.id" :label="p.label" :value="p.id" />
          </el-select>
        </div>
      </aside>

      <!-- Center: Three.js viewport -->
      <main class="dw-viewport" ref="viewportRef">
        <canvas ref="canvasRef" @contextmenu.prevent></canvas>
        <!-- Validation overlay -->
        <div v-if="showValidation" class="dw-validation-overlay">
          <div v-for="e in validationErrors" :key="e.code" class="dw-val-error">{{ e.message }}</div>
          <div v-for="w in validationWarnings" :key="w.code" class="dw-val-warning">{{ w.message }}</div>
          <el-button size="small" @click="showValidation = false">关闭</el-button>
        </div>
      </main>

      <!-- Right: Properties -->
      <aside class="dw-right">
        <div class="dw-panel-title">{{ selectedObject ? selectedObject.name : '属性' }}</div>
        <div v-if="selectedObject" class="dw-props">
          <label>名称 <el-input v-model="selectedObject.name" size="small" @change="markDirty" /></label>
          <label>位置 X/Y/Z</label>
          <div class="dw-vec3">
            <el-input-number v-model="selectedObject.position.x" :step="0.1" size="small" @change="markDirty" />
            <el-input-number v-model="selectedObject.position.y" :step="0.1" size="small" @change="markDirty" />
            <el-input-number v-model="selectedObject.position.z" :step="0.1" size="small" @change="markDirty" />
          </div>
        </div>
        <div v-else class="dw-empty">选择对象或相机查看属性</div>
      </aside>
    </div>

    <!-- Bottom: Timeline -->
    <div class="dw-timeline-bar">
      <button :class="{ active: playing }" @click="togglePlay">▶</button>
      <span>{{ formatTime(currentTimeMs) }} / {{ formatTime(document.durationMs) }}</span>
      <input type="range" :min="0" :max="document.durationMs" :value="currentTimeMs"
             @input="seekTo($event.target.value)" class="dw-scrubber" />
      <span>{{ document.fps }}fps</span>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { canvasApi } from '@/api/canvas.js'
import { createEmptyDocument, shouldAutosave } from './state/directorDocument.js'
import { createUndoStack } from './state/undoStack.js'
import { useDirectorHotkeys } from './state/useDirectorHotkeys.js'
import { useCanvasUIState } from '../composables/useCanvasUIState.js'
import { DIRECTOR_PRESETS } from './presets/directorPresets.js'
import { validateDirectorDocument } from './validation/directorValidation.js'
import { createSceneController } from './viewport/threeSceneController.js'

const router = useRouter()
const route = useRoute()

const projectId = route.params.projectId
const unitId = route.params.shotUnitId || route.params.unitId

// Document state
const document = reactive(createEmptyDocument())
const dirty = ref(false)
const playing = ref(false)
const currentTimeMs = ref(0)
const selectedObjectId = ref(null)
const draftVersion = ref(0)
const currentRevisionId = ref(null)

// Validation
const validationErrors = ref([])
const validationWarnings = ref([])
const showValidation = ref(false)

// Undo/Redo
const undoStack = createUndoStack()
let autosaveTimer = null

// Three.js
const canvasRef = ref(null)
let sceneCtrl = null

// Presets
const cameraPresets = DIRECTOR_PRESETS.camera
const actionPresets = DIRECTOR_PRESETS.action

const selectedObject = ref(null)

function markDirty() { dirty.value = true }

function selectObject(id) {
  selectedObjectId.value = id
  selectedObject.value = document.objects.find(o => o.id === id) || null
}

function handleValidate() {
  const { errors, warnings } = validateDirectorDocument(document)
  validationErrors.value = errors
  validationWarnings.value = warnings
  showValidation.value = true
}

async function handleFreeze() {
  handleValidate()
  if (validationErrors.value.length > 0) {
    ElMessage.error('存在错误，无法冻结：' + validationErrors.value[0].message)
    return
  }
  if (validationWarnings.value.length > 0) {
    try {
      await ElMessageBox.confirm('存在 ' + validationWarnings.value.length + ' 个警告，确认冻结？', '冻结 Revision', { type: 'warning' })
    } catch { return }
  }
  try {
    const res = await canvasApi.freezeDirectorRevision(projectId, unitId, crypto.randomUUID())
    ElMessage.success('Revision 已冻结')
    currentRevisionId.value = res.data?.id || null
    dirty.value = false
  } catch (e) {
    ElMessage.error('冻结失败: ' + (e?.response?.data?.message || e.message))
  }
}

async function autosave() {
  if (!shouldAutosave({ dirty: dirty.value, validating: false, frozen: false })) return
  try {
    const res = await canvasApi.saveDirectorDraft(projectId, unitId, draftVersion.value, JSON.parse(JSON.stringify(document)))
    draftVersion.value = res.data?.newVersion || draftVersion.value + 1
    dirty.value = false
  } catch (e) {
    if (e?.response?.status === 409) {
      ElMessage.warning('草稿冲突：远端已被他人修改。请选择保留本地或加载远端。')
    }
  }
}

function goBack() {
  router.back()
}

function togglePlay() { playing.value = !playing.value }
function seekTo(ms) { currentTimeMs.value = Number(ms) }
function formatTime(ms) { return (ms / 1000).toFixed(1) + 's' }

function applyCameraPreset(presetId) {
  const preset = DIRECTOR_PRESETS.camera.find(p => p.id === presetId)
  if (!preset) return
  const cam = document.cameras[0]
  if (cam) {
    cam.focalLengthMm = preset.focalLengthMm
    markDirty()
  }
}

function applyActionPreset(presetId) {
  const preset = DIRECTOR_PRESETS.action.find(p => p.id === presetId)
  if (!preset || !selectedObject.value) return
  if (!selectedObject.value.actions) selectedObject.value.actions = []
  selectedObject.value.actions.push({ clipKey: preset.clipKey, inMs: currentTimeMs.value, outMs: currentTimeMs.value + 2000, weight: 1.0 })
  markDirty()
}

function objectIcon(type) {
  return { human: '♙', camera: '▰', geometry: '◇', light: '☀' }[type] || '▣'
}

// Hotkeys
const hotkeys = useDirectorHotkeys({
  setTransformMode: (m) => sceneCtrl?.setTransformMode(m),
  undo: () => { const cmd = undoStack.undo(); if (cmd) markDirty() },
  redo: () => { const cmd = undoStack.redo(); if (cmd) markDirty() },
  save: autosave,
  playPause: togglePlay,
  deselect: () => { selectedObjectId.value = null; selectedObject.value = null },
  deleteSelected: () => { /* TODO */ },
  prevFrame: () => { currentTimeMs.value = Math.max(0, currentTimeMs.value - Math.round(1000 / document.fps)) },
  nextFrame: () => { currentTimeMs.value = Math.min(document.durationMs, currentTimeMs.value + Math.round(1000 / document.fps)) },
  jumpToStart: () => { currentTimeMs.value = 0 },
  jumpToEnd: () => { currentTimeMs.value = document.durationMs }
})

// Autosave watcher
watch(dirty, (val) => {
  if (val && !autosaveTimer) {
    autosaveTimer = setTimeout(() => { autosave(); autosaveTimer = null }, 1000)
  }
})

onMounted(async () => {
  try {
    const res = await canvasApi.getDirectorDraft(projectId, unitId)
    if (res.data && res.data !== '{}') {
      Object.assign(document, JSON.parse(res.data))
    }
  } catch { /* use empty doc */ }

  if (canvasRef.value) {
    sceneCtrl = createSceneController({ canvas: canvasRef.value, document, onChange: markDirty })
    // Lazy-load Three.js at runtime
    try {
      const THREE = await import('three')
      const { OrbitControls } = await import('three/examples/jsm/controls/OrbitControls.js')
      await sceneCtrl.init(THREE, OrbitControls)
    } catch (e) {
      console.warn('Three.js 加载失败，3D 视口不可用:', e.message)
    }
  }
  hotkeys.register()
})

onUnmounted(() => {
  clearTimeout(autosaveTimer)
  hotkeys.unregister()
  sceneCtrl?.dispose()
})
</script>

<style scoped>
.director-workspace-v2 { display:flex; flex-direction:column; height:100vh; background:#0d0d0d; color:#ccc; }
.dw-topbar { display:flex; align-items:center; gap:16px; padding:8px 16px; background:#1a1a1a; border-bottom:1px solid #333; }
.dw-topbar .dw-shot-label { font-family:monospace; font-size:12px; color:#888; }
.dw-dirty { color:#e6a23c; font-size:11px; }
.dw-revision { color:#67c23a; font-size:11px; }
.dw-actions { margin-left:auto; display:flex; gap:8px; }
.dw-body { display:flex; flex:1; overflow:hidden; }
.dw-left, .dw-right { width:240px; padding:12px; overflow-y:auto; background:#111; border-color:#333; }
.dw-left { border-right:1px solid #333; }
.dw-right { border-left:1px solid #333; }
.dw-viewport { flex:1; position:relative; }
.dw-viewport canvas { width:100%; height:100%; }
.dw-panel-title { font-size:12px; font-weight:700; color:#888; margin-bottom:8px; text-transform:uppercase; }
.dw-scene-tree { margin-bottom:12px; }
.dw-tree-item { display:flex; align-items:center; gap:6px; padding:4px 8px; cursor:pointer; border-radius:4px; font-size:12px; }
.dw-tree-item:hover { background:#222; }
.dw-tree-item.selected { background:#1a3a5c; }
.dw-tree-badge { font-size:9px; color:#666; margin-left:auto; }
.dw-empty { font-size:11px; color:#555; padding:8px; }
.dw-preset-section { margin-top:16px; }
.dw-props label { display:block; font-size:11px; color:#888; margin-top:8px; }
.dw-vec3 { display:flex; gap:4px; }
.dw-timeline-bar { display:flex; align-items:center; gap:8px; padding:8px 16px; background:#1a1a1a; border-top:1px solid #333; }
.dw-scrubber { flex:1; }
.dw-validation-overlay { position:absolute; top:10%; left:10%; right:10%; background:#1a1a1a; border:1px solid #444; padding:16px; border-radius:6px; z-index:10; }
.dw-val-error { color:#f56c6c; font-size:12px; margin:4px 0; }
.dw-val-warning { color:#e6a23c; font-size:12px; margin:4px 0; }
</style>
