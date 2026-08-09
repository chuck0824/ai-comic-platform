<template>
  <section class="stage-panel">
    <header class="stage-heading">
      <div><p class="eyebrow">STEP 1</p><h2>创作设置</h2><p>先确定创作边界、产物规格和积分规则。</p></div>
      <el-tag :type="modelMode === 'remote' ? 'success' : 'warning'">{{ modelMode === 'remote' ? '3001 模型已连接' : '演示模式' }}</el-tag>
    </header>

    <el-form label-position="top" class="settings-grid">
      <el-form-item label="创作类型" data-action="creation-type">
        <el-radio-group v-model="draft.creationType" @change="notifyChange">
          <el-radio-button value="novel_adaptation">小说改编</el-radio-button>
          <el-radio-button value="original">原创剧本</el-radio-button>
          <el-radio-button value="outline_expansion">大纲扩写</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="题材 / 分类" data-action="genre-selector">
        <el-select v-model="draft.genre" filterable allow-create placeholder="选择或输入题材" @change="notifyChange">
          <el-option v-for="genre in genres" :key="genre" :label="genre" :value="genre" />
        </el-select>
      </el-form-item>
      <el-form-item label="故事基调">
        <el-select v-model="draft.tone" placeholder="选择基调" @change="notifyChange">
          <el-option v-for="tone in tones" :key="tone" :label="tone" :value="tone" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标受众"><el-input v-model="draft.audience" placeholder="例：18–35 岁女性" @input="notifyChange" /></el-form-item>
      <el-form-item label="总集数"><el-input-number v-model="draft.episodeCount" :min="1" :max="500" @change="notifyChange" /></el-form-item>
      <el-form-item label="单集时长（秒）"><el-input-number v-model="draft.episodeDuration" :min="15" :max="3600" @change="notifyChange" /></el-form-item>
      <el-form-item label="改编强度">
        <el-select v-model="draft.adaptationStrength" @change="notifyChange">
          <el-option label="忠实原作" value="faithful" /><el-option label="平衡改编" value="balanced" /><el-option label="强剧情改编" value="dramatic" />
        </el-select>
      </el-form-item>
      <el-form-item label="输出格式">
        <el-select v-model="draft.outputFormat" @change="notifyChange">
          <el-option label="竖屏短剧" value="vertical_short_drama" /><el-option label="横屏漫剧" value="horizontal_comic_drama" /><el-option label="标准文字剧本" value="standard_script" />
        </el-select>
      </el-form-item>
    </el-form>

    <div class="model-card" data-action="model-selector">
      <div class="model-title"><strong>创作模型</strong><el-button text @click="refreshModels">刷新模型</el-button></div>
      <el-select v-model="selectedModelId" placeholder="选择模型" :loading="modelLoading" @change="selectModel">
        <el-option v-for="model in models" :key="model.id" :label="model.name" :value="model.id">
          <span>{{ model.name }}</span><el-tag class="source-badge" size="small" :type="model.demo ? 'warning' : 'success'">{{ model.sourceBadge }}</el-tag>
        </el-option>
      </el-select>
      <div v-if="draft.model" class="model-meta">
        <el-tag :type="draft.model.demo ? 'warning' : 'success'">{{ draft.model.sourceBadge }}</el-tag>
        <span>{{ draft.model.pointRule }}</span>
        <strong>{{ draft.model.demo ? '0 积分' : (draft.estimatedPoints ? `预估 ${draft.estimatedPoints} 积分` : '待获取积分预估') }}</strong>
      </div>
    </div>

    <el-alert v-if="localGuidance" :title="localGuidance.title" :description="localGuidance.message" type="warning" show-icon :closable="false" />
    <footer class="stage-actions"><el-button type="primary" @click="saveSettings">保存创作设置</el-button></footer>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { canvasAgentApi } from '@/api/canvas'
import { generationApi } from '@/api/generation'
import { loadCreationModels, validateCreationSettings } from '../workbench/upstreamStageModel.js'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  fetchModels: { type: Function, default: null },
  estimatePoints: { type: Function, default: null },
  persistSettings: { type: Function, default: null }
})
const emit = defineEmits(['update:modelValue', 'guidance', 'saved', 'models-loaded'])
const defaults = { creationType: 'novel_adaptation', genre: '', tone: '', audience: '', episodeCount: 24, episodeDuration: 90, adaptationStrength: 'balanced', outputFormat: 'vertical_short_drama', model: null, estimatedPoints: null }
const draft = reactive({ ...defaults, ...props.modelValue })
const genres = ['都市', '悬疑', '逆袭', '古风', '幻想', '科幻', '爱情', '喜剧', '家庭', '青春']
const tones = ['高燃', '紧张', '治愈', '轻喜', '暗黑', '浪漫', '写实']
const models = ref([])
const selectedModelId = ref(draft.model?.id ?? '')
const modelMode = ref('loading')
const modelLoading = ref(false)
const localGuidance = ref(null)

