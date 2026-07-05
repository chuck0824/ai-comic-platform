<template>
  <div class="workspace">
    <!-- Left Rail -->
    <WorkflowRail :stages="workflow?.stages || []" :progress="workflow?.progress || 0" />

    <!-- Center: Main Content -->
    <div class="workspace-main">
      <!-- Header -->
      <div class="workspace-header">
        <div>
          <h1 class="workspace-title">{{ project?.name || '加载中…' }}</h1>
          <div class="workspace-meta">
            <span class="meta-item">
              <el-icon :size="14"><VideoCamera /></el-icon>
              {{ modeLabel(project?.creation_mode) }}
            </span>
            <span class="meta-separator">·</span>
            <span class="meta-item">
              <el-tag :type="statusTagType(project?.content_status)" size="small">{{ statusLabel(project?.content_status) }}</el-tag>
            </span>
            <span v-if="autosaveState" class="meta-item autosave-text">{{ autosaveState }}</span>
            <span v-if="generating" class="meta-item generating-badge">
              <el-icon class="is-loading"><Loading /></el-icon> AI生成中…
            </span>
          </div>
        </div>
        <div class="workspace-header-actions">
          <el-button size="small" @click="$router.push('/script-gen')">返回列表</el-button>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="workspace-loading">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p class="loading-text">正在加载项目…</p>
      </div>

      <!-- Error -->
      <div v-else-if="error" class="workspace-error">
        <div class="empty-state">
          <div class="empty-state-icon">⚠️</div>
          <div class="empty-state-title">加载失败</div>
          <div class="empty-state-desc">{{ error }}</div>
          <el-button @click="loadProject">重试</el-button>
        </div>
      </div>

      <!-- Current Stage Card -->
      <div v-else-if="currentStageInfo" class="stage-card card">
        <div class="stage-card-header">
          <div class="flex items-center gap-sm">
            <h3 class="stage-title">{{ stageLabel(currentStageInfo.key) }}</h3>
            <el-tag v-if="currentStageInfo.status === 'current'" type="primary" size="small" effect="light">当前阶段</el-tag>
            <el-tag v-else-if="currentStageInfo.status === 'completed'" type="success" size="small" effect="light">已完成</el-tag>
          </div>
        </div>
        <p v-if="currentStageInfo.primary_action" class="stage-desc">{{ currentStageInfo.primary_action }}</p>

        <!-- Generation error -->
        <el-alert v-if="genError" :title="genError" type="error" class="stage-alert" closable @close="genError=''" />

        <!-- Stage: story_seed -->
        <div v-if="currentStageInfo.key === 'story_seed'" class="stage-body">
          <p class="stage-hint">输入故事种子，AI将以此为基础生成完整剧本。</p>
          <el-input v-model="draftContent" type="textarea" :rows="6" class="stage-textarea"
                    placeholder="输入故事种子，如：林夏发现公司账本被篡改，背后牵扯到..." />
          <div class="stage-actions">
            <el-button type="primary" :loading="generating" @click="generateStage('story_seed_generate')">
              <el-icon><Cpu /></el-icon> AI 扩展故事种子
            </el-button>
            <el-button @click="saveDraft" :loading="autosaveState === '保存中…'">保存草稿</el-button>
          </div>
        </div>

        <!-- Stage: characters -->
        <div v-else-if="currentStageInfo.key === 'characters'" class="stage-body">
          <p class="stage-hint">AI将根据故事种子生成角色设定。</p>
          <div v-if="generatedContent" class="generated-content">{{ generatedContent }}</div>
          <div class="stage-actions">
            <el-button type="primary" :loading="generating" @click="generateStage('characters_generate')">
              <el-icon><Cpu /></el-icon> 生成角色设定
            </el-button>
            <el-button v-if="generatedContent" @click="acceptGenerated">采用此版本</el-button>
          </div>
        </div>

        <!-- Stage: synopsis -->
        <div v-else-if="currentStageInfo.key === 'synopsis'" class="stage-body">
          <p class="stage-hint">AI将根据故事种子和角色设定生成故事梗概。</p>
          <div v-if="generatedContent" class="generated-content">{{ generatedContent }}</div>
          <div class="stage-actions">
            <el-button type="primary" :loading="generating" @click="generateStage('synopsis_generate')">
              <el-icon><Cpu /></el-icon> 生成梗概
            </el-button>
            <el-button v-if="generatedContent" @click="acceptGenerated">采用此版本</el-button>
          </div>
        </div>

        <!-- Stage: outline -->
        <div v-else-if="currentStageInfo.key === 'outline'" class="stage-body">
          <p class="stage-hint">AI将生成分集大纲。</p>
          <div v-if="generatedContent" class="generated-content">{{ generatedContent }}</div>
          <div class="stage-actions">
            <el-button type="primary" :loading="generating" @click="generateStage('outline_generate')">
              <el-icon><Cpu /></el-icon> 生成大纲
            </el-button>
            <el-button v-if="generatedContent" @click="acceptGenerated">采用此版本</el-button>
          </div>
        </div>

        <!-- Stage: content -->
        <div v-else-if="currentStageInfo.key === 'content'" class="stage-body">
          <p class="stage-hint">AI将生成单集完整剧本。</p>
          <div v-if="generatedContent" class="generated-content">{{ generatedContent }}</div>
          <div class="stage-actions">
            <el-button type="primary" :loading="generating" @click="generateStage('content_generate')">
              <el-icon><Cpu /></el-icon> 生成正文
            </el-button>
            <el-button v-if="generatedContent" @click="acceptGenerated">采用此版本</el-button>
          </div>
        </div>

        <!-- Stage: review -->
        <div v-else-if="currentStageInfo.key === 'review'" class="stage-body">
          <p class="stage-hint">AI将从多个维度审核剧本质量。</p>
          <div v-if="generatedContent" class="generated-content">{{ generatedContent }}</div>
          <div class="stage-actions">
            <el-button type="primary" :loading="generating" @click="generateStage('review_generate')">
              <el-icon><Cpu /></el-icon> 运行审核
            </el-button>
            <el-button v-if="generatedContent" @click="acceptGenerated">确认审核结果</el-button>
          </div>
        </div>

        <!-- Stage: destination -->
        <div v-else-if="currentStageInfo.key === 'destination'" class="stage-body">
          <p class="stage-hint">内容已完成，选择下一步操作。</p>
          <div class="stage-actions">
            <el-button type="success" @click="skipStoryboard">
              <el-icon><CircleCheck /></el-icon> 完成并返回项目
            </el-button>
            <el-button v-if="project?.storyboard_intent_status !== 'requested'"
                       type="warning" @click="requestStoryboard">
              <el-icon><PictureFilled /></el-icon> 制作分镜
            </el-button>
          </div>
        </div>

        <!-- Stage: storyboard -->
        <div v-else-if="currentStageInfo.key === 'storyboard'" class="stage-body">
          <StoryboardPanel
            :master="storyboardMaster"
            :scenes="storyboardScenes"
            :shots="storyboardShots"
            :generating="storyboardGenerating"
            @generate="handleGenerateStoryboard"
            @lock="handleLockStoryboard"
          />
        </div>

        <!-- Fallback for other stages -->
        <div v-else class="stage-body">
          <p class="stage-hint">此阶段的创作工具将在后续版本上线。</p>
        </div>

        <!-- Generation progress bar -->
        <div v-if="generating" class="stage-progress">
          <el-progress :percentage="100" :indeterminate="true" :duration="2" />
          <p class="progress-text">AI正在生成内容，请稍候…</p>
        </div>
      </div>

      <!-- Generated Content History -->
      <div v-if="contentVersions.length > 0" class="version-history card">
        <h4 class="version-title">版本历史</h4>
        <div v-for="v in contentVersions.slice(0, 5)" :key="v.id" class="version-item">
          <div class="version-info">
            <span class="version-badge">v{{ v.version_no }}</span>
            <span class="version-source">{{ v.source === 'ai_generated' ? 'AI生成' : '手动' }}</span>
          </div>
          <span class="version-time">{{ formatTime(v.created_at) }}</span>
        </div>
      </div>

      <!-- Conflict Panel -->
      <div v-if="conflict" class="conflict-panel">
        <div class="conflict-header">
          <el-icon :size="18"><WarningFilled /></el-icon>
          <h4>编辑冲突</h4>
        </div>
        <p class="conflict-text">服务器版本与本地版本不一致，本地内容已保留。</p>
        <el-button size="small" @click="conflict = null">知道了</el-button>
      </div>
    </div>

    <!-- Right Panel -->
    <ContextPanel
      :versions="contextVersions"
      :locked-facts="lockedFacts"
      :impact-summary="impactSummary"
      :storyboard-intent="project?.storyboard_intent_status"
      :bible-health="bibleHealth"
      :selected-context="currentJob?.selected_context"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Cpu, Loading, VideoCamera, CircleCheck, PictureFilled, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { contentProjectApi } from '@/api/contentProject'
