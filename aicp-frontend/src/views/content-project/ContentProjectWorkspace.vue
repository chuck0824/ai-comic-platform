<template>
  <div class="workspace">
    <WorkflowRail
      :stages="workbench.stages.value"
      :progress="workbench.progress.value"
      :entered-stages="workbench.state.enteredStages"
      @navigate="navigateStage"
      @previous="previousStage"
      @save-draft="saveCurrentDraft"
      @confirm-next="confirmNextStage"
    />

    <main class="workspace-main">
      <header class="workspace-header">
        <div>
          <h1>{{ project?.name || '加载中…' }}</h1>
          <div class="workspace-meta">
            <el-tag size="small">{{ modeLabel(project?.creation_mode) }}</el-tag>
            <el-tag size="small" :type="statusTagType(project?.content_status)">{{ statusLabel(project?.content_status) }}</el-tag>
            <span>{{ activeStageLabel }}</span>
            <span v-if="autosaveState">{{ autosaveState }}</span>
          </div>
        </div>
        <div class="workspace-actions">
          <span class="model-context">{{ modelContext }}</span>
          <el-button @click="sceneLibraryVisible = !sceneLibraryVisible">场景资产</el-button>
          <el-button @click="openTaskEntry">任务 {{ workbench.state.tasks.length }}</el-button>
          <el-button @click="openResultEntry">结果 {{ workbench.state.results.length }}</el-button>
          <el-button @click="router.push('/script-gen')">返回启动台</el-button>
        </div>
      </header>

      <div v-if="loading" class="workspace-state"><el-icon class="is-loading" :size="32"><Loading /></el-icon><p>正在加载项目…</p></div>
      <div v-else-if="error" class="workspace-state"><el-result icon="error" title="加载失败" :sub-title="error"><template #extra><el-button @click="loadProject">重试</el-button></template></el-result></div>

      <template v-else>
        <el-alert v-if="routeNotice" type="warning" :title="routeNotice" show-icon :closable="false" class="route-notice" />
        <StageStalenessBanner
          v-if="stageTruthEnabled"
          :server-state="activeStageServerState"
          :stale-reasons="activeStageStaleReasons"
          :loading="stalenessLoading"
          @resolve="handleStalenessResolve"
        />

        <section v-if="sceneLibraryVisible" class="shared-panel card">
          <SceneAssetLibrary :scene-assets="sceneAssets" @guidance="showGuidance" @open-result="openSceneResult" />
        </section>

        <section class="stage-card card" :data-stage="workbench.activeStage.value">
          <CreationSettingsStage
            v-if="workbench.activeStage.value === 'creation_settings'"
            v-model="stageData.creationSettings"
            :persist-settings="persistSettings"
            @guidance="showGuidance"
            @saved="handleSettingsSaved"
          />
          <NovelUploadStage
            v-else-if="workbench.activeStage.value === 'novel_upload'"
            :upload-file="uploadNovelFile"
            :persist-pasted-text="persistPastedNovel"
            @guidance="showGuidance"
            @uploaded="handleNovelUploaded"
          />
          <NovelAnalysisStage
            v-else-if="workbench.activeStage.value === 'novel_analysis'"
            v-model="stageData.novelAnalysis"
            :persist-artifact="persistAnalysisArtifact"
            :scene-assets="sceneAssets"
            @guidance="showGuidance"
            @open-scene-asset="sceneLibraryVisible = true"
            @open-scene-action-result="openSceneResult"
          />
          <AdaptationStage
            v-else-if="workbench.activeStage.value === 'adaptation'"
            v-model="stageData.adaptation"
            :creation-settings="stageData.creationSettings"
            :persist-hook="persistAdaptationHook"
            :persist-plan="persistAdaptationPlan"
            :regenerate-artifact="task => submitStageGeneration('adaptation', {}, task)"
            :workbench="workbench"
            @guidance="showGuidance"
            @regenerated="handleGenerationResult"
          />
          <StructuredScriptStage
            v-else-if="workbench.activeStage.value === 'structured_script'"
            v-model="stageData.structuredScript"
            :open-episode-adapter="persistStructuredAction"
            :add-beat-adapter="persistStructuredAction"
            :regenerate-beat-adapter="persistStructuredAction"
            :regenerate-artifact-adapter="task => submitStageGeneration('structured_script', {}, task)"
            :workbench="workbench"
            :generation-input="generationInput"
            @guidance="showGuidance"
            @result="handleStageResult"
          />
          <ScriptBodyStage
            v-else-if="workbench.activeStage.value === 'script_body'"
            v-model="stageData.scriptBody"
            :scene-asset-state="sceneAssets"
            :scene-assets-degraded="sceneAssets.state.value === 'readonly'"
            :block-action-adapter="submitScriptBlockAction"
            :local-rewrite-adopt-adapter="adoptScriptLocalRewrite"
            :bind-scene-asset-adapter="bindScriptSceneAsset"
            :create-scene-asset-adapter="createAndReturnSceneAsset"
            :space-change-adapter="persistScriptBodyAction"
            :script-check-adapter="runScriptBodyCheck"
            :export-adapter="exportScriptBody"
            :regenerate-artifact-adapter="task => submitStageGeneration('script_body', {}, task)"
            :workbench="workbench"
            :generation-input="generationInput"
            @guidance="showGuidance"
            @result="handleStageResult"
            @open-scene-asset="sceneLibraryVisible = true"
            @open-scene-action-result="openSceneResult"
          />
          <ReviewRevisionStage
            v-else-if="workbench.activeStage.value === 'review_revision'"
            v-model="stageData.reviewRevision"
            episode-id="EP-001"
            :save-revision-adapter="persistReviewAction"
            :approve-adapter="approveReviewEpisode"
            :regenerate-artifact-adapter="task => submitStageGeneration('review_revision', {}, task)"
            :workbench="workbench"
            :generation-input="generationInput"
            @guidance="showGuidance"
            @result="handleStageResult"
          />
          <TextStoryboardStage
            v-else-if="workbench.activeStage.value === 'text_storyboard'"
            v-model="stageData.textStoryboard"
            :storyboard-scenes="storyboardScenes"
            :scene-asset-state="sceneAssets"
            :add-shot-adapter="addStoryboardShot"
            :split-shot-adapter="splitStoryboardShot"
            :merge-shot-adapter="mergeStoryboardShots"
            :continuity-adapter="runStoryboardContinuity"
            :archive-adapter="archiveStoryboard"
            :mindmap-adapter="persistMindmap"
            :canvas-adapter="createCanvasProject"
            :regenerate-artifact-adapter="task => submitStageGeneration('text_storyboard', {}, task)"
            :workbench="workbench"
            :generation-input="generationInput"
            @guidance="showGuidance"
            @result="handleStageResult"
            @archived="handleStoryboardArchived"
            @canvas-created="handoffCanvas"
            @open-scene-asset="sceneLibraryVisible = true"
            @open-scene-action-result="openSceneResult"
          />
        </section>
      </template>
    </main>

    <ActionGuidanceDialog :visible="Boolean(guidance)" :guidance="guidance" @close="guidance = null" @target="handleGuidanceTarget" />
    <GenerationProgressDialog
      :visible="Boolean(workbench.generationTaskRecord.value)"
      :task="workbench.generationTaskRecord.value"
      @cancel="cancelGenerationTask"
      @close="noop"
    />
    <ActionResultDrawer
      :visible="resultVisible"
      :result="selectedResult"
      @accept="acceptGenerationResult"
      @discard="discardGenerationResult"
      @close="resultVisible = false"
    />
    <el-dialog :model-value="transitionVisible" title="正在保存并进入下一步" width="440px" :show-close="false" :close-on-click-modal="false">
      <el-progress :percentage="workbench.state.transition?.percentage || 0" />
      <p>{{ workbench.state.transition?.message || '正在保存阶段产物…' }}</p>
    </el-dialog>
    <el-dialog
      v-model="conflictComparisonVisible"
      title="草稿冲突对照"
      width="640px"
      @close="conflictComparison = null"
    >
      <template v-if="conflictComparison">
        <p class="conflict-meta">
          本地 base={{ conflictComparison.base_revision ?? conflictComparison.baseRevision ?? '—' }}
          · 服务端 revision={{ conflictComparison.server_revision ?? conflictComparison.serverRevision ?? '—' }}
          · hash={{ conflictComparison.server_content_hash ?? conflictComparison.serverContentHash ?? '—' }}
        </p>
        <pre class="conflict-preview">{{ conflictComparison.server_plain_text || conflictComparison.serverPlainText || conflictComparison.server_content_json || conflictComparison.serverContentJson || '（远端草稿为空）' }}</pre>
      </template>
      <template #footer>
        <el-button @click="conflictComparisonVisible = false">关闭</el-button>
        <el-button type="primary" @click="loadRemoteDraftForConflict(); conflictComparisonVisible = false">
          加载远端草稿
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contentProjectApi } from '@/api/contentProject'
import { sceneAssetApi } from '@/api/sceneAsset'
import { storyboardV2Api } from '@/api/storyboardV2'
import WorkflowRail from './components/WorkflowRail.vue'
import StageStalenessBanner from './components/StageStalenessBanner.vue'
import SceneAssetLibrary from './components/SceneAssetLibrary.vue'
import ActionGuidanceDialog from './components/ActionGuidanceDialog.vue'
import GenerationProgressDialog from './components/GenerationProgressDialog.vue'
import ActionResultDrawer from './components/ActionResultDrawer.vue'
import CreationSettingsStage from './stages/CreationSettingsStage.vue'
import NovelUploadStage from './stages/NovelUploadStage.vue'
import NovelAnalysisStage from './stages/NovelAnalysisStage.vue'
import AdaptationStage from './stages/AdaptationStage.vue'
import StructuredScriptStage from './stages/StructuredScriptStage.vue'
import ScriptBodyStage from './stages/ScriptBodyStage.vue'
import ReviewRevisionStage from './stages/ReviewRevisionStage.vue'
import TextStoryboardStage from './stages/TextStoryboardStage.vue'
import { STAGES, createWorkbenchState } from './workbench/scriptWorkbenchModel.js'
import { useScriptWorkbench } from './workbench/useScriptWorkbench.js'
import { useSceneAssets } from './workbench/useSceneAssets.js'
import { createWorkspaceAdapters, normalizeBatchGeneration } from './workbench/workspaceAdapters.js'
import { nextStageKey, resolveWorkspaceStage, restoreWorkbenchStage, shouldAdvanceResume } from './workbench/workspaceRouting.js'
import {
  applyStageTruthProjection,
  buildStageTransitionPayload,
  checkpointRevisionOf,
  resolveWorkspaceStageFromTruth
} from './workbench/stageTruthAdapter.js'
import {
  extractGateDetailsFromError,
  formatGateIssues,
  hasDurationVarianceUnconfirmed,
  isStageGateBlockedError,
  issueCodes,
  normalizeGateView
} from './workbench/stageGateHelper.js'
import { buildForkStageRequest, shouldForkBeforeEdit } from './workbench/stageForkHelper.js'
import { createDraftSaveQueue } from './workbench/draftSaveQueue.js'
import {
  conflictActionOptions,
  extractEditConflict,
  formatEditConflictMessage
} from './workbench/editConflictHelper.js'
import { scriptStageFeatures } from '@/config/scriptStageFeatures.js'
import { validateAnalysisSection, validateCreationSettings } from './workbench/upstreamStageModel.js'
import { createProjectLoadGuard, resetProjectWorkspaceData } from './workbench/workspaceLoadState.js'
import { trackGenerationJob } from './workbench/generationJobTracker.js'
import { createGenerationDecisionGuard, loadAcceptedGeneration, loadUnitWorkspaceContent, persistGenerationDecision, runGuardedGenerationDecision } from './workbench/generationResultPersistence.js'
import {
  applyBlockReplacement,
  buildAdoptPatchesRequest,
  buildLocalRewriteRequest,
  findBlockRange,
  flattenScriptBodyPlainText,
  isCandidateStale,
  normalizeLocalRewriteCandidate,
  operationFromBlockAction,
  selectPartialPatches,
  sha256Hex
} from './workbench/localRewriteHelper.js'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.projectId))
const project = ref(null)
const units = ref([])
const loading = ref(true)
const error = ref('')
const routeNotice = ref('')
const autosaveState = ref('')
const guidance = ref(null)
const sceneLibraryVisible = ref(false)
const resultVisible = ref(false)
const selectedResult = ref(null)
const storyboard = reactive({ id: null, versionId: null, revision: 0, locked: false })
const storyboardScenes = ref([])
const storyboardShotIds = new Map()
let autosaveTimer = null
const projectLoadGuard = createProjectLoadGuard()
const decisionGuard = createGenerationDecisionGuard()
const activeGenerationJobs = new Map()
const stageTruthEnabled = scriptStageFeatures().stageTruthEnabled
const stageTruthProjection = ref(null)
const stalenessLoading = ref(false)
const draftSaveQueue = createDraftSaveQueue()
const conflictComparison = ref(null)
const conflictComparisonVisible = ref(false)

