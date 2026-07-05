<template>
  <div class="purchases-page">
    <h2 class="text-2xl font-bold mb-lg">我的订单</h2>

    <div v-if="orders.loading" class="flex flex-col items-center gap-sm py-xl">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span class="text-muted">加载中…</span>
    </div>

    <div v-else-if="orders.error" class="empty-state">
      <el-empty description="加载失败">
        <el-button size="small" @click="fetchOrders">重试</el-button>
      </el-empty>
    </div>

    <div v-else-if="orders.data.length === 0" class="empty-state">
      <el-empty description="暂无购买记录">
        <el-button size="small" @click="$router.push('/market')">去市场看看</el-button>
      </el-empty>
    </div>

    <div v-else class="orders-list">
      <div v-for="o in orders.data" :key="o.orderNo" class="card p-md mb-md">
        <div class="flex justify-between items-center">
          <div>
            <span class="font-semibold">{{ o.titleSnapshot || o.orderNo }}</span>
            <p class="text-sm text-muted">订单号：{{ o.orderNo }}</p>
          </div>
          <el-tag :type="orderStatusSeverity(o.status)" size="small">
            {{ orderStatusLabel(o.status) }}
          </el-tag>
        </div>
        <div class="flex gap-lg mt-sm text-sm text-muted">
          <span>金额：{{ formatCents(o.totalAmountCents) }}</span>
          <span v-if="o.paidAt">支付时间：{{ o.paidAt }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { useTradeMarket } from './useTradeMarket'
import { orderStatusLabel, orderStatusSeverity, formatCents } from './tradeState'

const { orders, fetchOrders } = useTradeMarket()

onMounted(() => {
  fetchOrders()
})
</script>

<style scoped>
.purchases-page { max-width: 1000px; }
</style>
