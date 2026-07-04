import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { enterpriseApi } from '@/api/enterprise'
import {
  commitWorkspaceSelection, personalFallback, clearWorkspaceCache
} from './workspaceState'

export const useWorkspaceStore = defineStore('workspace', () => {
  const items = ref([])
  const activeId = ref(localStorage.getItem('active_workspace_id') || '')
  const activeType = ref(localStorage.getItem('active_workspace_type') || 'personal')
  const membership = ref(null)
  const loading = ref(false)

  const isEnterprise = computed(() => activeType.value === 'enterprise')
  const isPersonal = computed(() => activeType.value === 'personal')

  /** Load all available workspaces for the current user. */
  async function loadWorkspaces() {
    loading.value = true
    try {
      const res = await enterpriseApi.getContext()
      // The context endpoint returns the current workspace context.
      // For a full list we rely on the 3001 workspaces endpoint via BFF.
      // Here we store the workspace list from member's available workspaces.
      if (res?.data) {
        membership.value = res.data
        activeId.value = res.data.workspaceId
        activeType.value = res.data.workspaceType
        localStorage.setItem('active_workspace_id', activeId.value)
        localStorage.setItem('active_workspace_type', activeType.value)
      }
    } catch (e) {
      console.error('[workspace] Failed to load workspaces:', e)
    } finally {
      loading.value = false
    }
  }

  /**
   * Switch to a different workspace.
   * Fetches membership first; preserves old workspace on failure.
   */
  async function selectWorkspace(id) {
    const previous = {
      workspaceId: activeId.value,
      workspaceType: activeType.value
    }
    loading.value = true
    try {
      localStorage.setItem('active_workspace_id', id)
      const res = await enterpriseApi.getContext()
      const result = commitWorkspaceSelection(previous, { workspaceId: id }, res?.data)
      activeId.value = result.workspaceId
      activeType.value = result.workspaceType
      membership.value = result
      localStorage.setItem('active_workspace_id', result.workspaceId)
      localStorage.setItem('active_workspace_type', result.workspaceType)
      clearWorkspaceCache()
    } catch (e) {
      // Restore previous workspace on failure
      activeId.value = previous.workspaceId
      activeType.value = previous.workspaceType
      localStorage.setItem('active_workspace_id', previous.workspaceId)
      localStorage.setItem('active_workspace_type', previous.workspaceType)
      console.error('[workspace] Switch failed, restored previous:', e)
    } finally {
      loading.value = false
    }
  }

  /**
   * Fall back to personal workspace when enterprise membership is revoked.
   */
  function fallbackToPersonal(userId) {
    const fallback = personalFallback(userId)
    activeId.value = fallback.workspaceId
    activeType.value = 'personal'
    membership.value = fallback
    localStorage.setItem('active_workspace_id', fallback.workspaceId)
    localStorage.setItem('active_workspace_type', 'personal')
    clearWorkspaceCache()
  }

  return {
    items, activeId, activeType, membership, loading,
    isEnterprise, isPersonal,
    loadWorkspaces, selectWorkspace, fallbackToPersonal
  }
})