const defaultEpisode = () => ({ id: 'EP-001', title: '第 1 集', beats: [], scenes: [] })
const stageData = reactive({
  creationSettings: {},
  novelUpload: {},
  novelAnalysis: {},
  adaptation: { hooks: [] },
  structuredScript: { episodes: [defaultEpisode()] },
  scriptBody: { episodes: [defaultEpisode()] },
  reviewRevision: { issues: [] },
  textStoryboard: { shots: [] }
})

const sceneAssets = useSceneAssets(projectId, {
  isProjectArchived: () => String(project.value?.content_status).toLowerCase() === 'archived'
})

const adapters = createWorkspaceAdapters({
  projectId: () => projectId.value,
  project: () => project.value,
  api: contentProjectApi,
  sceneApi: sceneAssetApi,
  activeUnitId: () => units.value.find(item => item.unit_type === workbench.activeStage.value)?.id ?? null
})

const workbench = useScriptWorkbench({ persistStage, persistFinalStage })
const activeStageLabel = computed(() => STAGES.find(stage => stage.key === workbench.activeStage.value)?.label || workbench.activeStage.value)
const activeStageServerState = computed(() => {
  const stage = workbench.state.stages.find(item => item.key === workbench.activeStage.value)
  return stage?.serverState || ''
})
const activeStageStaleReasons = computed(() => {
  const key = workbench.activeStage.value
  const row = workbench.state.stageTruth?.stagesByKey?.[key]
  return row?.stale_reasons || row?.staleReasons || []
})
const modelContext = computed(() => {
  const settings = stageData.creationSettings
  if (!settings.model) return '未选择模型'
  return `${settings.model.name || settings.model.id} · ${settings.model.demo ? '0' : (settings.estimatedPoints ?? '—')} 积分`
})
const generationInput = computed(() => ({ model: stageData.creationSettings.model, estimatedPoints: stageData.creationSettings.estimatedPoints }))
const transitionVisible = computed(() => workbench.state.transition?.status === 'persisting')

onMounted(() => {
  loadProject()
  window.addEventListener('beforeunload', onBeforeUnload)
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  if (autosaveTimer) clearTimeout(autosaveTimer)
  activeGenerationJobs.forEach(record => record.controller.abort())
  activeGenerationJobs.clear()
})
onBeforeRouteLeave((_to, _from, next) => {
  if (!draftSaveQueue.shouldBlockLeave()) return next()
  ElMessageBox.confirm('有未保存的修改或保存失败，确定离开吗？', '未保存修改', {
    type: 'warning',
    confirmButtonText: '离开',
    cancelButtonText: '留下'
  }).then(() => next()).catch(() => next(false))
})
watch(() => route.params.projectId, (next, previous) => { if (next !== previous) loadProject() })
watch(() => route.query.stage, stage => {
  if (!project.value || Number(project.value.id) !== projectId.value) return
  let resolved
  if (stageTruthEnabled && workbench.state.stageTruth?.enabled) {
    resolved = resolveWorkspaceStageFromTruth({
      enteredStages: workbench.state.enteredStages,
      lastStageKey: workbench.state.stageTruth.lastStageKey || project.value.last_stage_key,
      queryStage: stage
    })
  } else {
    resolved = resolveWorkspaceStage({ persistedStage: project.value.last_stage_key, queryStage: stage })
  }
  if (resolved !== stage) replaceStageQuery(resolved)
  workbench.navigate(resolved)
})
watch(stageData, () => {
  if (loading.value) return
  draftSaveQueue.markDirty()
  if (autosaveTimer) clearTimeout(autosaveTimer)
  autosaveTimer = setTimeout(saveCurrentDraft, 2000)
}, { deep: true })

