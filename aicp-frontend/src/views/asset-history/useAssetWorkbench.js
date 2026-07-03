import { ref, reactive, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { assetHistoryApi } from '@/api/assetHistory'
import { parseAssetHistoryQuery, serializeAssetHistoryState } from './assetHistoryState'
import { ElMessage } from 'element-plus'

/**
 * Reactive workbench page state — URL-driven, workspace-safe.
 */
export function useAssetWorkbench() {
  const route = useRoute()
  const router = useRouter()

  const state = reactive(parseAssetHistoryQuery(route.query))

  const projects = reactive({ items: [], loading: false, error: null })
  const records = reactive({ items: [], facets: null, total: 0, loading: false, error: null })
  const detail = reactive({ data: null, loading: false, error: null })
  const selectedUuids = ref(new Set())

  let requestSeq = 0

  async function fetchProjects() {
    projects.loading = true
    projects.error = null
    try {
      const res = await assetHistoryApi.listProjects()
      projects.items = res.data || []
    } catch (e) {
      projects.error = e.message || '加载项目失败'
    } finally {
      projects.loading = false
    }
  }

  async function fetchRecords() {
    const seq = ++requestSeq
    records.loading = true
    records.error = null
    try {
      const params = buildQueryParams()
      const res = await assetHistoryApi.queryRecords(params)
      if (seq !== requestSeq) return // stale
      records.items = res.data?.items || []
      records.facets = res.data?.facets || null
      records.total = res.data?.total || 0
    } catch (e) {
      if (seq !== requestSeq) return
      records.error = e.message || '加载记录失败'
    } finally {
      if (seq === requestSeq) records.loading = false
    }
  }

  async function fetchDetail(recordKind, recordUuid) {
    detail.loading = true
    detail.error = null
    try {
      const res = await assetHistoryApi.getDetail(recordKind, recordUuid)
      detail.data = res.data
    } catch (e) {
      detail.error = e.message || '加载详情失败'
    } finally {
      detail.loading = false
    }
  }

  function buildQueryParams() {
    const p = { page: state.page, pageSize: state.pageSize }
    if (state.projectUuid) p.project_uuid = state.projectUuid
    if (state.assetType) p.asset_type = state.assetType
    if (state.statuses?.length) p.status = state.statuses.join(',')
    if (state.mediaType) p.media_type = state.mediaType
    if (state.keyword) p.keyword = state.keyword
    if (state.sort) p.sort = state.sort
    if (state.collection) p.collection = state.collection
    if (state.scope) p.scope = state.scope
    return p
  }

  function syncQuery() {
    router.replace({ query: serializeAssetHistoryState({ ...state }) })
  }

  function setFilter(key, value) {
    state[key] = value
    state.page = 1
    syncQuery()
    fetchRecords()
  }

  function setPage(page) { state.page = page; syncQuery(); fetchRecords() }
  function setPageSize(size) { state.pageSize = size; state.page = 1; syncQuery(); fetchRecords() }
  function clearFilters() {
    Object.assign(state, parseAssetHistoryQuery({}))
    syncQuery()
    fetchRecords()
  }

  async function toggleFavorite(recordId) {
    try {
      const uuid = recordId.replace('asset-', '')
      await assetHistoryApi.favorite(uuid)
      ElMessage.success('已收藏')
      fetchRecords()
    } catch (e) { ElMessage.error(e.message || '操作失败') }
  }

  async function trashAssets(uuids) {
    try {
      await assetHistoryApi.batchOperate({ assetUuids: uuids, operation: 'TRASH' })
      ElMessage.success(`已删除 ${uuids.length} 项`)
      selectedUuids.value = new Set()
      fetchRecords()
    } catch (e) { ElMessage.error(e.message || '删除失败') }
  }

  async function restoreAssets(uuids) {
    try {
      await assetHistoryApi.batchOperate({ assetUuids: uuids, operation: 'RESTORE' })
      ElMessage.success(`已恢复 ${uuids.length} 项`)
      selectedUuids.value = new Set()
      fetchRecords()
    } catch (e) { ElMessage.error(e.message || '恢复失败') }
  }

  function openDetail(recordKind, recordUuid) {
    state.recordKind = recordKind
    state.recordUuid = recordUuid
    syncQuery()
    fetchDetail(recordKind, recordUuid)
  }

  function closeDetail() {
    state.recordKind = ''
    state.recordUuid = ''
    detail.data = null
    syncQuery()
  }

  // Debounced keyword search
  let keywordTimer = null
  watch(() => state.keyword, (val) => {
    clearTimeout(keywordTimer)
    keywordTimer = setTimeout(() => fetchRecords(), 300)
  })

  onBeforeUnmount(() => clearTimeout(keywordTimer))

  return {
    state, projects, records, detail, selectedUuids,
    fetchProjects, fetchRecords, fetchDetail,
    setFilter, setPage, setPageSize, clearFilters,
    toggleFavorite, trashAssets, restoreAssets,
    openDetail, closeDetail
  }
}