import { currentStage, stageLabel } from './utils/workflowPath'
import { useGeneration } from './composables/useGeneration'
import WorkflowRail from './components/WorkflowRail.vue'
import ContextPanel from './components/ContextPanel.vue'
import StoryboardPanel from './components/StoryboardPanel.vue'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => route.params.projectId)

const project = ref(null)
const workflow = ref(null)
const units = ref([])
const loading = ref(true)
const error = ref('')
const autosaveState = ref('')
const conflict = ref(null)
const draftContent = ref('')
const generatedContent = ref('')
const contentVersions = ref([])
const contextVersions = ref([])
const lockedFacts = ref([])
const impactSummary = ref('')
const bibleHealth = ref(null)
const bibleLoading = ref(false)

let currentUnitId = null
let autosaveTimer = null

// Storyboard state
const storyboardMaster = ref(null)
const storyboardScenes = ref([])
const storyboardShots = ref([])
const storyboardGenerating = ref(false)

const { generating, genError, currentJob, triggerGeneration, cancelGeneration } = useGeneration(projectId)

// Load current unit when units are loaded
watchEffect(() => {
  if (units.value.length > 0 && currentStageInfo.value) {
    const stageKey = currentStageInfo.value.key
    const matchedUnit = units.value.find(u => u.unit_type === stageKey || u.title?.includes(stageLabel(stageKey)))
    if (matchedUnit) {
      currentUnitId = matchedUnit.id
      loadDraftForUnit(matchedUnit.id)
      loadVersionsForUnit(matchedUnit.id)
    }
  }
})

