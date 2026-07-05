<template>
  <div class="topup-page">
    <h2 class="text-2xl font-bold mb-lg">充值</h2>

    <div class="card p-lg mb-lg">
      <p class="text-muted">当前余额：<strong>{{ formatCents(balanceCents) }}</strong></p>
    </div>

    <div class="card p-lg mb-lg">
      <h3 class="font-semibold mb-md">选择充值金额</h3>
      <div class="flex gap-sm flex-wrap">
        <el-button v-for="opt in amountOptions" :key="opt.cents"
          :type="selectedAmount === opt.cents ? 'primary' : 'default'"
          @click="selectedAmount = opt.cents">
          ¥{{ (opt.cents / 100).toFixed(2) }}
        </el-button>
      </div>
    </div>

    <div class="card p-lg mb-lg">
      <h3 class="font-semibold mb-md">支付方式</h3>
      <el-radio-group v-model="channel">
        <el-radio value="epay">微信/支付宝</el-radio>
        <el-radio value="stripe">Stripe</el-radio>
      </el-radio-group>
    </div>

    <div class="flex justify-end gap-md">
      <el-button @click="goBack">返回</el-button>
      <el-button type="primary" :loading="loading" @click="doTopUp">
        立即充值
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tradeApi } from '@/api/trade'
import { formatCents, safeReturnPath } from './tradeState'

const router = useRouter()
const balanceCents = ref(0)
const selectedAmount = ref(1000)
const channel = ref('epay')
const loading = ref(false)

const amountOptions = [
  { cents: 500 }, { cents: 1000 }, { cents: 2000 },
  { cents: 5000 }, { cents: 10000 }, { cents: 20000 }
]

async function fetchBalance() {
  try {
    const res = await tradeApi.getWalletBalance()
    balanceCents.value = res.data?.availableCents || 0
  } catch { balanceCents.value = 0 }
}

function goBack() {
  const ret = safeReturnPath(router.currentRoute.value.query.return_to)
  router.push(ret)
}

async function doTopUp() {
  loading.value = true
  try {
    await tradeApi.createTopUp({ amount_cents: selectedAmount.value, channel: channel.value })
    ElMessage.success('充值请求已提交，请完成支付')
    await fetchBalance()
  } catch (e) {
    ElMessage.error(e.message || '充值失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchBalance)
</script>

<style scoped>
.topup-page { max-width: 500px; }
</style>
