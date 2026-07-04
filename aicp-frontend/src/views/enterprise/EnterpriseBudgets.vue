<template>
  <div class="enterprise-budgets">
    <h2>预算与用量</h2>

    <!-- Budget cards -->
    <h3>采购预算</h3>
    <el-table :data="budgets" stripe v-loading="loading" class="budget-table">
      <el-table-column prop="subjectType" label="范围" width="100" />
      <el-table-column prop="subjectId" label="主体" width="120" />
      <el-table-column prop="periodMonth" label="月份" width="100" />
      <el-table-column label="总额度" width="130">
        <template #default="{ row }">¥{{ centsToYuan(row.amountCents) }}</template>
      </el-table-column>
      <el-table-column label="单笔上限" width="130">
        <template #default="{ row }">¥{{ centsToYuan(row.singleLimitCents) }}</template>
      </el-table-column>
      <el-table-column label="已预占" width="120">
        <template #default="{ row }">¥{{ centsToYuan(row.reservedCents) }}</template>
      </el-table-column>
      <el-table-column label="已消费" width="120">
        <template #default="{ row }">¥{{ centsToYuan(row.consumedCents) }}</template>
      </el-table-column>
      <el-table-column label="可用" width="130">
        <template #default="{ row }">
          <span :class="{ 'text-danger': available(row) <= 0 }">
            ¥{{ centsToYuan(available(row)) }}
          </span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="budgetTotal > size"
      v-model:current-page="page"
      :page-size="size"
      :total="budgetTotal"
      layout="prev,pager,next"
      @current-change="fetchBudgets"
    />

    <!-- AI Usage (from 3001) -->
    <h3 style="margin-top: 32px;">AI 用量</h3>
    <el-card class="usage-card">
      <el-row :gutter="24">
        <el-col :span="8">
          <div class="metric-label">共享余额</div>
          <div class="metric-value">¥{{ centsToYuan(billing?.available_cents) }}</div>
        </el-col>
        <el-col :span="8">
          <div class="metric-label">冻结金额</div>
          <div class="metric-value">¥{{ centsToYuan(billing?.frozen_cents) }}</div>
        </el-col>
        <el-col :span="8">
          <el-button type="primary" disabled>充值（前往 3001）</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- Budget entries -->
    <h3 style="margin-top: 32px;">预算流水</h3>
    <el-table :data="entries" stripe v-loading="entryLoading" size="small">
      <el-table-column prop="entryType" label="类型" width="100">
        <template #default="{ row }">{{ entryLabel(row.entryType) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="130">
        <template #default="{ row }">
          <span :class="row.amountCents > 0 ? 'text-success' : 'text-danger'">
            {{ row.amountCents > 0 ? '+' : '' }}¥{{ centsToYuan(Math.abs(row.amountCents)) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="sourceType" label="来源" width="120" />
      <el-table-column prop="idempotencyKey" label="幂等键" min-width="200" />
      <el-table-column prop="createdAt" label="时间" width="170" />
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { enterpriseApi } from '@/api/enterprise'

const budgets = ref([])
const entries = ref([])
const billing = ref(null)
const loading = ref(false)
const entryLoading = ref(false)
const page = ref(1)
const size = ref(20)
const budgetTotal = ref(0)

function centsToYuan(c) { return c ? (c / 100).toFixed(2) : '0.00' }
function available(row) {
  return Math.max(0, (row.amountCents || 0) - (row.reservedCents || 0) - (row.consumedCents || 0))
}
function entryLabel(t) {
  return { RESERVE: '预占', RELEASE: '释放', CONSUME: '消费', REVERSE: '冲回' }[t] || t
}

async function fetchBudgets() {
  loading.value = true
  try {
    const res = await enterpriseApi.listBudgets()
    if (res?.data) {
      budgets.value = Array.isArray(res.data) ? res.data : (res.data.records || [])
      budgetTotal.value = res.data.total || 0
    }
  } catch {} finally { loading.value = false }
}

async function fetchEntries() {
  entryLoading.value = true
  try {
    const res = await enterpriseApi.listBudgetEntries({ page: 1, size: 50 })
    if (res?.data) {
      entries.value = res.data.records || []
    }
  } catch {} finally { entryLoading.value = false }
}

async function fetchBilling() {
  try {
    const res = await enterpriseApi.getBillingSummary()
    billing.value = res?.data
  } catch {}
}

onMounted(() => {
  fetchBudgets()
  fetchEntries()
  fetchBilling()
})
</script>

<style scoped>
.budget-table { margin-bottom: 16px; }
.usage-card { max-width: 600px; }
.metric-label { font-size: 13px; color: var(--el-text-color-secondary); }
.metric-value { font-size: 22px; font-weight: 700; color: var(--el-color-primary); margin-top: 4px; }
.text-danger { color: var(--el-color-danger); }
.text-success { color: var(--el-color-success); }
</style>
