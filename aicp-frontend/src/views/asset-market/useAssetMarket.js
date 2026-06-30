import { ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as api from '@/api/asset'

/**
 * Pinia-style composable for AI Asset Market state management.
 * Owns separate reactive states for listings, detail, library, and publish requests.
 * Synchronizes filters with URL query params for shareable/bookmarkable state.
 */
export function useAssetMarket() {
  const route = useRoute()
  const router = useRouter()

  // ---- State ----
  const listings = reactive({ data: null, loading: false, error: null })
  const detail = reactive({ data: null, loading: false, error: null })
  const library = reactive({ data: null, loading: false, error: null })
  const publishRequests = reactive({ data: null, loading: false, error: null })

  const filters = reactive({
    keyword: route.query.keyword ?? '',
    type: route.query.type ?? '',
    sort: route.query.sort ?? 'latest',
    page: Number(route.query.page) || 1,
    pageSize: Number(route.query.page_size) || 20
  })

  let requestSequence = 0

  // ---- Actions ----
  async function fetchListings(params = {}) {
    const seq = ++requestSequence
    Object.assign(filters, params)
    listings.loading = true
    listings.error = null
    try {
      const query = {
        keyword: filters.keyword || undefined,
        type: filters.type || undefined,
        sort: filters.sort,
        page: filters.page,
        page_size: filters.pageSize
      }
      const res = await api.listMarket(query)
      if (seq === requestSequence) {
        listings.data = res.data?.data
        syncQuery()
      }
    } catch (e) {
      if (seq === requestSequence) listings.error = e.response?.data?.message || '加载失败'
    } finally {
      if (seq === requestSequence) listings.loading = false
    }
  }

  async function fetchDetail(listingId) {
    detail.loading = true
    detail.error = null
    try {
      const res = await api.getListingDetail(listingId)
      detail.data = res.data?.data
    } catch (e) {
      detail.error = e.response?.data?.message || '加载失败'
    } finally {
      detail.loading = false
    }
  }

  async function claimListing(id) {
    const res = await api.claimListing(id)
    return res.data?.data
  }

  async function favoriteListing(id) {
    await api.favoriteListing(id)
  }

  async function unfavoriteListing(id) {
    await api.unfavoriteListing(id)
  }

  async function fetchLibrary(params = {}) {
    library.loading = true
    library.error = null
    try {
      const res = await api.listLibrary(params)
      library.data = res.data?.data
    } catch (e) {
      library.error = e.response?.data?.message || '加载失败'
    } finally {
      library.loading = false
    }
  }

  async function createAsset(body) {
    const res = await api.createAsset(body)
    return res.data?.data
  }

  async function editAsset(id, body) {
    const res = await api.editLibraryAsset(id, body)
    return res.data?.data
  }

  async function publishAsset(id, body) {
    const res = await api.publishAsset(id, body)
    return res.data?.data
  }

  async function unlistAsset(id, rowVersion) {
    await api.unlistAsset(id, rowVersion)
  }

  async function archiveAsset(id, rowVersion) {
    await api.archiveAsset(id, rowVersion)
  }

  async function requestPublish(id, body) {
    const res = await api.requestPublish(id, body)
    return res.data?.data
  }

  async function fetchPublishRequests(params = {}) {
    publishRequests.loading = true
    publishRequests.error = null
    try {
      const res = await api.listPublishRequests(params)
      publishRequests.data = res.data?.data
    } catch (e) {
      publishRequests.error = e.response?.data?.message || '加载失败'
    } finally {
      publishRequests.loading = false
    }
  }

  async function approveRequest(id, body) {
    const res = await api.approveRequest(id, body)
    return res.data?.data
  }

  async function rejectRequest(id, body) {
    const res = await api.rejectRequest(id, body)
    return res.data?.data
  }

  async function cancelRequest(id) {
    await api.cancelRequest(id)
  }

  async function applyAsset(id, body) {
    const res = await api.applyAsset(id, body)
    return res.data?.data
  }

  async function undoApplication(id, body) {
    await api.undoApplication(id, body)
  }

  // ---- URL sync ----
  function syncQuery() {
    const q = {}
    if (filters.keyword) q.keyword = filters.keyword
    if (filters.type) q.type = filters.type
    if (filters.sort && filters.sort !== 'latest') q.sort = filters.sort
    if (filters.page > 1) q.page = filters.page
    router.replace({ query: { ...route.query, ...q } })
  }

  return {
    listings, detail, library, publishRequests, filters,
    fetchListings, fetchDetail, claimListing, favoriteListing, unfavoriteListing,
    fetchLibrary, createAsset, editAsset, publishAsset, unlistAsset, archiveAsset,
    requestPublish, fetchPublishRequests, approveRequest, rejectRequest, cancelRequest,
    applyAsset, undoApplication
  }
}
