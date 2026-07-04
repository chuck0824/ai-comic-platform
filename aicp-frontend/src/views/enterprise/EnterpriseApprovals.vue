<template>
  <div class="enterprise-approvals">
    <h2>统一审批</h2>

    <el-tabs v-model="activeTab" @tab-change="fetchItems">
      <el-tab-pane label="待我处理" name="pending" />
      <el-tab-pane label="我发起的" name="mine" />
      <el-tab-pane label="已处理" name="processed" />
    </el-tabs>

    <el-row :gutter="12" class="filters">
      <el-col :span="6">
        <el-select v-model="filterType" placeholder="审批类型" clearable @change="fetchItems">
          <el-option label="采购" value="PURCHASE" />
          <el-option label="资产发布" value="ASSET_PUBLISH" />
          <el-option label="项目导出" value="PROJECT_EXPORT" />
        </el-select>
      </el-col>
    </el-row>

    <el-table :data="items" stripe v-loading="loading" class="approval-table">
      <el-table-column prop="sourceType" label="类型" width="100">
        <template #default="{ row }">{{ typeLabel(row.sourceType) }}</template>
      </el-table-column>
      <el-table-column prop="summary" label="摘要" min-width="200" />
      <el-table-column prop="requesterUserId" label="申请人" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" width="170" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" @click="viewDetail(row)">详情</el-button>
          <template v-if="row.status === 'PENDING' && activeTab === 'pending'">
            <el-button size="small" type="success" @click="decide(row, true)">批准</el-button>
            <el-button size="small" type="danger" @click="decide(row, false)">驳回</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="prev,pager,next"
      @current-change="fetchItems"
    />

    <!-- Detail drawer -->
    <el-drawer v-model="drawerVisible" title="审批详情" size="480px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="类型">{{ typeLabel(detail.sourceType) }}</el-descriptions-item>
          <el-descriptions-item label="摘要">{{ detail.summary }}</el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ centsToYuan(detail.amountCents) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.sourceVersion }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <!-- Decision dialog -->
    <el-dialog v-model="decisionDialog" title="审批决定" width="400px">
      <el-input v-model="decisionReason" type="textarea" :rows="3"
        :placeholder="decisionApprove ? '审批意见（可选）' : '驳回原因（必填）'" />
      <template #footer>
        <el-button @click="decisionDialog = false">取消</el-button>
        <el-button :type="decisionApprove ? 'success' : 'danger'" @click="confirmDecision">
          {{ decisionApprove ? '确认批准' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { enterpriseApi } from '@/api/enterprise'
import { ElMessage } from 'element-plus'

const activeTab = ref('pending')
const filterType = ref('')
const items = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

const drawerVisible = ref(false)
const detail = ref(null)

const decisionDialog = ref(false)
const decisionApprove = ref(true)
const decisionReason = ref('')
const decidingItem = ref(null)

function typeLabel(t) {
  return { PURCHASE: '采购', ASSET_PUBLISH: '资产发布', PROJECT_EXPORT: '项目导出' }[t] || t
}
function statusTag(s) {
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'info' }[s] || 'info'
}
function centsToYuan(c) { return c ? (c / 100).toFixed(2) : '0.00' }

async function fetchItems() {
  loading.value = true
  try {
    const res = await enterpriseApi.listApprovals({
      page: page.value, size: size.value,
      bucket: activeTab.value,
      sourceType: filterType.value || undefined
    })
    if (res?.data) {
      items.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch { ElMessage.error('加载审批列表失败') }
  finally { loading.value = false }
}

async function viewDetail(row) {
  try {
    const res = await enterpriseApi.getApprovalDetail(row.sourceType, row.sourceId)
    detail.value = res?.data
    drawerVisible.value = true
  } catch { ElMessage.error('加载详情失败') }
}

function decide(row, approve) {
  decidingItem.value = row
  decisionApprove.value = approve
  decisionReason.value = ''
  decisionDialog.value = true
}

async function confirmDecision() {
  if (!decisionApprove.value && !decisionReason.value.trim()) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  try {
    await enterpriseApi.submitApprovalDecision(
      decidingItem.value.sourceType, decidingItem.value.sourceId,
      {
        approved: decisionApprove.value,
        reason: decisionReason.value,
        expectedVersion: decidingItem.value.sourceVersion,
        idempotencyKey: `${decidingItem.value.sourceId}-${Date.now()}`
      }
    )
    ElMessage.success('操作成功')
    decisionDialog.value = false
    fetchItems()
  } catch (e) {
    if (e?.response?.status === 409) {
      ElMessage.warning('数据已变更，请刷新后重试')
    } else {
      ElMessage.error('操作失败')
    }
  }
}

onMounted(fetchItems)
</script>

<style scoped>
.approval-table { margin-top: 16px; }
.filters { margin-top: 12px; }
</style>
