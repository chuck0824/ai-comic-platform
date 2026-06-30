<template>
  <div class="tag-editor-page">
    <div class="page-header">
      <el-button size="small" @click="$router.push('/warehouse')">
        <el-icon><ArrowLeft /></el-icon> 返回仓库
      </el-button>
      <div class="page-heading">
        <h2 class="text-xl font-bold">作品编辑 — {{ editorData?.title || '加载中…' }}</h2>
        <span class="text-sm text-muted">
          总字数 {{ formatNumber(editorData?.totalWords) }}
          <template v-if="isLegacy"> · 旧版兼容模式</template>
        </span>
      </div>
    </div>

    <div class="editor-layout">
      <WorkInfoNav
        :active-section="activeSection"
        :setting-counts="editorData?.settingCounts ?? {}"
        @select="selectSection"
      />

      <main class="card editor-content" v-loading="loading">
        <!-- Error -->
        <el-alert v-if="error" class="mb-lg" type="warning" :title="error" :closable="true" @close="clearError" show-icon />

        <!-- 403 / 404 -->
        <el-result v-if="loadError" icon="warning" :title="loadError">
          <template #extra>
            <el-button type="primary" @click="init">重试</el-button>
            <el-button @click="$router.push('/warehouse')">返回仓库</el-button>
          </template>
        </el-result>

        <template v-if="!loadError && editorData">
          <!-- Tags -->
          <TagPanel
            v-if="activeSection === 'tags'"
            :genre="currentGenre" :plots="currentPlots" :tones="currentTones" :setting="currentSetting"
            :dictionary="dictionary" :save-status="saveStatus.tags"
            @update:genre="onGenre" @update:plots="onPlots" @update:tones="onTones" @update:setting="onSetting"
            @clear="confirmClearTags" @save="manualSaveTags"
          />

          <!-- Synopsis -->
          <TextProfilePanel
            v-else-if="activeSection === 'synopsis'"
            type="synopsis"
            :model-value="currentSynopsis"
            :saving="saveStatus.profile === 'saving'"
            :save-status="saveStatus.profile"
            :is-dirty="isDirty('synopsis')"
            :read-only="isReadOnly"
            @update:model-value="onSynopsis"
            @save="saveSynopsisNow"
          />

          <!-- Outline -->
          <TextProfilePanel
            v-else-if="activeSection === 'outline'"
            type="outline"
            :model-value="currentOutline"
            :saving="saveStatus.profile === 'saving'"
            :save-status="saveStatus.profile"
            :is-dirty="isDirty('outline')"
            :read-only="isReadOnly"
            @update:model-value="onOutline"
            @save="saveOutlineNow"
          />

          <!-- Setting panels -->
          <SettingPanel
            v-else-if="isSettingSection"
            :project-id="editorData?.projectId"
            :setting-type="activeSection"
            :read-only="isReadOnly"
          />

          <!-- Reading pane -->
          <div v-else class="placeholder">
            <el-empty description="选择左侧导航开始编辑" />
          </div>
        </template>
      </main>
    </div>

    <!-- Extraction Review Drawer -->
    <ExtractionReviewDrawer
      v-model="showExtractionDrawer"
      :project-id="editorData?.projectId"
      :batch-id="extractionBatchId"
      @applied="onExtractionApplied"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useWorkEditor } from './work-editor/useWorkEditor'
import WorkInfoNav from './work-editor/WorkInfoNav.vue'
import TagPanel from './work-editor/TagPanel.vue'
import TextProfilePanel from './work-editor/TextProfilePanel.vue'
import SettingPanel from './work-editor/SettingPanel.vue'
import ExtractionReviewDrawer from './work-editor/ExtractionReviewDrawer.vue'

const route = useRoute()
const router = useRouter()

const {
  loading, error, editorData, dirtyFlags, saveStatus, dictionary, isReadOnly,
  loadEditor, loadDictionary, saveTags, saveProfile, clearError, markDirty, isDirty
} = useWorkEditor()

const activeSection = ref('tags')
const loadError = ref('')
const isLegacy = ref(false)

// Local tag state
const currentGenre = ref('')
const currentPlots = ref([])
const currentTones = ref([])
const currentSetting = ref('')
const currentSynopsis = ref('')
const currentOutline = ref('')
let autoSaveTimer = null

const SETTING_SECTIONS = ['character', 'background', 'faction', 'location', 'item']
const isSettingSection = computed(() => SETTING_SECTIONS.includes(activeSection.value))

const showExtractionDrawer = ref(false)
const extractionBatchId = ref(null)