watch(() => props.modelValue, value => Object.assign(draft, defaults, value || {}), { deep: true })

function notifyChange() { emit('update:modelValue', { ...draft }) }
function showGuidance(value) { localGuidance.value = value; emit('guidance', value); return value }

async function refreshModels() {
  modelLoading.value = true
  const loader = props.fetchModels || (() => canvasAgentApi.getModels({ node_type: 'text', agent_type: 'text_agent' }))
  const catalog = await loadCreationModels(loader)
  models.value = catalog.models
  modelMode.value = catalog.mode
  localGuidance.value = catalog.guidance
  if (catalog.guidance) emit('guidance', catalog.guidance)
  if (!models.value.some(model => model.id === selectedModelId.value)) {
    selectedModelId.value = models.value[0]?.id ?? ''
    if (selectedModelId.value) await selectModel(selectedModelId.value)
  }
  emit('models-loaded', catalog)
  modelLoading.value = false
}

async function selectModel(modelId) {
  const model = models.value.find(item => item.id === modelId) || null
  draft.model = model
  draft.estimatedPoints = model?.demo ? 0 : model?.estimatedPoints
  if (model && !model.demo && !draft.estimatedPoints) {
    try {
      const estimate = props.estimatePoints
        ? await props.estimatePoints({ modelId: model.id, operation: 'script_workbench' })
        : await generationApi.estimateCredits({ model_id: model.id, operation: 'script_workbench' })
      const payload = estimate?.data?.data ?? estimate?.data ?? estimate ?? {}
      const points = Number(payload.estimated_points ?? payload.estimatedCredits ?? payload.credits)
      draft.estimatedPoints = Number.isFinite(points) && points > 0 ? points : null
      if (!draft.estimatedPoints) showGuidance({ allowed: false, code: 'POINT_ESTIMATE_REQUIRED', title: '积分预估不可用', message: '未获取到有效积分预估，请刷新后重试。', targetAction: 'refresh_model_catalog' })
    } catch (error) {
      draft.estimatedPoints = null
      showGuidance({ allowed: false, code: 'POINT_ESTIMATE_FAILED', title: '积分预估失败', message: error?.message || '请刷新后重试。', targetAction: 'refresh_model_catalog' })
    }
  }
  notifyChange()
}

async function saveSettings() {
  const validation = validateCreationSettings(draft)
  if (!validation.allowed) return showGuidance(validation)
  if (!props.persistSettings) return showGuidance({ allowed: false, code: 'SETTINGS_PERSISTENCE_REQUIRED', title: '创作设置未保存', message: '保存服务尚未连接，请稍后重试。', targetAction: 'retry_settings_save' })
  try {
    const response = await props.persistSettings({ ...draft })
    if (response?.persisted !== true) return showGuidance({ allowed: false, code: 'SETTINGS_PERSISTENCE_FAILED', title: '创作设置未保存', message: response?.message || '请稍后重试。', targetAction: 'retry_settings_save' })
    localGuidance.value = null
    emit('saved', response)
    return response
  } catch (error) {
    return showGuidance({ allowed: false, code: 'SETTINGS_PERSISTENCE_FAILED', title: '创作设置未保存', message: error?.message || '请稍后重试。', targetAction: 'retry_settings_save' })
  }
}

onMounted(refreshModels)
</script>

<style scoped>
.stage-panel { display:grid; gap:20px; }.stage-heading,.model-title,.stage-actions { display:flex; justify-content:space-between; align-items:flex-start; gap:16px }.eyebrow { color:var(--el-color-primary); font-weight:700; margin:0 }.stage-heading h2 { margin:4px 0 }.stage-heading p { color:var(--el-text-color-secondary) }.settings-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0 20px }.model-card { border:1px solid var(--el-border-color); border-radius:12px; padding:16px; display:grid; gap:12px }.model-meta { display:flex; align-items:center; gap:12px; color:var(--el-text-color-secondary); flex-wrap:wrap }.source-badge { float:right; margin-left:16px }.stage-actions { justify-content:flex-end }@media(max-width:760px){.settings-grid{grid-template-columns:1fr}}
</style>