onMounted(() => loadProject())
onBeforeUnmount(() => {
  if (autosaveTimer) clearTimeout(autosaveTimer)
})

async function loadProject() {
  loading.value = true
  error.value = ''
  try {
    const [projectRes, workflowRes, unitsRes] = await Promise.all([
      contentProjectApi.get(projectId.value),
      contentProjectApi.workflow(projectId.value),
      contentProjectApi.listUnits(projectId.value)
    ])
    project.value = projectRes.data
    workflow.value = workflowRes.data
    units.value = unitsRes.data?.items || unitsRes.data || []

    // load bible health (non-blocking)
    loadBibleHealth()

    // load draft for last content unit
    if (project.value.last_content_unit_id) {
      currentUnitId = project.value.last_content_unit_id
      await loadDraftForUnit(currentUnitId)
      await loadVersionsForUnit(currentUnitId)
    }
    // load storyboard if available
    await loadStoryboard()
  } catch (e) {
    error.value = e.response?.data?.message || '加载项目失败'
  } finally {
    loading.value = false
  }
}

async function loadBibleHealth() {
  bibleLoading.value = true
  try {
    const res = await contentProjectApi.getCreativeBibleHealth(projectId.value)
    bibleHealth.value = res.data ?? res
  } catch (e) {
    bibleHealth.value = { status: 'error', ready_for_generation: false, message: '圣经状态获取失败' }
  } finally {
    bibleLoading.value = false
  }
}

async function loadDraftForUnit(unitId) {
  try {
    const res = await contentProjectApi.getDraft(unitId)
    const draft = res.data
    draftContent.value = draft?.plain_text || ''
    generatedContent.value = draft?.source === 'ai_generated' ? (draft?.plain_text || '') : ''
  } catch (e) { /* no draft yet */ }
}

async function loadVersionsForUnit(unitId) {
  try {
    const res = await contentProjectApi.listVersions(unitId)
    contentVersions.value = res.data || []
  } catch (e) { contentVersions.value = [] }
}

const currentStageInfo = computed(() => currentStage(workflow.value?.stages || []))