function onBeforeUnload(event) {
  if (!draftSaveQueue.shouldBlockLeave()) return
  event.preventDefault()
  event.returnValue = ''
}
function responseData(response) { return response?.data?.data ?? response?.data ?? response ?? {} }
function clone(value) { return JSON.parse(JSON.stringify(value ?? {})) }
function stageDataKey(stage) {
  return ({
    creation_settings: 'creationSettings', novel_upload: 'novelUpload', novel_analysis: 'novelAnalysis', adaptation: 'adaptation',
    structured_script: 'structuredScript', script_body: 'scriptBody', review_revision: 'reviewRevision', text_storyboard: 'textStoryboard'
  })[stage]
}
function currentStagePayload() { return clone(stageData[stageDataKey(workbench.activeStage.value)]) }
function replaceStageQuery(stage) { router.replace({ name: 'ScriptGenWorkspace', params: { projectId: projectId.value }, query: { ...route.query, stage } }) }
function showGuidance(value) {
  guidance.value = {
    title: value?.title || '操作未完成',
    message: value?.message || '请根据提示补全条件后重试。',
    targetAction: value?.targetAction,
    code: value?.code,
    conflict: value?.conflict,
    items: value?.items || value?.blockers || null,
    actions: value?.actions || null
  }
  return value
}

function handleGuidanceTarget(action) {
  const current = guidance.value
  const code = action || current?.targetAction
  guidance.value = null
  if (code === 'load_remote_draft') return loadRemoteDraftForConflict()
  if (code === 'keep_local_retry' || code === 'retry_stage_save') return saveCurrentDraft()
  if (code === 'open_comparison') return openConflictComparison(current?.conflict)
}

async function openConflictComparison(conflict) {
  const stage = workbench.activeStage.value
  const unit = units.value.find(item => item.unit_type === stage)
  if (!unit) {
    return showGuidance({ code: 'UNIT_REQUIRED', title: '内容单元不存在', message: '请刷新后重试。' })
  }
  try {
    const data = responseData(await contentProjectApi.getConflicts(unit.id, {
      base_revision: conflict?.base_revision ?? conflict?.baseRevision
    }))
    conflictComparison.value = data
    conflictComparisonVisible.value = true
  } catch (caught) {
    showGuidance({
      code: 'CONFLICT_COMPARE_FAILED',
      title: '无法加载冲突对照',
      message: caught?.response?.data?.message || caught?.message || '请稍后重试'
    })
  }
}

async function loadProject() {
  const requestedProjectId = Number(route.params.projectId)
  const loadToken = projectLoadGuard.begin(requestedProjectId)
  resetWorkspaceForProject()
  loading.value = true
  try {
    const [projectResponse, unitResponse, parameterResponse] = await Promise.all([
      contentProjectApi.get(requestedProjectId), contentProjectApi.listUnits(requestedProjectId), contentProjectApi.listParameterVersions(requestedProjectId)
    ])
    if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return
    project.value = responseData(projectResponse)
    units.value = responseData(unitResponse)?.items || responseData(unitResponse) || []
    const parameters = responseData(parameterResponse) || []
    // Backend returns parameter versions by version_no DESC, so index 0 is authoritative.
    if (parameters.length) Object.assign(stageData.creationSettings, parameters[0]?.payload || {})
    await loadUnitDrafts(loadToken, requestedProjectId)
    await loadAdaptationHooks(loadToken, requestedProjectId)
    await Promise.all([loadStoryboard(loadToken, requestedProjectId), sceneAssets.load()])
    if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return

    const requestedStage = String(route.query.stage || '')
    let resolvedStage
    if (stageTruthEnabled) {
      await loadStageTruthProjection(loadToken, requestedProjectId)
      if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return
      resolvedStage = resolveWorkspaceStageFromTruth({
        enteredStages: workbench.state.enteredStages,
        lastStageKey: workbench.state.stageTruth?.lastStageKey || project.value.last_stage_key,
        queryStage: requestedStage
      })
      applyStageTruthProjection(workbench.state, stageTruthProjection.value, resolvedStage)
    } else {
      resolvedStage = resolveWorkspaceStage({ persistedStage: project.value.last_stage_key, queryStage: requestedStage })
      restoreWorkbenchStage(workbench.state, resolvedStage, project.value.last_stage_key)
    }
    if (requestedStage && requestedStage !== resolvedStage) routeNotice.value = '已拦截未完成阶段跳转，并恢复到最近保存位置。'
    replaceStageQuery(resolvedStage)
  } catch (caught) {
    if (projectLoadGuard.accept(loadToken, requestedProjectId)) {
      error.value = caught?.response?.data?.message || caught?.message || '加载项目失败'
    }
  } finally {
    if (projectLoadGuard.accept(loadToken, requestedProjectId)) loading.value = false
  }
}

async function loadStageTruthProjection(loadToken, requestedProjectId) {
  const payload = responseData(await contentProjectApi.listStageCheckpoints(requestedProjectId))
  if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return
  stageTruthProjection.value = payload
  // 服务端开关关闭时仍可预览投影；前端 flag 决定是否用于导航/流转
  applyStageTruthProjection(workbench.state, payload, payload.last_stage_key || payload.lastStageKey)
}

function resetWorkspaceForProject() {
  if (autosaveTimer) clearTimeout(autosaveTimer)
  autosaveTimer = null
  activeGenerationJobs.forEach(record => record.controller.abort())
  activeGenerationJobs.clear()
  project.value = null
  units.value = []
  error.value = ''
  routeNotice.value = ''
  autosaveState.value = ''
  guidance.value = null
  draftSaveQueue.markClean()
  sceneLibraryVisible.value = false
  resultVisible.value = false
  selectedResult.value = null
  Object.assign(storyboard, { id: null, versionId: null, revision: 0, locked: false })
  storyboardScenes.value = []
  storyboardShotIds.clear()
  stageTruthProjection.value = null
  resetProjectWorkspaceData(stageData)
  stageData.structuredScript.episodes = [defaultEpisode()]
  stageData.scriptBody.episodes = [defaultEpisode()]
  sceneAssets.reset()
  Object.assign(workbench.state, createWorkbenchState())
}

async function loadUnitDrafts(loadToken, requestedProjectId) {
  await Promise.all(units.value.map(async unit => {
    const key = stageDataKey(unit.unit_type)
    if (!key || unit.unit_type === 'novel_upload') return
    try {
      const loaded = await loadUnitWorkspaceContent({
        unit,
        listVersions: async unitId => responseData(await contentProjectApi.listVersions(unitId)) || [],
        getDraft: async unitId => responseData(await contentProjectApi.getDraft(unitId))
      })
      if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return
      Object.assign(stageData[key], loaded.content)
      if (loaded.draft) unit.revision = loaded.draft.revision ?? unit.revision
    } catch { /* a content unit may not have a draft yet */ }
  }))
}

async function loadAdaptationHooks(loadToken, requestedProjectId) {
  try {
    const payload = responseData(await contentProjectApi.hookSummary(requestedProjectId))
    if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return
    const hooks = payload.hooks || payload.items || (Array.isArray(payload) ? payload : [])
    if (hooks.length) stageData.adaptation.hooks = hooks
  } catch { /* the stage remains usable and explains missing hook prerequisites */ }
}

async function loadStoryboard(loadToken, requestedProjectId) {
  try {
    const masters = responseData(await contentProjectApi.listStoryboardMasters(requestedProjectId)) || []
    if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return
    const master = masters[0]
    if (!master) return
    const detail = responseData(await contentProjectApi.getStoryboardMaster(requestedProjectId, master.id))
    if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return
    const storyboardId = detail.id
    const storyboardVersionId = detail.currentDraftVersionId || detail.current_draft_version_id || detail.currentLockedVersionId || detail.current_locked_version_id
    if (!storyboardVersionId) return
    const version = responseData(await storyboardV2Api.getVersion(requestedProjectId, storyboardId, storyboardVersionId))
    if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return
    const storyboardLocked = ['LOCKED', 'SUPERSEDED'].includes(String(version.state || version.status).toUpperCase())
    const [scenesResponse, shotsResponse] = await Promise.all([
      contentProjectApi.listStoryboardVersionScenes(requestedProjectId, storyboardId, storyboardVersionId),
      contentProjectApi.listStoryboardVersionShots(requestedProjectId, storyboardId, storyboardVersionId)
    ])
    if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return
    const loadedScenes = responseData(scenesResponse) || []
    const shots = (responseData(shotsResponse)?.items || responseData(shotsResponse) || []).map(toWorkbenchShot)
    const contentUnit = units.value.find(item => item.unit_type === 'script_body')
    let contentVersionLocked = false
    if (contentUnit?.current_version_id) {
      try {
        const versions = responseData(await contentProjectApi.listVersions(contentUnit.id)) || []
        if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return
        const active = versions.find(item => item.id === contentUnit.current_version_id)
        contentVersionLocked = ['approved', 'locked'].includes(String(active?.status).toLowerCase())
      } catch { /* the final gate remains blocked when version proof is unavailable */ }
    }
    if (!projectLoadGuard.accept(loadToken, requestedProjectId)) return
    Object.assign(storyboard, { id: storyboardId, versionId: storyboardVersionId, revision: version.revision || 0, locked: storyboardLocked })
    storyboardScenes.value = loadedScenes
    storyboardShotIds.clear()
    shots.forEach(shot => storyboardShotIds.set(shot.id, shot.id))
    Object.assign(stageData.textStoryboard, {
      contentVersionId: contentUnit?.current_version_id || null,
      contentVersionLocked,
      storyboardVersionId,
      storyboardVersionLocked: storyboardLocked,
      shots
    })
  } catch (caught) {
    if (projectLoadGuard.accept(loadToken, requestedProjectId)) {
      showGuidance({ code: 'STORYBOARD_LOAD_FAILED', title: '分镜数据未加载', message: caught?.message || '请稍后重试。', targetAction: 'retry_storyboard_load' })
    }
  }
}

