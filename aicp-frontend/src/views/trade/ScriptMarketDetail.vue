<template>
  <div class="detail-page">
    <div v-if="detail.loading" class="flex flex-col items-center gap-sm py-xl">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span class="text-muted">加载详情…</span>
    </div>

    <div v-else-if="detail.error" class="empty-state">
      <el-empty description="加载失败">
        <el-button size="small" @click="fetchDetail(route.params.listingId)">重试</el-button>
      </el-empty>
    </div>

    <template v-else-if="detail.data">
      <div class="detail-header">
        <h2>{{ detail.data.title }}</h2>
        <p class="text-muted">@{{ detail.data.authorDisplayName }}</p>
        <p class="text-sm mt-sm">{{ detail.data.synopsis }}</p>
        <p class="text-sm text-muted mt-sm">
          共{{ detail.data.episodeCount }}集 · 试读前{{ detail.data.previewEpisodeCount }}集
        </p>
        <p v-if="detail.data.historicalNormalCount > 0" class="text-sm text-muted">
          历史普通授权：{{ detail.data.historicalNormalCount }} 笔
        </p>
      </div>

      <!-- License options -->
      <div class="card mt-lg" style="padding:18px">
        <h3 class="font-semibold mb-md">选择授权方案</h3>
        <div v-for="lic in detail.data.licenses" :key="lic.licenseType"
          class="flex justify-between items-center p-sm border rounded mb-sm">
          <div>
            <strong>{{ licenseLabel(lic.licenseType) }}</strong>
            <p class="text-sm text-muted">{{ lic.agreementSummary }}</p>
          </div>
          <div class="flex items-center gap-sm">
            <span class="font-bold text-lg">
              {{ lic.priceCents > 0 ? '¥' + (lic.priceCents / 100).toFixed(2) : '免费' }}
            </span>
            <el-button :type="lic.priceCents > 0 ? 'primary' : 'success'" size="small"
              :loading="order.loading" @click="doClaim(lic.licenseType)">
              {{ lic.priceCents > 0 ? '立即购买' : '免费领取' }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- Preview episodes -->
      <div class="card mt-lg" style="padding:18px" v-if="preview.data">
        <h3 class="font-semibold mb-md">试读内容</h3>
        <div v-for="ep in preview.data.episodes" :key="ep.episodeNumber"
          class="p-sm border rounded mb-sm">
          <strong>第{{ ep.episodeNumber }}集 {{ ep.title }}</strong>
          <p class="text-sm mt-sm">{{ ep.content }}</p>
        </div>
      </div>
    </template>

    <!-- Order result dialog -->
    <el-dialog v-model="resultVisible" title="订单结果" width="400px">
      <div v-if="orderResult" class="text-center">
        <el-icon v-if="orderResult.status === 'FULFILLED'" :size="40" color="#67c23a"><SuccessFilled /></el-icon>
        <p class="font-bold mt-md">订单号：{{ orderResult.orderNo }}</p>
        <p class="text-sm mt-sm">状态：{{ orderStatusLabel(orderResult.status) }}</p>
        <p class="text-sm">金额：{{ formatCents(orderResult.totalAmountCents) }}</p>
      </div>
      <template #footer>
        <el-button @click="resultVisible = false">关闭</el-button>
        <el-button v-if="orderResult?.status === 'FULFILLED'" type="primary"
          @click="$router.push('/trade/orders')">查看订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Loading, SuccessFilled } from '@element-plus/icons-vue'
import { useTradeMarket } from './useTradeMarket'
import { licenseLabel, orderStatusLabel, formatCents } from './tradeState'

const route = useRoute()
const { detail, preview, order, fetchDetail, fetchPreview, createOrder } = useTradeMarket()

const resultVisible = ref(false)
const orderResult = ref(null)

let claimSeq = 0

async function doClaim(licenseType) {
  claimSeq++
  const key = `claim-${route.params.listingId}-${licenseType}-${Date.now()}`
  const result = await createOrder(Number(route.params.listingId), licenseType, key)
  if (result) {
    orderResult.value = result
    resultVisible.value = true
  }
}

onMounted(() => {
  const id = route.params.listingId
  if (id) {
    fetchDetail(Number(id))
    fetchPreview(Number(id))
  }
})
</script>

<style scoped>
.detail-page { max-width: 900px; }
.detail-header { margin-bottom: 16px; }
</style>