// M1: Trigger AI generation for current stage
async function generateStage(jobType) {
  // Bible readiness gate — block generation if bible not confirmed
  if (bibleHealth.value && !bibleHealth.value.ready_for_generation) {
    ElMessage.warning('创作圣经尚未确认，请先确认生态或实体设定')
    router.push(`/script-gen/${projectId.value}/edit/bible-overview`)
    return
  }

  if (!currentUnitId) {
    // create a content unit for this stage
    try {
      const stageKey = currentStageInfo.value.key
      const res = await contentProjectApi.createUnit(projectId.value, {
        unit_type: stageKey,
        display_no: units.value.length + 1,
        title: stageLabel(stageKey)
      })
      currentUnitId = res.data.id
      // reload units
      const unitsRes = await contentProjectApi.listUnits(projectId.value)
      units.value = unitsRes.data?.items || unitsRes.data || []
    } catch (e) {
      genError.value = '创建内容单元失败: ' + (e.response?.data?.message || e.message)
      return
    }
  }

  // Build selected versions from context
  const selectedVersions = {}
  if (project.value?.current_parameter_version_id) {
    selectedVersions.parameter = project.value.current_parameter_version_id
  }
  // Add existing content units' current versions
  for (const u of units.value) {
    if (u.current_version_id && u.id !== currentUnitId) {
      selectedVersions[u.unit_type] = u.current_version_id
    }
  }

  const job = await triggerGeneration(jobType, 'content_unit', currentUnitId, selectedVersions, '')
  if (job) {
    // Poll until complete, then reload draft
    const pollInterval = setInterval(async () => {
      if (!generating.value) {
        clearInterval(pollInterval)
        await loadDraftForUnit(currentUnitId)
        await loadVersionsForUnit(currentUnitId)
        // Reload workflow
        try {
          const wfRes = await contentProjectApi.workflow(projectId.value)
          workflow.value = wfRes.data
        } catch (e) { /* ignore */ }
      }
    }, 1000)
  }
}

async function acceptGenerated() {
  if (!currentUnitId) return
  try {
    await contentProjectApi.createVersion(currentUnitId, { status: 'approved' })
    ElMessage.success('版本已确认')
    await loadVersionsForUnit(currentUnitId)
    // Advance workflow
    await saveResumePosition()
    await loadProject()
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.response?.data?.message || e.message))
  }
}

async function saveResumePosition() {
  const stage = currentStageInfo.value
  if (!stage || !project.value) return
  try {
    const stages = workflow.value?.stages || []
    const idx = stages.findIndex(s => s.key === stage.key)
    const nextStage = idx >= 0 && idx < stages.length - 1 ? stages[idx + 1] : null
    if (nextStage && nextStage.status !== 'skipped') {
      await contentProjectApi.saveResume(projectId.value, {
        stage_key: nextStage.key,
        task_key: nextStage.primary_action || nextStage.key,
        content_unit_id: currentUnitId,
        revision: project.value.revision
      })
    }
  } catch (e) { /* non-critical */ }
}

// Autosave
function onDraftChange() {
  if (autosaveTimer) clearTimeout(autosaveTimer)
  autosaveTimer = setTimeout(() => saveDraft(), 2000)
}

async function saveDraft() {
  if (!currentUnitId) return
  autosaveState.value = '保存中…'
  try {
    const res = await contentProjectApi.saveDraft(currentUnitId, {
      revision: project.value?.revision || 0,
      content_json: JSON.stringify({ blocks: [draftContent.value || generatedContent.value] }),
      plain_text: draftContent.value || generatedContent.value
    })
    if (project.value) project.value.revision = res.data?.revision
    autosaveState.value = '已保存'
    setTimeout(() => { autosaveState.value = '' }, 2000)
  } catch (e) {
    if (e.response?.status === 409) {
      conflict.value = { serverRevision: '?', localRevision: project.value?.revision }
      autosaveState.value = '冲突'
    } else {
      autosaveState.value = '保存失败'
    }
  }
}

// Keyboard save
function onKeyDown(e) {
  if ((e.metaKey || e.ctrlKey) && e.key === 's') {
    e.preventDefault()
    saveDraft()
  }
}
if (typeof window !== 'undefined') {
  window.addEventListener('keydown', onKeyDown)
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeyDown))
}

async function skipStoryboard() {
  try {
    await contentProjectApi.setStoryboardIntent(projectId.value, 'skipped')
    ElMessage.success('项目已完成')
    await loadProject()
  } catch (e) {
    error.value = '操作失败: ' + (e.response?.data?.message || e.message)
  }
}

async function requestStoryboard() {
  try {
    await contentProjectApi.setStoryboardIntent(projectId.value, 'requested')
    ElMessage.success('分镜制作已请求')
    await loadProject()
  } catch (e) {
    error.value = '操作失败: ' + (e.response?.data?.message || e.message)
  }
}

// ===== Storyboard handlers =====

async function loadStoryboard() {
  try {
    const res = await contentProjectApi.listStoryboardMasters(projectId.value)
    const masters = res.data || []
    if (masters.length > 0) {
      storyboardMaster.value = masters[0]
      const [scenesRes, shotsRes] = await Promise.all([
        contentProjectApi.listStoryboardScenes(projectId.value, storyboardMaster.value.id),
        contentProjectApi.listStoryboardShots(projectId.value, storyboardMaster.value.id)
      ])
      storyboardScenes.value = scenesRes.data || []
      storyboardShots.value = shotsRes.data || []
    }
  } catch (e) { /* no storyboard yet */ }
}