function toWorkbenchShot(shot) {
  const snapshot = shot.sceneAssetSnapshot || shot.scene_asset_snapshot || null
  const hasBinding = shot.sceneAssetId != null || shot.scene_asset_id != null
  return {
    ...shot,
    id: shot.id,
    sceneId: shot.sceneId ?? shot.scene_id,
    description: shot.visualDescription || shot.visual_description || shot.description || '',
    durationMs: shot.durationMs ?? shot.duration_ms ?? 3000,
    assetBinding: hasBinding ? {
      sceneAssetId: shot.sceneAssetId ?? shot.scene_asset_id,
      sceneAssetVersionId: shot.sceneAssetVersionId ?? shot.scene_asset_version_id,
      sceneVariantId: shot.sceneVariantId ?? shot.scene_variant_id,
      sceneVariantVersion: shot.sceneVariantVersion ?? shot.scene_variant_version,
      sceneOverride: {}
    } : null,
    snapshotLocked: Boolean(snapshot),
    sceneAssetSnapshotRef: snapshot ? {
      shotId: shot.id,
      fingerprint: snapshot.fingerprint,
      sceneAssetId: snapshot.sceneAssetId ?? snapshot.scene_asset_id,
      sceneAssetVersionId: snapshot.sceneAssetVersionId ?? snapshot.scene_asset_version_id,
      sceneVariantId: snapshot.sceneVariantId ?? snapshot.scene_variant_id,
      sceneVariantVersion: snapshot.sceneVariantVersion ?? snapshot.scene_variant_version
    } : null
  }
}

async function ensureUnit(stage) {
  let unit = units.value.find(item => item.unit_type === stage)
  if (unit) return unit
  const created = responseData(await contentProjectApi.createUnit(projectId.value, {
    unit_type: stage, display_no: units.value.length + 1, title: STAGES.find(item => item.key === stage)?.label || stage
  }))
  units.value.push(created)
  return created
}

async function ensureForkBeforeEdit(stageKey = workbench.activeStage.value) {
  if (!stageTruthEnabled || !workbench.state.stageTruth?.enabled) return true
  const stage = workbench.state.stages.find(item => item.key === stageKey)
  if (!shouldForkBeforeEdit(stage?.serverState)) return true
  const row = workbench.state.stageTruth.stagesByKey?.[stageKey]
  const adoptedId = row?.adopted_content_version_id ?? row?.adoptedContentVersionId
  const idempotencyKey = (typeof crypto !== 'undefined' && crypto.randomUUID)
    ? crypto.randomUUID()
    : `stage-fork-${Date.now()}`
  const projection = responseData(await contentProjectApi.forkStage(
    projectId.value,
    stageKey,
    buildForkStageRequest({
      checkpointRevision: stage?.checkpointRevision ?? 0,
      adoptedContentVersionId: adoptedId,
      projectRevision: workbench.state.stageTruth.projectRevision ?? project.value?.revision
    }),
    { 'Idempotency-Key': idempotencyKey }
  ))
  stageTruthProjection.value = projection
  project.value = {
    ...project.value,
    last_stage_key: projection.last_stage_key || projection.lastStageKey || project.value.last_stage_key,
    revision: projection.project_revision ?? projection.projectRevision ?? project.value.revision
  }
  applyStageTruthProjection(workbench.state, projection, stageKey)
  routeNotice.value = '已为完成阶段创建新草稿，下游成果已标记为需确认。'
  return true
}

async function handleStalenessResolve({ action, reasonFilter }) {
  if (!stageTruthEnabled) return
  stalenessLoading.value = true
  try {
    const stageKey = workbench.activeStage.value
    const stage = workbench.state.stages.find(item => item.key === stageKey)
    const idempotencyKey = (typeof crypto !== 'undefined' && crypto.randomUUID)
      ? crypto.randomUUID()
      : `stale-${Date.now()}`
    const projection = responseData(await contentProjectApi.resolveStaleness(
      projectId.value,
      stageKey,
      {
        action,
        checkpoint_revision: stage?.checkpointRevision ?? 0,
        reason_filter: reasonFilter,
        note: ''
      },
      { 'Idempotency-Key': idempotencyKey }
    ))
    stageTruthProjection.value = projection
    applyStageTruthProjection(workbench.state, projection, stageKey)
    ElMessage.success('过期状态已处理')
  } catch (caught) {
    showGuidance({
      code: 'STALENESS_RESOLVE_FAILED',
      title: '过期处理失败',
      message: caught?.response?.data?.message || caught?.message || '处理失败'
    })
  } finally {
    stalenessLoading.value = false
  }
}

async function persistUnit(stage, payload) {
  if (stageTruthEnabled) await ensureForkBeforeEdit(stage)
  const unit = await ensureUnit(stage)
  const plainText = stage === 'script_body'
    ? flattenScriptBodyPlainText(payload).plainText
    : JSON.stringify(payload, null, 2)
  const saved = responseData(await contentProjectApi.saveDraft(unit.id, {
    revision: unit.revision || 0,
    content_json: JSON.stringify(payload),
    plain_text: plainText
  }))
  unit.revision = saved.revision ?? unit.revision
  return { persisted: true, version: unit.revision, unitId: unit.id, artifactPath: `content-units/${unit.id}/draft` }
}

async function refreshProjectRevision() {
  project.value = responseData(await contentProjectApi.get(projectId.value))
  return project.value
}

async function persistSettings(settings) {
  try {
    if (stageTruthEnabled) await ensureForkBeforeEdit('creation_settings')
    const persisted = await adapters.persistSettings(clone(settings))
    Object.assign(stageData.creationSettings, clone(settings))
    await refreshProjectRevision()
    return { ...persisted, version: project.value.current_parameter_version_id }
  } catch (caught) { return { persisted: false, message: caught?.message || '创作设置保存失败' } }
}

function handleSettingsSaved() {
  if (route.query.next === 'novel_upload') routeNotice.value = '创作设置已保存，确认进入下一步后将打开小说上传。'
}

async function persistStage(targetStage) {
  const current = workbench.activeStage.value
  const saved = current === 'creation_settings'
    ? await persistSettings(stageData.creationSettings)
    : await persistUnit(current, currentStagePayload())
  if (!saved.persisted) return saved
  if (shouldAdvanceResume(project.value.last_stage_key, targetStage)) await adapters.persistStage(targetStage)
  return { persisted: true, message: '阶段产物与恢复位置已保存' }
}

async function persistFinalStage() {
  const saved = await persistUnit('text_storyboard', clone(stageData.textStoryboard))
  if (!saved.persisted) return saved
  project.value = responseData(await contentProjectApi.saveResume(projectId.value, {
    stage_key: 'text_storyboard', task_key: 'completed', content_unit_id: saved.unitId, revision: project.value.revision
  }))
  return { persisted: true }
}

