<template>
  <div class="workspace" style="display:flex;height:calc(100vh - 60px)">
    <!-- Left Rail -->
    <WorkflowRail :stages="workflow?.stages || []" :progress="workflow?.progress || 0" />

    <!-- Center: Main Content -->
    <div class="flex-1 flex flex-col" style="padding:16px;overflow-y:auto">
      <!-- Header -->
      <div class="flex items-center justify-between mb-md">
        <div>
          <h1 class="text-lg font-bold">{{ project?.name || '加载中…' }}</h1>
          <div class="text-xs text-muted mt-xs">
            <span>模式：{{ modeLabel(project?.creation_mode) }}</span>
            <span class="ml-sm">状态：{{ statusLabel(project?.content_status) }}</span>
            <span v-if="autosaveState" class="ml-sm">{{ autosaveState }}</span>
            <span v-if="generating" class="ml-sm badge badge-warning">
              <el-icon class="is-loading"><Loading /></el-icon> AI生成中…
            </span>
          </div>
        </div>
        <div class="flex gap-sm">
          <el-button size="small" @click="$router.push('/content-projects')">返回列表</el-button>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="flex justify-center py-xl">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      </div>

      <!-- Error -->
      <div v-else-if="error" class="text-center py-xl">
        <p class="text-muted mb-md">{{ error }}</p>
        <el-button @click="loadProject">重试</el-button>
      </div>

      <!-- Current Stage Card -->
      <div v-else-if="currentStageInfo" class="card p-lg mb-md">
        <div class="flex items-center justify-between mb-sm">
          <h3 class="font-semibold">{{ stageLabel(currentStageInfo.key) }}</h3>
          <el-tag v-if="currentStageInfo.status === 'current'" type="primary" size="small">当前阶段</el-tag>
        </div>
        <p v-if="currentStageInfo.primary_action" class="text-muted mb-md">
          {{ currentStageInfo.primary_action }}
        </p>

        <!-- Generation error -->
        <el-alert v-if="genError" :title="genError" type="error" class="mb-md" closable @close="genError=''" />

        <!-- Stage: story_seed -->
        <div v-if="currentStageInfo.key === 'story_seed'">
          <p class="text-sm text-muted mb-sm">输入故事种子，AI将以此为基础生成完整剧本。</p>
          <el-input v-model="draftContent" type="textarea" :rows="6" class="mb-md"
                    placeholder="输入故事种子，如：林夏发现公司账本被篡改，背后牵扯到..." />
          <div class="flex gap-md">
            <el-button type="primary" :loading="generating" @click="generateStage('story_seed_generate')">
              <el-icon><Cpu /></el-icon> AI 扩展故事种子
            </el-button>
            <el-button @click="saveDraft" :loading="autosaveState === '保存中…'">保存草稿</el-button>
          </div>
        </div>

        <!-- Stage: characters -->
        <div v-else-if="currentStageInfo.key === 'characters'">
          <p class="text-sm text-muted mb-sm">AI将根据故事种子生成角色设定。</p>
          <div v-if="generatedContent" class="card p-md mb-md" style="background:#fafafa;white-space:pre-wrap;max-height:400px;overflow-y:auto">{{ generatedContent }}</div>
          <div class="flex gap-md">
            <el-button type="primary" :loading="generating" @click="generateStage('characters_generate')">
              <el-icon><Cpu /></el-icon> 生成角色设定
            </el-button>
            <el-button v-if="generatedContent" @click="acceptGenerated">采用此版本</el-button>
          </div>
        </div>

        <!-- Stage: synopsis -->
        <div v-else-if="currentStageInfo.key === 'synopsis'">
          <p class="text-sm text-muted mb-sm">AI将根据故事种子和角色设定生成故事梗概。</p>
          <div v-if="generatedContent" class="card p-md mb-md" style="background:#fafafa;white-space:pre-wrap;max-height:400px;overflow-y:auto">{{ generatedContent }}</div>
          <div class="flex gap-md">
            <el-button type="primary" :loading="generating" @click="generateStage('synopsis_generate')">
              <el-icon><Cpu /></el-icon> 生成梗概
            </el-button>
            <el-button v-if="generatedContent" @click="acceptGenerated">采用此版本</el-button>
          </div>
        </div>

        <!-- Stage: outline -->
        <div v-else-if="currentStageInfo.key === 'outline'">
          <p class="text-sm text-muted mb-sm">AI将生成分集大纲。</p>
          <div v-if="generatedContent" class="card p-md mb-md" style="background:#fafafa;white-space:pre-wrap;max-height:400px;overflow-y:auto">{{ generatedContent }}</div>
          <div class="flex gap-md">
            <el-button type="primary" :loading="generating" @click="generateStage('outline_generate')">
              <el-icon><Cpu /></el-icon> 生成大纲
            </el-button>
            <el-button v-if="generatedContent" @click="acceptGenerated">采用此版本</el-button>
          </div>
        </div>

        <!-- Stage: content -->
        <div v-else-if="currentStageInfo.key === 'content'">
          <p class="text-sm text-muted mb-sm">AI将生成单集完整剧本。</p>
          <div v-if="generatedContent" class="card p-md mb-md" style="background:#fafafa;white-space:pre-wrap;max-height:400px;overflow-y:auto">{{ generatedContent }}</div>
          <div class="flex gap-md">
            <el-button type="primary" :loading="generating" @click="generateStage('content_generate')">
              <el-icon><Cpu /></el-icon> 生成正文
            </el-button>
            <el-button v-if="generatedContent" @click="acceptGenerated">采用此版本</el-button>
          </div>
        </div>

        <!-- Stage: review -->
        <div v-else-if="currentStageInfo.key === 'review'">
          <p class="text-sm text-muted mb-sm">AI将从多个维度审核剧本质量。</p>
          <div v-if="generatedContent" class="card p-md mb-md" style="background:#fafafa;white-space:pre-wrap;max-height:400px;overflow-y:auto">{{ generatedContent }}</div>
          <div class="flex gap-md">
            <el-button type="primary" :loading="generating" @click="generateStage('review_generate')">
              <el-icon><Cpu /></el-icon> 运行审核
            </el-button>
            <el-button v-if="generatedContent" @click="acceptGenerated">确认审核结果</el-button>
          </div>
        </div>

        <!-- Stage: destination -->
        <div v-else-if="currentStageInfo.key === 'destination'">
          <p class="text-sm text-muted mb-sm">内容已完成，选择下一步操作。</p>
          <div class="flex gap-md">
            <el-button type="success" @click="skipStoryboard">完成并返回项目</el-button>
            <el-button v-if="project?.storyboard_intent_status !== 'requested'"
                       type="warning" @click="requestStoryboard">制作分镜</el-button>
          </div>
        </div>

        <!-- Fallback for other stages -->
        <div v-else>
          <p class="text-sm text-muted">此阶段的创作工具将在后续版本上线。</p>
        </div>

        <!-- Generation progress bar -->
        <div v-if="generating" class="mt-md">
          <el-progress :percentage="100" :indeterminate="true" :duration="2" />
          <p class="text-xs text-muted mt-xs">AI正在生成内容，请稍候…</p>
        </div>
      </div>

      <!-- Generated Content History -->
      <div v-if="contentVersions.length > 0" class="card p-md mb-md">
        <h4 class="text-sm font-semibold mb-sm">版本历史</h4>
        <div v-for="v in contentVersions.slice(0, 5)" :key="v.id"
             class="flex items-center justify-between text-xs py-xs" style="border-bottom:1px solid #f0f0f0">
          <span>v{{ v.version_no }} · {{ v.source === 'ai_generated' ? 'AI生成' : '手动' }}</span>
          <span class="text-muted">{{ formatTime(v.created_at) }}</span>
        </div>
      </div>

      <!-- Conflict Panel -->
      <div v-if="conflict" class="card p-md mb-md" style="background:#fff3e0">
        <h4 class="font-semibold text-warning">编辑冲突</h4>
        <p class="text-sm">服务器版本与本地版本不一致，本地内容已保留。</p>
        <el-button size="small" @click="conflict = null">知道了</el-button>
      </div>
    </div>

    <!-- Right Panel -->
    <ContextPanel
      :versions="contextVersions"
      :locked-facts="lockedFacts"
      :impact-summary="impactSummary"
      :storyboard-intent="project?.storyboard_intent_status"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Cpu, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { contentProjectApi } from '@/api/contentProject'
import { currentStage, stageLabel, primaryAction } from './utils/workflowPath'
import { useGeneration } from './composables/useGeneration'
import WorkflowRail from './components/WorkflowRail.vue'
import ContextPanel from './components/ContextPanel.vue'

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

let currentUnitId = null
let autosaveTimer = null

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

    // load draft for last content unit
    if (project.value.last_content_unit_id) {
      currentUnitId = project.value.last_content_unit_id
      await loadDraftForUnit(currentUnitId)
      await loadVersionsForUnit(currentUnitId)
    }
  } catch (e) {
    error.value = e.response?.data?.message || '加载项目失败'
  } finally {
    loading.value = false
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

function modeLabel(m) {
  return { short_drama: '短剧', long_form: '长篇', tvc: 'TVC' }[m] || m
}

function statusLabel(s) {
  return { draft: '草稿', reviewing: '审核中', approved: '已通过', needs_revision: '需修改', locked: '已锁定' }[s] || s
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>