async function handleGenerateStoryboard() {
  storyboardGenerating.value = true
  try {
    const contentUnitId = currentUnitId || units.value.find(u => u.unit_type === 'content')?.id
    if (!contentUnitId) {
      ElMessage.warning('请先生成正文内容')
      storyboardGenerating.value = false
      return
    }
    await contentProjectApi.generateStoryboard(projectId.value, contentUnitId)
    ElMessage.success('分镜生成成功')
    await loadStoryboard()
  } catch (e) {
    ElMessage.error('分镜生成失败: ' + (e.response?.data?.message || e.message))
  } finally {
    storyboardGenerating.value = false
  }
}

async function handleLockStoryboard(masterId) {
  try {
    await contentProjectApi.lockStoryboardMaster(projectId.value, masterId)
    ElMessage.success('分镜已锁定')
    await loadStoryboard()
  } catch (e) {
    ElMessage.error('锁定失败: ' + (e.response?.data?.message || e.message))
  }
}

function modeLabel(m) {
  return { short_drama: '短剧', long_form: '长篇', tvc: 'TVC' }[m] || m
}

function statusLabel(s) {
  return { draft: '草稿', reviewing: '审核中', approved: '已通过', needs_revision: '需修改', locked: '已锁定' }[s] || s
}

function statusTagType(s) {
  return { draft: 'info', reviewing: 'warning', approved: 'success', needs_revision: 'danger', locked: '' }[s] || 'info'
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
/* ===== Workspace Layout ===== */
.workspace {
  display: flex;
  height: calc(100vh - var(--topbar-h));
}

.workspace-main {
  flex: 1;
  padding: 20px 24px;
  overflow-y: auto;
  min-width: 0;
}

/* ===== Header ===== */
.workspace-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}
.workspace-title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -.01em;
  margin: 0 0 6px 0;
}
.workspace-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--text-secondary);
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.meta-separator {
  color: var(--text-tertiary);
}
.autosave-text {
  color: var(--text-tertiary);
  font-size: 12px;
}
.generating-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 100px;
  background: var(--warning-bg);
  color: var(--warning);
  font-size: 12px;
  font-weight: 600;
}
.workspace-header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ===== Loading & Error ===== */
.workspace-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 64px 0;
  color: var(--text-secondary);
}
.loading-text {
  font-size: 14px;
}
.workspace-error {
  padding: 48px 0;
}

/* ===== Stage Card ===== */
.stage-card {
  margin-bottom: 20px;
}
.stage-card-header {
  margin-bottom: 8px;
}
.stage-title {
  font-size: 17px;
  font-weight: 600;
  margin: 0;
}
.stage-desc {
  color: var(--text-secondary);
  font-size: 14px;
  margin: 0 0 16px 0;
  line-height: 1.5;
}
.stage-alert {
  margin-bottom: 16px;
}
.stage-body {
  /* content area within stage */
}
.stage-hint {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 12px 0;
  line-height: 1.5;
}
.stage-textarea {
  margin-bottom: 16px;
}
.stage-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
}

/* ===== Generated Content ===== */
.generated-content {
  background: var(--bg-app);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
  margin-bottom: 16px;
  white-space: pre-wrap;
  max-height: 400px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
}

/* ===== Stage Progress ===== */
.stage-progress {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}
.progress-text {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 6px;
}

/* ===== Version History ===== */
.version-history {
  margin-bottom: 20px;
}
.version-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 10px 0;
}
.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-light);
  font-size: 13px;
}
.version-item:last-child {
  border-bottom: none;
}
.version-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.version-badge {
  font-weight: 600;
  color: var(--accent);
}
.version-source {
  color: var(--text-secondary);
}
.version-time {
  color: var(--text-tertiary);
  font-size: 12px;
}

/* ===== Conflict Panel ===== */
.conflict-panel {
  background: var(--warning-bg);
  border: 1px solid var(--warning);
  border-radius: var(--radius-md);
  padding: 16px;
  margin-bottom: 20px;
}
.conflict-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--warning);
  margin-bottom: 8px;
}
.conflict-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}
.conflict-text {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 12px 0;
  line-height: 1.5;
}

/* ===== Responsive ===== */
@media (max-width: 1024px) {
  .workspace-main {
    padding: 16px;
  }
}

@media (max-width: 768px) {
  .workspace {
    flex-direction: column;
    height: auto;
  }
  .workspace-main {
    padding: 12px;
  }
  .workspace-header {
    flex-direction: column;
    gap: 12px;
  }
  .stage-actions {
    flex-direction: column;
  }
}
</style>