async function saveCurrentDraft() {
  if (loading.value || !project.value) return { persisted: true }
  draftSaveQueue.markDirty()
  return draftSaveQueue.enqueue(async () => {
    autosaveState.value = '保存中…'
    try {
      const result = workbench.activeStage.value === 'creation_settings'
        ? await persistSettings(stageData.creationSettings)
        : await persistUnit(workbench.activeStage.value, currentStagePayload())
      if (!result.persisted) throw Object.assign(new Error(result.message || '草稿保存失败'), {
        response: result.code === 'EDIT_CONFLICT'
          ? { data: { code: 43003, data: result.conflict } }
          : undefined,
        conflict: result.conflict,
        code: result.code
      })
      autosaveState.value = '已保存'
      setTimeout(() => { if (autosaveState.value === '已保存') autosaveState.value = '' }, 1500)
      return result
    } catch (caught) {
      autosaveState.value = '保存失败'
      const conflict = caught?.conflict || extractEditConflict(caught)
      if (conflict) {
        showGuidance({
          code: 'EDIT_CONFLICT',
          title: '草稿冲突',
          message: formatEditConflictMessage(conflict),
          targetAction: 'load_remote_draft',
          conflict,
          actions: conflictActionOptions(conflict)
        })
        return { persisted: false, code: 'EDIT_CONFLICT', conflict, message: caught?.message || '编辑冲突' }
      }
      showGuidance({
        code: 'STAGE_SAVE_FAILED',
        title: '草稿保存失败',
        message: caught?.message,
        targetAction: 'retry_stage_save'
      })
      return { persisted: false, message: caught?.message || '草稿保存失败' }
    }
  })
}

async function loadRemoteDraftForConflict() {
  const stage = workbench.activeStage.value
  const unit = units.value.find(item => item.unit_type === stage)
  if (!unit) return showGuidance({ code: 'UNIT_REQUIRED', title: '内容单元不存在', message: '请刷新后重试。' })
  try {
    const draft = responseData(await contentProjectApi.getDraft(unit.id))
    unit.revision = draft.revision ?? unit.revision
    if (draft.content_json || draft.contentJson) {
      const parsed = JSON.parse(draft.content_json || draft.contentJson)
      const keyMap = {
        creation_settings: 'creationSettings', novel_upload: 'novelUpload', novel_analysis: 'novelAnalysis',
        adaptation: 'adaptation', structured_script: 'structuredScript', script_body: 'scriptBody',
        review_revision: 'reviewRevision', text_storyboard: 'textStoryboard'
      }
      const field = keyMap[stage]
      if (field && parsed && typeof parsed === 'object') Object.assign(stageData[field], parsed)
    }
    ElMessage.success('已加载远端草稿')
    draftSaveQueue.markClean()
    autosaveState.value = ''
    guidance.value = null
  } catch (caught) {
    showGuidance({
      code: 'REMOTE_DRAFT_LOAD_FAILED',
      title: '加载远端草稿失败',
      message: caught?.response?.data?.message || caught?.message || '请稍后重试'
    })
  }
}

async function confirmNextStage() {
  const guarded = transitionGuard()
  if (guarded) return showGuidance(guarded)
  const next = nextStageKey(workbench.activeStage.value)
  if (stageTruthEnabled) {
    const prepared = await prepareStageTruthTransition(workbench.activeStage.value)
    if (!prepared.ok) {
      if (prepared.cancelled) return
      return showGuidance({
        code: 'STAGE_GATE_BLOCKED',
        title: '门禁未通过',
        message: prepared.message || '请处理阻断项后重试。',
        items: prepared.blockers,
        targetAction: 'retry_stage_transition'
      })
    }
    if (!next) return completeFinalStageWithTruth(prepared.acknowledgements)
    const transition = await transitionWithStageTruth(next, prepared.acknowledgements)
    if (transition.status === 'completed') {
      replaceStageQuery(next)
      if (route.query.next === next) {
        router.replace({
          name: 'ScriptGenWorkspace',
          params: { projectId: projectId.value },
          query: { stage: next, ...(route.query.variant ? { variant: route.query.variant } : {}) }
        })
      }
    } else if (transition.code === 43010 || transition.code === 'STAGE_GATE_BLOCKED') {
      showGuidance({
        code: 'STAGE_GATE_BLOCKED',
        title: '门禁未通过',
        message: transition.message,
        items: transition.blockers || transition.gate?.blockers,
        targetAction: 'retry_stage_transition'
      })
    } else {
      showGuidance({
        code: transition.code || 'STAGE_TRANSITION_FAILED',
        title: '无法进入下一步',
        message: transition.message,
        targetAction: 'retry_stage_transition'
      })
    }
    return
  }
  if (!next) return completeFinalStage()
  const transition = await workbench.transition(next)
  if (transition.status === 'completed') {
    replaceStageQuery(next)
    if (route.query.next === next) {
      router.replace({
        name: 'ScriptGenWorkspace',
        params: { projectId: projectId.value },
        query: { stage: next, ...(route.query.variant ? { variant: route.query.variant } : {}) }
      })
    }
  } else {
    showGuidance({
      code: 'STAGE_TRANSITION_FAILED',
      title: '无法进入下一步',
      message: transition.message,
      targetAction: 'retry_stage_transition'
    })
  }
}

/** 预览服务端门禁；有警告时要求用户确认，返回 warning_acknowledgements。 */
async function prepareStageTruthTransition(sourceStage) {
  try {
    let gate = normalizeGateView(responseData(await contentProjectApi.previewStageGate(projectId.value, sourceStage)))

    if (hasDurationVarianceUnconfirmed(gate) && sourceStage === 'structured_script') {
      try {
        await ElMessageBox.confirm(
          `${formatGateIssues(gate.blockers.filter(item => item.code === 'DURATION_VARIANCE_UNCONFIRMED')) || '目标时长偏差超阈值。'}\n\n确认后将写入 durationVarianceConfirmed 并重新校验。`,
          '确认时长偏差',
          { type: 'warning', confirmButtonText: '确认偏差并继续', cancelButtonText: '返回修改' }
        )
      } catch {
        return { ok: false, cancelled: true }
      }
      stageData.structuredScript.durationVarianceConfirmed = true
      const saved = await saveCurrentDraft()
      if (saved && saved.persisted === false) {
        return {
          ok: false,
          message: saved.message || '确认时长偏差后保存失败',
          blockers: gate.blockers
        }
      }
      gate = normalizeGateView(responseData(await contentProjectApi.previewStageGate(projectId.value, sourceStage)))
    }

    if (gate.blockers.length) {
      return {
        ok: false,
        message: formatGateIssues(gate.blockers) || '门禁存在阻断项',
        blockers: gate.blockers,
        warnings: gate.warnings,
        gate
      }
    }

    let acknowledgements = []
    if (gate.warnings.length) {
      try {
        await ElMessageBox.confirm(
          `进入下一阶段前请确认以下警告：\n\n${formatGateIssues(gate.warnings)}`,
          '阶段警告确认',
          { type: 'warning', confirmButtonText: '已确认，继续', cancelButtonText: '取消' }
        )
        acknowledgements = issueCodes(gate.warnings)
      } catch {
        return { ok: false, cancelled: true }
      }
    }
    return { ok: true, acknowledgements, gate }
  } catch (caught) {
    return {
      ok: false,
      message: caught?.response?.data?.message || caught?.message || '门禁预览失败',
      blockers: extractGateDetailsFromError(caught)?.blockers || []
    }
  }
}

async function transitionWithStageTruth(targetStage, warningAcknowledgements = []) {
  const sourceStage = workbench.activeStage.value
  workbench.state.transition = {
    targetStage,
    sourceStage,
    percentage: 10,
    status: 'persisting',
    message: '正在校验门禁并保存阶段…'
  }
  try {
    // 先落盘当前草稿，再走服务端原子流转
    const saved = await saveCurrentDraft()
    if (saved && saved.persisted === false) {
      workbench.state.transition = { ...workbench.state.transition, status: 'error', message: saved.message || '草稿保存失败' }
      return workbench.state.transition
    }
    workbench.state.transition.percentage = 40
    const activeUnit = units.value.find(item => item.unit_type === sourceStage)
    const targetRow = workbench.state.stages.find(item => item.key === targetStage)
    const payload = buildStageTransitionPayload({
      sourceStageKey: sourceStage,
      targetStageKey: targetStage,
      projectRevision: workbench.state.stageTruth?.projectRevision ?? project.value?.revision,
      sourceCheckpointRevision: checkpointRevisionOf(workbench.state, sourceStage),
      targetCheckpointRevision: targetRow?.checkpointRevision ?? checkpointRevisionOf(workbench.state, targetStage),
      contentUnitId: activeUnit?.id,
      contentUnitRevision: activeUnit?.revision,
      warningAcknowledgements
    })
    const idempotencyKey = (typeof crypto !== 'undefined' && crypto.randomUUID)
      ? crypto.randomUUID()
      : `stage-transition-${Date.now()}`
    const projection = responseData(await contentProjectApi.stageTransition(
      projectId.value,
      payload,
      { 'Idempotency-Key': idempotencyKey }
    ))
    stageTruthProjection.value = projection
    project.value = {
      ...project.value,
      last_stage_key: projection.last_stage_key || projection.lastStageKey || targetStage,
      revision: projection.project_revision ?? projection.projectRevision ?? project.value.revision
    }
    applyStageTruthProjection(workbench.state, projection, targetStage)
    workbench.state.transition = {
      targetStage,
      sourceStage,
      percentage: 100,
      status: 'completed',
      message: '阶段已保存'
    }
    return workbench.state.transition
  } catch (caught) {
    const message = caught?.response?.data?.message || caught?.message || '阶段流转失败'
    const code = caught?.response?.data?.code
    const gate = extractGateDetailsFromError(caught)
    workbench.state.transition = {
      targetStage,
      sourceStage,
      percentage: workbench.state.transition?.percentage || 0,
      status: 'error',
      message,
      code,
      blockers: gate?.blockers,
      warnings: gate?.warnings,
      gate
    }
    return workbench.state.transition
  }
}

