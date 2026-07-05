<template>
  <div class="seller-page">
    <h2 class="text-2xl font-bold mb-lg">卖家中心</h2>

    <div class="card p-lg mb-lg" v-if="overview.data">
      <div class="flex gap-lg">
        <div><span class="text-muted">总收入</span><p class="text-xl font-bold">{{ formatCents(overview.data.totalRevenueCents) }}</p></div>
        <div><span class="text-muted">冻结中</span><p class="text-xl font-bold text-warning">{{ formatCents(overview.data.frozenRevenueCents) }}</p></div>
        <div><span class="text-muted">可提现</span><p class="text-xl font-bold text-success">{{ formatCents(overview.data.availableRevenueCents) }}</p></div>
        <div><span class="text-muted">销量</span><p class="text-xl font-bold">{{ overview.data.totalOrders }} 单</p></div>
      </div>
    </div>

    <div class="card p-lg mb-lg">
      <div class="flex justify-between items-center mb-md">
        <h3 class="font-semibold">我的上架</h3>
        <el-button type="primary" size="small" @click="$router.push('/trade/seller/listings/new')">新建上架</el-button>
      </div>
      <el-table :data="listings.data || []" v-loading="listings.loading" stripe>
        <el-table-column prop="title" label="剧本" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag size="small">{{ listingStatusLabel(row.listingStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="historicalNormalCount" label="销量" width="80" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button v-if="row.listingStatus === 'LISTED'" size="small" type="danger" @click="unlist(row.id)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="card p-lg">
      <h3 class="font-semibold mb-md">销售记录</h3>
      <el-table :data="sales.data || []" v-loading="sales.loading" stripe>
        <el-table-column prop="orderNo" label="订单号" width="140" />
        <el-table-column prop="title" label="剧本" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag size="small" :type="orderStatusSeverity(row.status)">{{ orderStatusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="收入" width="100">
          <template #default="{ row }">{{ formatCents(row.sellerIncomeCents) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { tradeApi } from '@/api/trade'
import { formatCents, orderStatusLabel, orderStatusSeverity, listingStatusLabel } from './tradeState'

const overview = reactive({ data: null, loading: false })
const listings = reactive({ data: [], loading: false })
const sales = reactive({ data: [], loading: false })

async function unlist(id) {
  await tradeApi.unlistListing(id)
  fetchListings()
}

async function fetchOverview() {
  overview.loading = true
  try { const r = await tradeApi.getSellerOverview(); overview.data = r.data }
  catch { overview.data = null }
  finally { overview.loading = false }
}

async function fetchListings() {
  listings.loading = true
  try { const r = await tradeApi.getMyListings(); listings.data = r.data || [] }
  catch { listings.data = [] }
  finally { listings.loading = false }
}

async function fetchSales() {
  sales.loading = true
  try { const r = await tradeApi.getSellerOrders(); sales.data = r.data || [] }
  catch { sales.data = [] }
  finally { sales.loading = false }
}

onMounted(() => { fetchOverview(); fetchListings(); fetchSales() })
</script>
