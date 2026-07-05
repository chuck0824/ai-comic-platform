import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { tradeApi } from '@/api/trade'

/**
 * Composable for market search, detail, and order creation state.
 * Follows the useAssetMarket pattern.
 */
export function useTradeMarket() {
  const route = useRoute()
  const router = useRouter()

  // Market listing state
  const listings = reactive({ data: null, loading: false, error: null })
  const detail = reactive({ data: null, loading: false, error: null })
  const preview = reactive({ data: null, loading: false, error: null })

  // Order state
  const order = reactive({ data: null, loading: false, error: null })
  const orders = reactive({ data: [], loading: false, error: null })

  // Filters synced with URL query
  const filters = reactive({
    keyword: route.query.keyword || '',
    genre: route.query.genre || '',
    plot: route.query.plot || '',
    tone: route.query.tone || '',
    setting: route.query.setting || '',
    licenseType: route.query.licenseType || '',
    sort: route.query.sort || 'latest',
    page: Number(route.query.page) || 1,
    pageSize: Number(route.query.pageSize) || 20
  })

  let requestSeq = 0

  /** Fetch market listings with current filters. */
  async function fetchListings() {
    listings.loading = true
    listings.error = null
    const seq = ++requestSeq
    try {
      const res = await tradeApi.searchListings({ ...filters })
      if (seq === requestSeq) {
        listings.data = res.data
      }
    } catch (e) {
      if (seq === requestSeq) {
        listings.error = e.message || '加载失败'
        listings.data = null
      }
    } finally {
      if (seq === requestSeq) listings.loading = false
    }
  }

  /** Fetch listing detail by ID. */
  async function fetchDetail(listingId) {
    detail.loading = true
    detail.error = null
    try {
      const res = await tradeApi.getListing(listingId)
      detail.data = res.data
    } catch (e) {
      detail.error = e.message || '加载详情失败'
      detail.data = null
    } finally {
      detail.loading = false
    }
  }

  /** Fetch preview episodes. */
  async function fetchPreview(listingId) {
    preview.loading = true
    preview.error = null
    try {
      const res = await tradeApi.getPreview(listingId)
      preview.data = res.data
    } catch (e) {
      preview.error = e.message || '加载试读失败'
      preview.data = null
    } finally {
      preview.loading = false
    }
  }

  /** Create an order (free auto-fulfills). */
  async function createOrder(listingId, licenseType, idempotencyKey) {
    order.loading = true
    order.error = null
    try {
      const res = await tradeApi.createOrder({
        listingId,
        licenseType,
        idempotencyKey
      })
      order.data = res.data
      return res.data
    } catch (e) {
      order.error = e.message || '创建订单失败'
      order.data = null
      return null
    } finally {
      order.loading = false
    }
  }

  /** Fetch buyer's orders. */
  async function fetchOrders() {
    orders.loading = true
    orders.error = null
    try {
      const res = await tradeApi.getOrders()
      orders.data = res.data || []
    } catch (e) {
      orders.error = e.message || '加载订单失败'
      orders.data = []
    } finally {
      orders.loading = false
    }
  }

  /** Update URL query params without full navigation. */
  function syncQuery(newFilters) {
    Object.assign(filters, newFilters)
    router.replace({ query: { ...filters } })
  }

  return {
    listings, detail, preview, order, orders, filters,
    fetchListings, fetchDetail, fetchPreview,
    createOrder, fetchOrders, syncQuery
  }
}