async function completeFinalStageWithTruth(warningAcknowledgements = []) {
  const sourceStage = 'text_storyboard'
  try {
    await saveCurrentDraft()
    const activeUnit = units.value.find(item => item.unit_type === sourceStage)
    const payload = buildStageTransitionPayload({
      sourceStageKey: sourceStage,
      targetStageKey: null,
      action: 'COMPLETE_HANDOFF',
      projectRevision: workbench.state.stageTruth?.projectRevision ?? project.value?.revision,
      sourceCheckpointRevision: checkpointRevisionOf(workbench.state, sourceStage),
      contentUnitId: activeUnit?.id,
      contentUnitRevision: activeUnit?.revision,
      warningAcknowledgements
    })
    const idempotencyKey = (typeof crypto !== 'undefined' && crypto.randomUUID)
      ? crypto.randomUUID()
      : `stage-handoff-${Date.now()}`
    const projection = responseData(await contentProjectApi.stageTransition(
      projectId.value,
      payload,
      { 'Idempotency-Key': idempotencyKey }
    ))
    stageTruthProjection.value = projection
    applyStageTruthProjection(workbench.state, projection, sourceStage)
    ElMessage.success('八阶段已完成，交接快照已提交')
  } catch (caught) {
    if (isStageGateBlockedError(caught)) {
      const gate = extractGateDetailsFromError(caught)
      return showGuidance({
        code: 'STAGE_GATE_BLOCKED',
        title: '完成交接失败',
        message: caught?.response?.data?.message || '门禁未通过',
        items: gate?.blockers,
        targetAction: 'complete_storyboard_archive'
      })
    }
    showGuidance({
      code: 'STAGE_HANDOFF_FAILED',
      title: '完成交接失败',
      message: caught?.response?.data?.message || caught?.message || 'COMPLETE_HANDOFF 失败'
    })
  }
}

async function completeFinalStage() {
  const result = await workbench.completeFinalStageWithPersistence()
  if (result?.allowed === false) {
    return showGuidance(result)
  }
  ElMessage.success('八阶段流程已完成')
}

async function handleStoryboardArchived() {
  if (stageTruthEnabled) {
    const prepared = await prepareStageTruthTransition('text_storyboard')
    if (!prepared.ok) {
      if (prepared.cancelled) return
      return showGuidance({
        code: 'STAGE_GATE_BLOCKED',
        title: '完成交接失败',
        message: prepared.message || '门禁未通过',
        items: prepared.blockers,
        targetAction: 'complete_storyboard_archive'
      })
    }
    return completeFinalStageWithTruth(prepared.acknowledgements)
  }
  return completeFinalStage()
}

function transitionGuard() {
  const stage = workbench.activeStage.value
  if (stage === 'creation_settings') {
    const validation = validateCreationSettings(stageData.creationSettings)
    return validation.allowed ? null : validation
  }
  if (stage === 'novel_upload' && !stageData.novelUpload.source) return { code: 'NOVEL_UPLOAD_REQUIRED', title: '请先上传小说', message: '上传文件或保存粘贴文本后才能进入小说分析。', targetAction: 'focus_novel_upload' }
  if (stage === 'novel_analysis') {
    for (const [section, value] of Object.entries({
      synopsis: stageData.novelAnalysis.synopsis,
      events: stageData.novelAnalysis.events,
      chapterOutline: stageData.novelAnalysis.chapterOutline,
      worldview: { ...(stageData.novelAnalysis.worldview || {}), locations: stageData.novelAnalysis.locations || [] },
      characters: stageData.novelAnalysis.characters
    })) {
      const validation = validateAnalysisSection(section, value)
      if (!validation.allowed) return validation
    }
  }
  if (stage === 'adaptation' && !stageData.adaptation.confirmed) return { code: 'ADAPTATION_CONFIRMATION_REQUIRED', title: '请先确认改编方案', message: '持久化高压开场并确认改编方案后才能进入结构化文字剧本。', targetAction: 'confirm_adaptation' }
  if (stage === 'structured_script' && !stageData.structuredScript.episodes?.some(episode => episode.beats?.length)) return { code: 'STRUCTURED_SCRIPT_REQUIRED', title: '请先完成单集结构', message: '至少为一集新增并保存一个节拍。', targetAction: 'focus_episode_structure' }
  if (stage === 'script_body') {
    const scenes = (stageData.scriptBody.episodes || []).flatMap(episode => episode.scenes || [])
    if (!scenes.length) return { code: 'SCRIPT_SCENE_REQUIRED', title: '请先新增剧本场景', message: '剧本正文至少需要一个场景。', targetAction: 'focus_script_scenes' }
    if (scenes.some(scene => !scene.assetBinding && scene.bindingState !== 'DEFERRED')) return { code: 'SCENE_ASSET_BINDING_REQUIRED', title: '请处理场景资产绑定', message: '绑定场景资产，或明确选择稍后绑定后再进入审核修订。', targetAction: 'focus_scene_asset_binding' }
  }
  if (stage === 'review_revision' && !stageData.reviewRevision.approvedEpisodeIds?.length) return { code: 'REVIEW_APPROVAL_REQUIRED', title: '请先审核通过本集', message: '解决 HIGH/BLOCKER 问题并完成审核通过后才能进入文字分镜。', targetAction: 'approve_review_episode' }
  if (stage === 'text_storyboard' && !stageData.textStoryboard.archived) return { code: 'STORYBOARD_ARCHIVE_REQUIRED', title: '请先完成并归档', message: '通过连续性检查并锁定所有场景快照后，再完成八阶段流程。', targetAction: 'complete_storyboard_archive' }
  return null
}

function previousStage() {
  const index = STAGES.findIndex(stage => stage.key === workbench.activeStage.value)
  if (index <= 0) return showGuidance({ code: 'FIRST_STAGE', title: '已是第一步', message: '当前已在创作设置。' })
  navigateStage(STAGES[index - 1].key)
}
function navigateStage(stage) {
  if (!workbench.navigate(stage)) return showGuidance({ code: 'STAGE_NOT_ENTERED', title: '阶段尚未解锁', message: '请按创作顺序完成前置阶段。' })
  replaceStageQuery(stage)
}

async function uploadNovelFile(file) {
  const form = new FormData(); form.append('file', file)
  const upload = responseData(await contentProjectApi.uploadFile(form))
  stageData.novelUpload = { source: 'file', uploadId: upload.id || upload.upload_id, fileName: file.name }
  await persistUnit('novel_upload', stageData.novelUpload)
  return { uploaded: true, persisted: true, upload }
}
async function persistPastedNovel(payload) {
  stageData.novelUpload = { source: 'paste', ...payload }
  return persistUnit('novel_upload', stageData.novelUpload)
}
function handleNovelUploaded(payload) { Object.assign(stageData.novelUpload, payload) }

