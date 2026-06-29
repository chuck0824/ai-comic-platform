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
        <h3 class="font-semibold mb-sm">{{ stageLabel(currentStageInfo.key) }}</h3>
        <p v-if="currentStageInfo.primary_action" class="text-muted mb-md">
          {{ currentStageInfo.primary_action }}
        </p>

        <!-- Stage-specific content placeholder -->
        <div v-if="currentStageInfo.key === 'story_seed'" class="mb-md">
          <p class="text-sm text-muted">在此输入你的故事种子，AI将据此生成完整剧本。</p>
          <el-input v-model="draftContent" type="textarea" :rows="6" class="mt-sm"
                    placeholder="输入故事种子…" @input="onDraftChange" />
        </div>
        <div v-else class="mb-md">
          <p class="text-sm text-muted">此阶段的创作工具将在后续版本上线。</p>
        </div>

        <div class="flex gap-md">
          <el-button v-if="currentStageInfo.primary_action"
                     type="primary" @click="handlePrimaryAction">
            {{ currentStageInfo.primary_action }}
          </el-button>
          <!-- Storyboard choice at destination -->
          <template v-if="currentStageInfo.key === 'destination'">
            <el-button @click="skipStoryboard">完成并返回项目</el-button>
            <el-button v-if="project?.storyboard_intent_status !== 'requested'"
                       type="warning" @click="requestStoryboard">制作分镜</el-button>
          </template>
        </div>
      </div>

      <!-- Conflict Panel -->
      <div v-if="conflict" class="card p-md mb-md" style="background:#fff3e0">
        <h4 class="font-semibold text-warning">编辑冲突</h4>
        <p class="text-sm">服务器版本 (rev {{ conflict.serverRevision }}) 与本地版本 (rev {{ conflict.localRevision }}) 不一致。</p>
        <p class="text-sm text-muted">本地内容已保留，请手动处理冲突后重试保存。</p>
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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { contentProjectApi } from '@/api/contentProject'
import { currentStage, stageLabel, primaryAction } from './utils/workflowPath'
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
const contextVersions = ref([])
const lockedFacts = ref([])
const impactSummary = ref('')

let autosaveTimer = null
let currentUnitId = null

onMounted(() => loadProject())
onBeforeUnmount(() => { if (autosaveTimer) clearTimeout(autosaveTimer) })

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
      try {
        const draftRes = await contentProjectApi.getDraft(currentUnitId)
        draftContent.value = draftRes.data?.content_json
          ? (typeof draftRes.data.content_json === 'string'
            ? (safeJsonParse(draftRes.data.content_json)?.blocks?.join('\n') || draftRes.data.plain_text || '')
            : JSON.stringify(draftRes.data.content_json))
          : (draftRes.data?.plain_text || '')
      } catch (e) { /* no draft yet */ }
    }

    // save resume position
    const stage = currentStage(workflow.value?.stages || [])
    if (stage) {
      contentProjectApi.saveResume(projectId.value, {
        stage_key: stage.key,
        task_key: stage.primary_action,
        revision: project.value.revision
      }).catch(() => {})
    }
  } catch (e) {
    error.value = e.response?.data?.message || '加载项目失败'
  } finally {
    loading.value = false
  }
}

const currentStageInfo = computed(() => currentStage(workflow.value?.stages || []))

function modeLabel(m) {
  return { short_drama: '短剧', long_form: '长篇', tvc: 'TVC' }[m] || m
}

function statusLabel(s) {
  return { draft: '草稿', reviewing: '审核中', approved: '已通过', needs_revision: '需修改', locked: '已锁定' }[s] || s
}

function safeJsonParse(s) {
  try { return JSON.parse(s) } catch { return null }
}

// Autosave with debounce
function onDraftChange() {
  if (autosaveTimer) clearTimeout(autosaveTimer)
  autosaveTimer = setTimeout(() => saveDraft(), 2000)
}

async function saveDraft() {
  if (!currentUnitId) return
  autosaveState.value = '保存中…'
  try {
    const res = await contentProjectApi.saveDraft(currentUnitId, {
      revision: project.value.revision,
      content_json: JSON.stringify({ blocks: [draftContent.value] }),
      plain_text: draftContent.value
    })
    project.value.revision = res.data.revision
    autosaveState.value = '已保存'
    setTimeout(() => { autosaveState.value = '' }, 2000)
  } catch (e) {
    if (e.response?.status === 409) {
      conflict.value = {
        serverRevision: e.response.data?.revision || '?',
        localRevision: project.value.revision
      }
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

async function handlePrimaryAction() {
  // Placeholder: navigate or trigger generation in M1+
  const stage = currentStageInfo.value
  if (!stage) return
  if (stage.key === 'story_seed') {
    await saveDraft()
  }
}

async function skipStoryboard() {
  try {
    await contentProjectApi.setStoryboardIntent(projectId.value, 'skipped')
    await loadProject()
  } catch (e) {
    error.value = '操作失败: ' + (e.response?.data?.message || e.message)
  }
}

async function requestStoryboard() {
  try {
    await contentProjectApi.setStoryboardIntent(projectId.value, 'requested')
    await loadProject()
  } catch (e) {
    error.value = '操作失败: ' + (e.response?.data?.message || e.message)
  }
}
</script>
