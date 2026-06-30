import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storyboardV2Api } from '@/api/storyboardV2'
import {
  createRevisionSaveQueue, normalizeShot, SAVE_STATES,
  generateIdempotencyKey, buildConflictDiff
} from '../storyboardData'

export function useStoryboardEditor() {
  const route = useRoute()
  const router = useRouter()

  // ---- Core State ----
  const projectId = computed(() => Number(route.params.projectId))
  const storyboardId = computed(() => Number(route.params.storyboardId))
  const storyboard = ref(null)
  const versions = ref([])
  const activeVersion = ref(null)
  const scenes = ref([])
  const shots = ref([])
  const selectedShotId = ref(null)
  const activeModule = ref('shots')

  const loading = ref(true)
  const error = ref(null)
  const saveState = ref(SAVE_STATES.IDLE)
  const isDirty = ref(false)
  const conflictDiffs = ref([])

  let saveQueue = null
  let saveTimer = null

  // ---- Computed ----
  const isLocked = computed(() =>
    activeVersion.value?.status === 'locked' || activeVersion.value?.status === 'superseded')

  const selectedShot = computed(() =>
    shots.value.find(s => s.id === selectedShotId.value) || null)

  const shotsGroupedByScene = computed(() => {
    const map = new Map()
    for (const scene of scenes.value) {
      map.set(scene.id, { ...scene, shots: [] })
    }
    for (const shot of shots.value) {
      const group = map.get(shot.sceneId)
      if (group) group.shots.push(shot)
    }
    return Array.from(map.values())
  })

  const totalDurationMs = computed(() =>
    shots.value.reduce((sum, s) => sum + (s.durationMs || 0), 0))

  // ---- Load ----
  async function load() {
    loading.value = true
    error.value = null
    try {
      const [sbRes, verRes] = await Promise.all([
        storyboardV2Api.get(projectId.value, storyboardId.value),
        storyboardV2Api.listVersions(projectId.value, storyboardId.value)
      ])
      storyboard.value = sbRes.data
      versions.value = verRes.data

      // Determine active version
      const draftId = sbRes.data.currentDraftVersionId
      const lockedId = sbRes.data.currentLockedVersionId
      const activeVerId = draftId || lockedId
      if (activeVerId) {
        await switchVersion(activeVerId)
      }
      saveQueue = createRevisionSaveQueue(activeVersion.value?.revision || 0)
    } catch (e) {
      error.value = e.response?.data?.message || '加载分镜失败'
    } finally {
      loading.value = false
    }
  }

  async function switchVersion(versionId) {
    const verRes = await storyboardV2Api.getVersion(projectId.value, storyboardId.value, versionId)
    activeVersion.value = verRes.data
    saveQueue = createRevisionSaveQueue(verRes.data.revision || 0)

    const [scenesRes, shotsRes] = await Promise.all([
      storyboardV2Api.listScenes(projectId.value, storyboardId.value, versionId),
      storyboardV2Api.listShots(projectId.value, storyboardId.value, versionId, { page: 1, size: 500 })
    ])
    scenes.value = scenesRes.data
    shots.value = (shotsRes.data || []).map(normalizeShot)
  }

  // ---- Save ----
  function queuePatch(shotId, patch) {
    isDirty.value = true
    saveState.value = SAVE_STATES.WAITING

    // Optimistic update
    const idx = shots.value.findIndex(s => s.id === shotId)
    if (idx >= 0) {
      const original = { ...shots.value[idx] }
      shots.value[idx] = { ...shots.value[idx], ...patch }
      shots.value[idx]._rollback = () => { shots.value[idx] = original }
    }

    clearTimeout(saveTimer)
    saveTimer = setTimeout(() => flushSave(shotId, patch), 800)
  }

  async function flushSave(shotId, patch) {
    saveState.value = SAVE_STATES.SAVING
    try {
      const revision = saveQueue.getRevision()
      const result = await saveQueue.enqueue(async (rev) => {
        const res = await storyboardV2Api.patchShot(
          projectId.value, storyboardId.value, activeVersion.value.id, shotId,
          { ...patch, revision: rev })
        return { revision: res.data?.revision || rev + 1 }
      })
      saveState.value = SAVE_STATES.SAVED
      isDirty.value = false
      setTimeout(() => { if (saveState.value === SAVE_STATES.SAVED) saveState.value = SAVE_STATES.IDLE }, 2000)
    } catch (e) {
      if (e.response?.status === 409) {
        saveState.value = SAVE_STATES.CONFLICT
        // Reload server version for diff
        try {
          const res = await storyboardV2Api.getShot(projectId.value, storyboardId.value, activeVersion.value.id, shotId)
          const serverShot = normalizeShot(res.data)
          const localShot = shots.value.find(s => s.id === shotId)
          if (localShot) {
            conflictDiffs.value = buildConflictDiff(localShot, serverShot)
          }
        } catch (_) { /* ignore */ }
      } else {
        saveState.value = SAVE_STATES.FAILED
      }
    }
  }

  function resolveConflict(useServerVersion) {
    if (useServerVersion && selectedShot.value) {
      // Reload the shot from server
      loadShot(selectedShot.value.id)
    }
    conflictDiffs.value = []
    saveState.value = SAVE_STATES.IDLE
  }

  async function loadShot(shotId) {
    try {
      const res = await storyboardV2Api.getShot(projectId.value, storyboardId.value, activeVersion.value.id, shotId)
      const idx = shots.value.findIndex(s => s.id === shotId)
      if (idx >= 0) shots.value[idx] = normalizeShot(res.data)
    } catch (_) { /* ignore */ }
  }

  // ---- Structural Operations ----
  async function addShot(sceneId) {
    const res = await storyboardV2Api.createShot(projectId.value, storyboardId.value, activeVersion.value.id, { sceneId, durationMs: 3000 })
    await switchVersion(activeVersion.value.id)
    selectedShotId.value = res.data.id
    ElMessage.success('镜头已添加')
  }

  async function duplicateShot(shotId) {
    await storyboardV2Api.copyShot(projectId.value, storyboardId.value, activeVersion.value.id, shotId)
    await switchVersion(activeVersion.value.id)
    ElMessage.success('镜头已复制')
  }

  async function removeShot(shotId) {
    try {
      await ElMessageBox.confirm('确定删除该镜头？', '确认', { type: 'warning' })
      await storyboardV2Api.deleteShot(projectId.value, storyboardId.value, activeVersion.value.id, shotId)
      await switchVersion(activeVersion.value.id)
      if (selectedShotId.value === shotId) selectedShotId.value = null
      ElMessage.success('镜头已删除')
    } catch (_) { /* cancelled */ }
  }

  async function splitShot(shotId, firstDurationMs) {
    await storyboardV2Api.splitShot(projectId.value, storyboardId.value, activeVersion.value.id, shotId, { firstDurationMs })
    await switchVersion(activeVersion.value.id)
    ElMessage.success('镜头已拆分')
  }

  // ---- Version Operations ----
  async function lockCurrentVersion() {
    const key = generateIdempotencyKey()
    await storyboardV2Api.lockVersion(projectId.value, storyboardId.value, activeVersion.value.id, { revision: activeVersion.value.revision }, key)
    await load()
    ElMessage.success('版本已锁定')
  }

  async function forkVersion() {
    const key = generateIdempotencyKey()
    await storyboardV2Api.forkVersion(projectId.value, storyboardId.value, activeVersion.value.id, key)
    await load()
    ElMessage.success('已创建新草稿')
  }

  async function upgradeVersion(targetTier) {
    const key = generateIdempotencyKey()
    await storyboardV2Api.upgradeVersion(projectId.value, storyboardId.value, activeVersion.value.id, { targetTier, idempotencyKey: key })
    await load()
    ElMessage.success(`已升档至 ${targetTier} 档`)
  }

  // ---- Leave Protection ----
  onBeforeRouteLeave((to, from, next) => {
    if (isDirty.value || saveState.value === SAVE_STATES.FAILED) {
      ElMessageBox.confirm('有未保存的修改，确定离开吗？', '未保存修改', {
        confirmButtonText: '离开', cancelButtonText: '留下', type: 'warning'
      }).then(() => next()).catch(() => next(false))
    } else {
      next()
    }
  })

  function beforeUnloadHandler(e) {
    if (isDirty.value || saveState.value === SAVE_STATES.FAILED) {
      e.preventDefault()
      e.returnValue = ''
    }
  }

  // Setup
  if (typeof window !== 'undefined') {
    window.addEventListener('beforeunload', beforeUnloadHandler)
  }

  onBeforeUnmount(() => {
    if (typeof window !== 'undefined') {
      window.removeEventListener('beforeunload', beforeUnloadHandler)
    }
    clearTimeout(saveTimer)
  })

  return {
    // State
    projectId, storyboardId, storyboard, versions, activeVersion,
    scenes, shots, selectedShotId, activeModule,
    loading, error, saveState, isDirty, conflictDiffs,
    // Computed
    isLocked, selectedShot, shotsGroupedByScene, totalDurationMs,
    // Actions
    load, switchVersion,
    queuePatch, resolveConflict,
    addShot, duplicateShot, removeShot, splitShot,
    lockCurrentVersion, forkVersion, upgradeVersion,
    setSelectedShot: (id) => { selectedShotId.value = id },
    setActiveModule: (m) => { activeModule.value = m }
  }
}