async function persistAnalysisArtifact({ section, value, previousVersion }) {
  const next = clone(stageData.novelAnalysis); next[section] = value
  const persisted = await persistUnit('novel_analysis', next)
  if (persisted.persisted) Object.assign(stageData.novelAnalysis, next)
  return { ...persisted, version: Math.max(Number(persisted.version) || 0, Number(previousVersion) + 1), impact: { stale: ['adaptation', 'structured_script', 'script_body'] } }
}
async function persistAdaptationHook(hookId) {
  const persisted = await persistUnit('adaptation', { ...clone(stageData.adaptation), selectedHookId: hookId })
  return { ...persisted, version: Math.max(Number(persisted.version) || 1, Number(stageData.adaptation.hookVersion || 0) + 1) }
}
async function persistAdaptationPlan(payload) {
  const persisted = await persistUnit('adaptation', payload)
  return { ...persisted, version: Math.max(Number(persisted.version) || 1, Number(stageData.adaptation.version || 0) + 1), impact: { stale: ['structured_script', 'script_body'] } }
}
async function persistStructuredAction(payload) {
  const persisted = await persistUnit('structured_script', { ...clone(stageData.structuredScript), pendingAction: payload })
  return { ...persisted, beat: payload }
}
async function persistScriptBodyAction(payload) { return persistUnit('script_body', { ...clone(stageData.scriptBody), pendingAction: payload }) }
async function persistReviewAction(payload) { return { ...(await persistUnit('review_revision', { ...clone(stageData.reviewRevision), pendingAction: payload })), version: Date.now(), revision: payload } }
async function persistMindmap(configuration) { return { ...(await persistUnit('text_storyboard', { ...clone(stageData.textStoryboard), mindmap: configuration })), configuration } }

async function runScriptBodyCheck() {
  const unit = await ensureUnit('script_body')
  const check = responseData(await contentProjectApi.reviewUnit(unit.id))
  return { persisted: true, check }
}

async function approveReviewEpisode(payload) {
  const unit = units.value.find(item => String(item.id) === String(payload.episodeId)) || await ensureUnit('review_revision')
  const version = responseData(await contentProjectApi.createVersion(unit.id, { status: 'approved' }))
  await persistUnit('review_revision', { ...clone(stageData.reviewRevision), approval: payload, versionId: version.id })
  return { persisted: true, version: version.id, approval: payload }
}

async function submitStageGeneration(stage, payload = {}, localTask = null) {
  const unit = await ensureUnit(stage)
  const batch = normalizeBatchGeneration(await contentProjectApi.batchGenerate(projectId.value, [unit.id], `${stage}_generate`))
  if (!batch.ok) throw Object.assign(new Error(batch.message), { code: batch.code })
  const controller = new AbortController()
  if (localTask?.id) activeGenerationJobs.set(localTask.id, { serverJobId: batch.job.id, controller })
  let job
  try {
    job = await trackGenerationJob({
      job: batch.job,
      getJob: contentProjectApi.getGenerationJob,
      signal: controller.signal,
      onProgress: update => {
        if (localTask?.id) workbench.updateGenerationProgress(localTask.id, update)
      }
    })
  } finally {
    if (localTask?.id) activeGenerationJobs.delete(localTask.id)
  }
  const artifactPath = job.artifact_ref ?? job.artifactRef ?? null
  const resultVersionId = job.result_version_id ?? job.resultVersionId ?? null
  return {
    artifact: {
      path: artifactPath,
      jobId: job.id,
      status: 'completed',
      availability: artifactPath && resultVersionId ? 'candidate' : 'pending_reference',
      message: artifactPath && resultVersionId ? '候选版本已生成，采用后才会切换当前内容。' : '任务已完成，但服务端尚未返回候选版本。',
      version: resultVersionId
    },
    actualPoints: job.actualCredits ?? job.actual_credits ?? null,
    impact: job.impact || '当前阶段与关联下游产物',
    batch: { total: batch.total, jobIds: batch.jobs.map(item => item.id) },
    request: payload
  }
}

async function submitScriptBlockAction(payload) {
  if (!stageTruthEnabled) {
    const outcome = await submitStageGeneration('script_body', payload)
    await persistUnit('script_body', { ...clone(stageData.scriptBody), pendingGeneration: { payload, taskPath: outcome.artifact.path } })
    return { persisted: true, result: outcome }
  }
  return submitLocalRewriteBlockAction(payload)
}

async function submitLocalRewriteBlockAction(payload) {
  const block = payload?.block
  if (!block?.id) {
    return { allowed: false, code: 'SCRIPT_BLOCK_REQUIRED', title: '请先选择正文块', message: '选择正文块后再执行局部改写。' }
  }
  if (!(String(block.text || '').trim())) {
    return { allowed: false, code: 'SCRIPT_BLOCK_EMPTY', title: '正文块为空', message: '请先填写正文后再请求 AI 局部改写。' }
  }
  await persistUnit('script_body', clone(stageData.scriptBody))
  const unit = await ensureUnit('script_body')
  const { plainText, ranges } = flattenScriptBodyPlainText(stageData.scriptBody)
  const range = findBlockRange(ranges, block.id)
  if (!range || range.start >= range.end) {
    return { allowed: false, code: 'SCRIPT_BLOCK_RANGE_MISSING', title: '无法定位选区', message: '刷新后重试局部改写。' }
  }
  const requestBody = await buildLocalRewriteRequest({
    baseVersionId: unit.current_version_id || unit.currentVersionId || null,
    contentUnitRevision: unit.revision || 0,
    plainText,
    startOffset: range.start,
    endOffset: range.end,
    operation: operationFromBlockAction(payload.action),
    prompt: payload.action
  })
  const idempotencyKey = (typeof crypto !== 'undefined' && crypto.randomUUID)
    ? crypto.randomUUID()
    : `local-rewrite-${Date.now()}`
  const raw = responseData(await contentProjectApi.createLocalRewrite(
    unit.id,
    requestBody,
    { 'Idempotency-Key': idempotencyKey }
  ))
  const candidate = normalizeLocalRewriteCandidate({
    ...raw,
    base_content_hash: requestBody.content_hash,
    base_revision: unit.revision || 0
  })
  return {
    persisted: true,
    localRewrite: true,
    block: clone(block),
    candidate,
    result: {
      mode: 'local_rewrite',
      jobId: candidate.jobId,
      candidateVersionId: candidate.candidateVersionId,
      before: candidate.before,
      after: candidate.after
    }
  }
}

async function adoptScriptLocalRewrite({ blockId, candidate, plainText, selectedPatchIndexes }) {
  const unit = await ensureUnit('script_body')
  const currentPlain = plainText ?? flattenScriptBodyPlainText(stageData.scriptBody).plainText
  const currentHash = await sha256Hex(currentPlain)
  if (isCandidateStale({
    candidateContentHash: candidate?.baseContentHash,
    currentPlainTextHash: currentHash,
    candidateRevision: candidate?.baseRevision,
    currentRevision: unit.revision
  })) {
    return {
      allowed: false,
      ok: false,
      code: 'AI_CANDIDATE_STALE',
      title: '候选已过期',
      message: '正文或 revision 已变化，请重新生成局部改写后再采用。'
    }
  }

  const allIndexes = (candidate?.patches || []).map((_, index) => index)
  const indexes = Array.isArray(selectedPatchIndexes) && selectedPatchIndexes.length
    ? selectedPatchIndexes
    : allIndexes
  const patches = selectPartialPatches(candidate?.patches || [], indexes)
  if (!patches.length) {
    return {
      allowed: false,
      ok: false,
      code: 'LOCAL_REWRITE_PATCH_REQUIRED',
      title: '请选择改动',
      message: '至少选择一处 Patch 后再采用。'
    }
  }

  const body = await buildAdoptPatchesRequest({
    contentUnitRevision: unit.revision || 0,
    plainText: currentPlain,
    patches
  })
  const idempotencyKey = (typeof crypto !== 'undefined' && crypto.randomUUID)
    ? crypto.randomUUID()
    : `local-rewrite-adopt-${Date.now()}`
  const draft = responseData(await contentProjectApi.adoptLocalRewrite(
    unit.id,
    body,
    { 'Idempotency-Key': idempotencyKey }
  ))
  unit.revision = draft.revision ?? unit.revision
  const replacement = patches.length === (candidate?.patches || []).length
    ? (candidate?.after ?? patches[0]?.replacement ?? '')
    : (patches[patches.length - 1]?.replacement ?? candidate?.after ?? '')
  const nextBody = applyBlockReplacement(stageData.scriptBody, blockId, replacement)
  Object.assign(stageData.scriptBody, nextBody)
  await persistUnit('script_body', clone(stageData.scriptBody))
  return { persisted: true, adopted: true, draft, block: { id: blockId, text: replacement } }
}

async function createAndReturnSceneAsset(draft) {
  const result = await sceneAssets.create(draft)
  return result.ok ? { persisted: true, asset: result.data || sceneAssets.selectedAsset.value } : result
}
async function bindScriptSceneAsset(binding, { sceneId }) {
  const application = await adapters.bindScriptScene(binding, { sceneId })
  if (!application.persisted) return application
  await persistUnit('script_body', { ...clone(stageData.scriptBody), bindingEvidence: { sceneId, binding, application } })
  return application
}
async function exportScriptBody() {
  const unit = await ensureUnit('script_body')
  const versions = responseData(await contentProjectApi.listVersions(unit.id)) || []
  return { persisted: true, format: 'markdown', artifactPath: `content-units/${unit.id}/versions`, versions: versions.length }
}

