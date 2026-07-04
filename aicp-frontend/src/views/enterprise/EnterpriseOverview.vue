<template>
  <div class="enterprise-overview">
    <h2>{{ title }}</h2>

    <div class="overview-grid">
      <!-- Member count card -->
      <el-card class="metric-card">
        <template #header><span>成员</span></template>
        <div class="metric-value">{{ memberCount }} / {{ memberLimit || '∞' }}</div>
      </el-card>

      <!-- Balance card -->
      <el-card class="metric-card">
        <template #header><span>共享余额</span></template>
        <div class="metric-value">¥{{ balanceYuan }}</div>
        <div class="metric-meta" v-if="balanceUpdatedAt">更新于 {{ balanceUpdatedAt }}</div>
      </el-card>

      <!-- Budget card -->
      <el-card class="metric-card" v-if="canViewBudget">
        <template #header><span>采购预算可用</span></template>
        <div class="metric-value">¥{{ budgetAvailableYuan }}</div>
      </el-card>

      <!-- Pending approvals -->
      <el-card class="metric-card" v-if="canApprove">
        <template #header><span>待审批</span></template>
        <div class="metric-value">{{ pendingApprovals }}</div>
      </el-card>
    </div>

    <!-- Quick actions -->
    <el-card class="actions-card" v-if="canManageOrg">
      <template #header><span>管理入口</span></template>
      <el-space>
        <el-button type="primary" @click="$router.push('/enterprise/organization')">组织管理</el-button>
        <el-button v-if="canManageBudget" @click="$router.push('/enterprise/budgets')">预算设置</el-button>
      </el-space>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useWorkspaceStore } from '@/stores/workspace'
import { enterpriseApi } from '@/api/enterprise'

const workspace = useWorkspaceStore()

const title = computed(() => workspace.isEnterprise ? '企业概览' : '个人空间')
const canManageOrg = computed(() => workspace.membership?.allowedActions?.canManageOrg)
const canViewBudget = computed(() => workspace.membership?.allowedActions?.canViewBudget)
const canManageBudget = computed(() => workspace.membership?.allowedActions?.canManageBudget)
const canApprove = computed(() =>
  workspace.membership?.allowedActions?.canApprovePurchase ||
  workspace.membership?.allowedActions?.canApproveAssetPublish ||
  workspace.membership?.allowedActions?.canApproveExport
)

const memberCount = ref(0)
const memberLimit = ref(0)
const balanceYuan = ref('0.00')
const balanceUpdatedAt = ref('')
const budgetAvailableYuan = ref('0.00')
const pendingApprovals = ref(0)

function centsToYuan(cents) {
  if (!cents) return '0.00'
  return (Number(cents) / 100).toFixed(2)
}

onMounted(async () => {
  try {
    const res = await enterpriseApi.getBillingSummary()
    if (res?.data) {
      balanceYuan.value = centsToYuan(res.data.available_cents)
      balanceUpdatedAt.value = res.data.updated_at || ''
    }
  } catch { /* card shows default */ }
})
</script>

<style scoped>
.enterprise-overview h2 {
  margin-bottom: 24px;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-color-primary);
}
.metric-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.actions-card {
  max-width: 600px;
}
</style>
