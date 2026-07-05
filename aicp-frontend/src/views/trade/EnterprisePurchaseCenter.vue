<template>
  <div class="enterprise-page">
    <h2 class="text-2xl font-bold mb-lg">企业采购中心</h2>

    <el-tabs v-model="tab">
      <el-tab-pane label="待审批" name="pending">
        <el-table :data="pendingList" stripe v-loading="loading">
          <el-table-column prop="requesterUserId" label="申请人" width="100" />
          <el-table-column label="授权" width="100">
            <template #default="{ row }">{{ licenseLabel(row.licenseType) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="120">
            <template #default="{ row }">{{ formatCents(row.amountCents) }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="理由" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="doApprove(row.id)">批准</el-button>
              <el-button size="small" type="danger" @click="doReject(row.id)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="已处理" name="done">
        <el-table :data="doneList" stripe>
          <el-table-column prop="requesterUserId" label="申请人" width="100" />
          <el-table-column label="授权" width="100">
            <template #default="{ row }">{{ licenseLabel(row.licenseType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag size="small" :type="row.status === 'APPROVED' ? 'success' : 'danger'">{{ row.status === 'APPROVED' ? '已批准' : '已驳回' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="approvalComment" label="批注" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tradeApi } from '@/api/trade'
import { formatCents, licenseLabel } from './tradeState'

const tab = ref('pending')
const loading = ref(false)
const items = ref([])

const pendingList = computed(() => items.value.filter(i => i.status === 'PENDING_APPROVAL'))
const doneList = computed(() => items.value.filter(i => i.status !== 'PENDING_APPROVAL'))

async function fetchItems() {
  loading.value = true
  try { const r = await tradeApi.submitPurchaseRequest({}); items.value = r.data || [] }
  catch { items.value = [] }
  finally { loading.value = false }
}

async function doApprove(id) {
  await tradeApi.approvePurchaseRequest(id, { approved: true, comment: '批准' })
  ElMessage.success('已批准')
  fetchItems()
}

async function doReject(id) {
  const { value } = await ElMessageBox.prompt('驳回理由', '驳回', { type: 'warning' })
  await tradeApi.rejectPurchaseRequest(id, { approved: false, comment: value || '驳回' })
  ElMessage.success('已驳回')
  fetchItems()
}

onMounted(fetchItems)
</script>