function storyboardRequired() {
  if (storyboard.id && storyboard.versionId) return null
  return { persisted: false, message: '请先在分镜专业编辑器创建可编辑分镜版本。' }
}
async function addStoryboardShot(payload) {
  const missing = storyboardRequired(); if (missing) return missing
  const shot = responseData(await contentProjectApi.createStoryboardShot(projectId.value, storyboard.id, storyboard.versionId, {
    sceneId: payload.sceneId,
    durationMs: payload.durationMs,
    visualDescription: payload.description
  }))
  storyboardShotIds.set(payload.id, shot.id)
  return { persisted: true, shot }
}
async function splitStoryboardShot(payload) {
  const missing = storyboardRequired(); if (missing) return missing
  const serverShotId = storyboardShotIds.get(payload.shotId) ?? payload.shotId
  const firstDurationMs = Math.max(1, Math.floor(Number(payload.source?.durationMs || 3000) / 2))
  const shots = responseData(await contentProjectApi.splitStoryboardShot(projectId.value, storyboard.id, storyboard.versionId, serverShotId, { firstDurationMs }))
  return { persisted: true, shots: (shots.items || shots).map(toWorkbenchShot) }
}
async function mergeStoryboardShots(payload) {
  const missing = storyboardRequired(); if (missing) return missing
  const shotIds = payload.shotIds.map(id => storyboardShotIds.get(id) ?? id)
  const shot = responseData(await contentProjectApi.mergeStoryboardShots(projectId.value, storyboard.id, storyboard.versionId, { shotIds, revision: storyboard.revision }))
  return { persisted: true, shot: toWorkbenchShot(shot) }
}
async function runStoryboardContinuity() {
  const missing = storyboardRequired(); if (missing) return missing
  const result = responseData(await contentProjectApi.runStoryboardContinuityCheck(projectId.value, storyboard.id, storyboard.versionId))
  return { persisted: true, passed: result.valid === true || result.passed === true, issues: result.issues || [] }
}
async function archiveStoryboard() {
  const missing = storyboardRequired(); if (missing) return missing
  const locked = responseData(await contentProjectApi.lockStoryboardVersion(projectId.value, storyboard.id, storyboard.versionId, storyboard.revision))
  storyboard.locked = true
  return { persisted: true, locked }
}
async function createCanvasProject(payload) {
  const missing = storyboardRequired(); if (missing) return missing
  const job = responseData(await contentProjectApi.createStoryboardCanvasSnapshot(projectId.value, storyboard.id, storyboard.versionId, {
    snapshotType: 'production', idempotencyKey: `canvas-${projectId.value}-${storyboard.versionId}-${Date.now()}`, ...payload
  }))
  return { persisted: true, job, projectId: job.canvasProjectId || job.canvas_project_id }
}
function handoffCanvas(result) {
  const canvasId = result?.projectId || result?.canvasProjectId
  if (canvasId) router.push(`/canvas/${canvasId}`)
  else showGuidance({ code: 'CANVAS_JOB_CREATED', title: '画布任务已创建', message: '快照任务已持久化，请在画布项目中查看生成结果。', targetAction: 'open_canvas_projects' })
}

function handleStageResult(result) { if (result?.allowed === false || result?.ok === false) showGuidance(result) }
function handleGenerationResult(result) { selectedResult.value = result; resultVisible.value = true }
function openSceneResult(result) { selectedResult.value = result; sceneLibraryVisible.value = true }
function openTaskEntry() {
  const task = workbench.state.tasks.at(-1)
  if (!task) showGuidance({ code: 'NO_TASKS', title: '暂无生成任务', message: '在阶段内执行重新生成后，可在此跟踪进度。' })
  else if (task.status !== 'running') openResultEntry()
}
function openResultEntry() {
  selectedResult.value = workbench.state.results.at(-1) || null
  if (!selectedResult.value) return showGuidance({ code: 'NO_RESULTS', title: '暂无生成结果', message: '完成一次生成任务后可在此对比与采用。' })
  resultVisible.value = true
}
async function cancelGenerationTask(taskId) {
  const record = activeGenerationJobs.get(taskId)
  if (!record) return showGuidance({ code: 'GENERATION_JOB_NOT_ACTIVE', title: '任务无法取消', message: '未找到正在跟踪的生成任务。', targetAction: 'refresh_generation_status' })
  try {
    await contentProjectApi.cancelGenerationJob(record.serverJobId)
  } catch (caught) {
    return showGuidance({ code: 'GENERATION_CANCEL_FAILED', title: '取消请求失败', message: caught?.response?.data?.message || caught?.message || '请稍后重试。', targetAction: 'retry_generation_cancel' })
  }
  record.controller.abort()
}
async function refreshAcceptedGeneration(response) {
  const loaded = await loadAcceptedGeneration({
    response,
    listUnits: async () => {
      const refreshed = responseData(await contentProjectApi.listUnits(projectId.value))
      return refreshed?.items || refreshed || []
    },
    listVersions: async unitId => responseData(await contentProjectApi.listVersions(unitId)) || []
  })
  units.value = loaded.units
  const key = stageDataKey(loaded.unit?.unit_type)
  if (!key || key === 'novelUpload') return
  Object.assign(stageData[key], loaded.content)
}
async function decideGenerationResult(taskId, decision) {
  const persisted = await runGuardedGenerationDecision({
    guard: decisionGuard,
    taskId,
    decision,
    execute: async () => {
      const result = workbench.state.results.find(item => item.taskId === taskId)
      return persistGenerationDecision({
        decision,
        serverJobId: result?.artifact?.jobId,
        localTaskId: taskId,
        api: contentProjectApi,
        workbench,
        refresh: refreshAcceptedGeneration
      })
    },
    onFailure: failure => showGuidance({ ...failure, title: decision === 'accept' ? '采用失败' : '丢弃失败', targetAction: `retry_generation_${decision}` })
  })
  if (!persisted.ok) return persisted
  resultVisible.value = false
  if (persisted.refreshFailure) showGuidance({ ...persisted.refreshFailure, title: '候选版本已采用，但页面刷新失败', targetAction: 'refresh_project_units' })
  return persisted
}
function acceptGenerationResult(taskId) { return decideGenerationResult(taskId, 'accept') }
function discardGenerationResult(taskId) { return decideGenerationResult(taskId, 'discard') }
function noop() {}
function modeLabel(mode) { return ({ short_drama: '短剧', long_form: '长篇', tvc: 'TVC' })[mode] || mode || '未知' }
function statusLabel(status) { return ({ draft: '草稿', reviewing: '审核中', approved: '已通过', needs_revision: '需修订', locked: '已锁定', archived: '已归档' })[status] || status || '草稿' }
function statusTagType(status) { return ({ reviewing: 'warning', approved: 'success', needs_revision: 'danger', archived: 'info' })[status] || 'info' }
</script>

<style scoped>
.workspace{display:flex;height:calc(100vh - var(--topbar-h));min-height:680px}.workspace-main{flex:1;min-width:0;overflow:auto;padding:20px 24px;background:var(--bg-app)}.workspace-header{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;margin-bottom:18px}.workspace-header h1{margin:0 0 8px;font-size:20px}.workspace-meta,.workspace-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.workspace-meta{color:var(--text-secondary);font-size:13px}.workspace-actions{justify-content:flex-end}.model-context{font-size:12px;color:var(--text-secondary);padding:6px 10px;border:1px solid var(--border);border-radius:8px}.workspace-state{display:grid;place-items:center;padding:70px;color:var(--text-secondary)}.stage-card,.shared-panel{padding:20px;margin-bottom:18px}.route-notice{margin-bottom:16px}.conflict-meta{margin:0 0 12px;color:var(--text-secondary);font-size:13px}.conflict-preview{margin:0;max-height:360px;overflow:auto;white-space:pre-wrap;word-break:break-word;padding:12px;border-radius:8px;background:var(--el-fill-color-light);font-size:13px;line-height:1.6}@media(max-width:1000px){.workspace-header{flex-direction:column}.workspace-actions{justify-content:flex-start}}@media(max-width:760px){.workspace{display:block;height:auto}.workspace-main{padding:12px}.workspace-actions{align-items:stretch}.workspace-actions .el-button{margin-left:0}}
</style>
