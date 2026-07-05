<template>
  <div class="checkout-page">
    <h2 class="text-2xl font-bold mb-lg">确认订单</h2>

    <div v-if="detail.loading" class="flex flex-col items-center gap-sm py-xl">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span class="text-muted">加载中…</span>
    </div>

    <template v-else-if="detail.data">
      <div class="card p-lg mb-lg">
        <h3 class="font-semibold">{{ detail.data.title }}</h3>
        <p class="text-sm text-muted mt-sm">作者：{{ detail.data.authorDisplayName }}</p>
        <p class="text-sm text-muted">历史普通授权：{{ detail.data.historicalNormalCount }} 笔</p>
      </div>

      <div class="card p-lg mb-lg">
        <h3 class="font-semibold mb-md">授权方案</h3>
        <div v-for="lic in detail.data.licenses" :key="lic.licenseType"
          class="p-sm border rounded mb-sm"
          :class="{ 'border-accent': selectedLicense === lic.licenseType }"
          @click="selectedLicense = lic.licenseType"
          style="cursor:pointer">
          <div class="flex justify-between items-center">
            <strong>{{ licenseLabel(lic.licenseType) }}</strong>
            <span class="font-bold text-lg">{{ formatCents(lic.priceCents) }}</span>
          </div>
          <p class="text-sm text-muted mt-xs">{{ lic.agreementSummary || '标准授权条款' }}</p>
        </div>
      </div>

      <div class="card p-lg mb-lg">
        <div class="flex justify-between items-center">
          <span class="font-semibold">钱包余额</span>
          <span class="font-bold">{{ formatCents(balanceCents) }}</span>
        </div>
        <div v-if="selectedPrice > balanceCents" class="mt-md">
          <el-alert type="warning" :closable="false"
            title="余额不足，请先充值" />
          <el-button type="warning" class="mt-sm" @click="goTopUp">
            去充值
          </el-button>
        </div>
      </div>

      <div class="flex justify-end gap-md">
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" :disabled="!canPay" :loading="order.loading"
          @click="doPay">
          {{ selectedPrice > 0 ? '确认支付 ¥' + (selectedPrice / 100).toFixed(2) : '免费领取' }}
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { tradeApi } from '@/api/trade'
import { formatCents, licenseLabel, topUpReturnPath } from './tradeState'

const route = useRoute()
const router = useRouter()

const listingId = Number(route.params.listingId)
const detail = ref({ data: null, loading: false, error: null })
const order = ref({ loading: false, data: null, error: null })
const selectedLicense = ref('FREE')
const balanceCents = ref(0)

const selectedPrice = computed(() => {
  if (!detail.value.data) return 0
  const lic = detail.value.data.licenses?.find(l => l.licenseType === selectedLicense.value)
  return lic ? lic.priceCents : 0
})

const canPay = computed(() => {
  if (selectedPrice.value === 0) return true
  return balanceCents.value >= selectedPrice.value
})

async function fetchDetail() {
  detail.value.loading = true
  try {
    const res = await tradeApi.getListing(listingId)
    detail.value.data = res.data
  } catch (e) {
    detail.value.error = e.message
  } finally {
    detail.value.loading = false
  }
}

async function fetchBalance() {
  try {
    const res = await tradeApi.getWalletBalance()
    balanceCents.value = res.data?.availableCents || 0
  } catch {
    balanceCents.value = 0
  }
}

function goTopUp() {
  router.push('/wallet/topup')
}

async function doPay() {
  order.value.loading = true
  try {
    const key = `pay-${listingId}-${selectedLicense.value}-${Date.now()}`
    const orderRes = await tradeApi.createOrder({
      listingId,
      licenseType: selectedLicense.value,
      idempotencyKey: key
    })

    if (selectedPrice.value > 0 && orderRes.data.status !== 'FULFILLED') {
      await tradeApi.payOrder(orderRes.data.orderNo, { paymentMethod: 'wallet' })
    }

    ElMessage.success('交易完成！')
    router.push(`/trade/orders/${orderRes.data.orderNo}`)
  } catch (e) {
    ElMessage.error(e.message || '支付失败')
  } finally {
    order.value.loading = false
  }
}

onMounted(() => {
  fetchDetail()
  fetchBalance()
})
</script>

<style scoped>
.checkout-page { max-width: 700px; }
.border-accent { border-color: var(--accent-border, #409eff); }
</style>