// ---- Init ----

onMounted(init)

async function init() {
  loadError.value = ''
  const scriptId = route.params.scriptId
  const projectId = route.params.projectId

  await loadDictionary()

  try {
    if (scriptId) {
      isLegacy.value = true
      await loadEditor(scriptId, true)
    } else if (projectId) {
      isLegacy.value = false
      await loadEditor(projectId, false)
    } else {
      loadError.value = '缺少作品 ID'
      return
    }

    // Sync local state from loaded data
    if (editorData.value?.profile) {
      const p = editorData.value.profile
      currentGenre.value = p.genreTag ?? ''
      currentPlots.value = Array.isArray(p.plotTags) ? [...p.plotTags] : []
      currentTones.value = Array.isArray(p.toneTags) ? [...p.toneTags] : []
      currentSetting.value = p.settingTag ?? ''
      currentSynopsis.value = p.synopsis ?? ''
      currentOutline.value = p.outline ?? ''
    }

    // Route-controlled section
    const section = route.params.section
    if (section) activeSection.value = section
  } catch (e) {
    loadError.value = e?.response?.data?.message || e.message || '加载失败'
  }
}

// ---- Navigation ----

function selectSection(key) {
  activeSection.value = key
  if (route.params.projectId) {
    router.replace({ params: { ...route.params, section: key } })
  }
}

// ---- Tags ----

function onGenre(v) { currentGenre.value = v; scheduleTagSave() }
function onPlots(v) { currentPlots.value = v; scheduleTagSave() }
function onTones(v) { currentTones.value = v; scheduleTagSave() }
function onSetting(v) { currentSetting.value = v; scheduleTagSave() }

function scheduleTagSave() {
  clearTimeout(autoSaveTimer)
  markDirty('tags')
  autoSaveTimer = setTimeout(() => saveTagsNow(), 800)
}

async function saveTagsNow() {
  try {
    await saveTags({
      genre: currentGenre.value,
      plot: currentPlots.value,
      tone: currentTones.value,
      setting: currentSetting.value
    })
  } catch { /* status already set by composable */ }
}

async function manualSaveTags() {
  try {
    await saveTagsNow()
    ElMessage.success('标签已保存')
  } catch { /* error shown in UI */ }
}

async function confirmClearTags() {
  const hasSelection = currentGenre.value || currentPlots.value.length || currentTones.value.length || currentSetting.value
  if (!hasSelection) return
  try {
    await ElMessageBox.confirm('清空后全部 4 轴标签将被移除。', '确认清空？', { type: 'warning' })
    currentGenre.value = ''
    currentPlots.value = []
    currentTones.value = []
    currentSetting.value = ''
  } catch { /* cancelled */ }
}

// ---- Synopsis / Outline ----

function onSynopsis(v) { currentSynopsis.value = v; markDirty('synopsis') }
function onOutline(v) { currentOutline.value = v; markDirty('outline') }

async function saveSynopsisNow() {
  try { await saveProfile({ synopsis: currentSynopsis.value, outline: currentOutline.value }) }
  catch {}
}

async function saveOutlineNow() {
  try { await saveProfile({ synopsis: currentSynopsis.value, outline: currentOutline.value }) }
  catch {}
}

// ---- Extraction ----

function onExtractionApplied() {
  ElMessage.success('设定已从 AI 提取结果更新')
}

// ---- Helpers ----

function formatNumber(n) { return Number(n || 0).toLocaleString('zh-CN') }

// ---- Leave protection ----

onBeforeRouteLeave((to, from, next) => {
  const hasDirty = Object.values(dirtyFlags).some(Boolean)
  if (hasDirty) {
    ElMessageBox.confirm('有未保存的修改，确定离开吗？', '未保存修改', {
      confirmButtonText: '离开', cancelButtonText: '留下', type: 'warning'
    }).then(() => next()).catch(() => next(false))
  } else {
    next()
  }
})

onBeforeUnmount(() => clearTimeout(autoSaveTimer))
</script>

<style scoped>
.tag-editor-page { min-width: 0; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
.page-heading { display: flex; align-items: baseline; gap: 12px; min-width: 0; }
.page-heading h2 { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.editor-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 24px; align-items: start; }
.editor-content { min-height: 580px; padding: 24px; }
.placeholder { display: flex; align-items: center; justify-content: center; min-height: 400px; }
.mb-lg { margin-bottom: 20px; }

@media (max-width: 860px) {
  .editor-layout { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .page-header, .editor-content { padding: 18px; }
}
</style>
