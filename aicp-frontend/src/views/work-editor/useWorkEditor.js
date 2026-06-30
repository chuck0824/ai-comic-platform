/**
 * 作品编辑中心 — Vue 3 Composable。
 * 管理编辑器加载、保存、错误、脏状态和 revision 跟踪。
 */
import { ref, reactive, computed, onBeforeUnmount } from 'vue'
import { contentProjectApi, tagDictionaryApi } from '@/api/contentProject'
import {
  normalizeEditorResponse,
  makeTagPayload,
  createSaveQueue,
  getCachedDictionary,
  setCachedDictionary,
  isDictionaryStale
} from './workEditorData'
import { ElMessage } from 'element-plus'

export function useWorkEditor() {
  // ---- State ----
  const loading = ref(false)
  const saving = ref(false)
  const error = ref(null)
  const editorData = ref(null)          // normalized EditorView
  const dirtyFlags = reactive({})       // section -> boolean
  const saveStatus = reactive({})       // section -> 'idle'|'saving'|'saved'|'error'|'conflict'
  const dictionary = ref(null)

  const saveQueue = createSaveQueue()

  const isReadOnly = computed(() => {
    const perm = editorData.value?.permissions
    return perm === 'viewer'
  })

  // ---- Load ----

  async function loadEditor(projectIdOrScriptId, isLegacy = false) {
    loading.value = true
    error.value = null
    try {
      const api = isLegacy
        ? contentProjectApi.getLegacyEditor(projectIdOrScriptId)
        : contentProjectApi.getEditor(projectIdOrScriptId)
      const res = await api
      editorData.value = normalizeEditorResponse(res?.data ?? res)
    } catch (e) {
      error.value = e?.response?.data?.message || e.message || '加载失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function loadDictionary() {
    try {
      const res = await tagDictionaryApi.get()
      const dict = res?.data ?? res
      if (isDictionaryStale(dict?.version)) {
        ElMessage.warning('标签字典已更新，请刷新页面获取最新选项')
      }
      setCachedDictionary(dict)
      dictionary.value = dict
    } catch (e) {
      // 静默失败，使用缓存字典或默认值
      dictionary.value = getCachedDictionary()
    }
  }

  // ---- Save ----

  async function saveTags(newTags) {
    if (!editorData.value) return
    const payload = makeTagPayload(editorData.value.profile, newTags)
    return saveQueue.enqueue(async () => {
      saving.value = true
      saveStatus.tags = 'saving'
      try {
        const res = await contentProjectApi.updateTags(editorData.value.projectId, payload)
        const profile = res?.data ?? res
        if (editorData.value) {
          editorData.value.profile = { ...editorData.value.profile, ...profile, revision: profile.revision }
          editorData.value.revision = profile.revision
        }
        saveStatus.tags = 'saved'
        dirtyFlags.tags = false
      } catch (e) {
        if (e?.response?.status === 409) {
          saveStatus.tags = 'conflict'
          error.value = '数据已被他人修改，请刷新后重试'
        } else {
          saveStatus.tags = 'error'
          error.value = e?.response?.data?.message || '保存失败'
        }
        throw e
      } finally {
        saving.value = false
      }
    })
  }

  async function saveProfile(updates) {
    if (!editorData.value) return
    const payload = {
      synopsis: updates.synopsis,
      outline: updates.outline,
      revision: editorData.value.profile?.revision ?? 0
    }
    return saveQueue.enqueue(async () => {
      saving.value = true
      saveStatus.profile = 'saving'
      try {
        const res = await contentProjectApi.updateProfile(editorData.value.projectId, payload)
        const profile = res?.data ?? res
        if (editorData.value) {
          editorData.value.profile = { ...editorData.value.profile, ...profile, revision: profile.revision }
          editorData.value.revision = profile.revision
        }
        saveStatus.profile = 'saved'
        dirtyFlags.profile = false
      } catch (e) {
        if (e?.response?.status === 409) {
          saveStatus.profile = 'conflict'
        } else {
          saveStatus.profile = 'error'
        }
        throw e
      } finally {
        saving.value = false
      }
    })
  }

  // ---- Cleanup ----

  function clearError() {
    error.value = null
  }

  function markDirty(section) {
    dirtyFlags[section] = true
  }

  function isDirty(section) {
    return !!dirtyFlags[section]
  }

  onBeforeUnmount(() => {
    // 离开保护由外层 TagEditor.vue 的 beforeRouteLeave 处理
  })

  return {
    // state
    loading,
    saving,
    error,
    editorData,
    dirtyFlags,
    saveStatus,
    dictionary,
    isReadOnly,
    // actions
    loadEditor,
    loadDictionary,
    saveTags,
    saveProfile,
    clearError,
    markDirty,
    isDirty
  }
}
