<template>
  <div class="sop-workspace">
    <div class="page-header">
      <el-button text @click="$router.push('/sop')">
        <el-icon><ArrowLeft /></el-icon> 返回项目列表
      </el-button>
      <h2>SOP 生产工作台</h2>
      <div class="header-actions">
        <el-button type="primary" :loading="checkLoading" @click="runCheck">重新检查</el-button>
        <el-button type="warning" :loading="gateLoading" @click="evaluateGate">评估准入</el-button>
      </div>
    </div>

    <!-- Loading -->
    <el-skeleton v-if="pageLoading" :rows="4" animated />

    <!-- Error -->
    <el-result v-else-if="pageError" icon="error" title="加载失败" :sub-title="pageError">
      <template #extra>
        <el-button type="primary" @click="loadData">重试</el-button>
      </template>
    </el-result>

    <!-- Content -->
    <template v-else>
      <SopSummaryCards :summary="summary" />

      <el-tabs v-model="activeTab">
        <el-tab-pane label="检查结果" name="results">
          <SopCheckTable
            :results="results"
            :loading="resultsLoading"
            @create-work-order="handleCreateWorkOrder"
          />
        </el-tab-pane>
        <el-tab-pane label="返工工单" name="orders">
          <SopWorkOrderTable
            :orders="workOrders"
            :loading="ordersLoading"
            @transition="handleTransition"
            @review="handleReview"
          />
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- Create work order dialog -->
    <el-dialog v-model="createDialogVisible" title="创建返工工单" width="400px">
      <el-form label-width="80px">
        <el-form-item label="责任岗位">
          <el-input v-model="newOrderRole" placeholder="如 director, ai_artist" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateWorkOrder">创建</el-button>
      </template>
    </el-dialog>

    <!-- Transition dialog -->
    <el-dialog v-model="transitionDialogVisible" title="工单操作" width="400px">
      <el-form label-width="80px">
        <el-form-item label="备注">
          <el-input v-model="transitionNote" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transitionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmTransition">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { sopApi } from '@/api/sop.js'
import SopSummaryCards from './SopSummaryCards.vue'
import SopCheckTable from './SopCheckTable.vue'
import SopWorkOrderTable from './SopWorkOrderTable.vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const projectId = computed(() => route.params.projectId)

// Page state
const pageLoading = ref(true)
const pageError = ref('')
const activeTab = ref('results')

// Data
const summary = ref({})
const latestRun = ref(null)
const results = ref([])
const workOrders = ref([])

// Loading flags
const checkLoading = ref(false)
const gateLoading = ref(false)
const resultsLoading = ref(false)
const ordersLoading = ref(false)

// Dialogs
const createDialogVisible = ref(false)
const selectedResult = ref(null)
const newOrderRole = ref('')
const transitionDialogVisible = ref(false)
const transitionTarget = ref(null)
const transitionToStatus = ref('')
const transitionNote = ref('')

async function loadData() {
  pageLoading.value = true
  pageError.value = ''
  try {
    const [summaryRes, checksRes, ordersRes] = await Promise.allSettled([
      sopApi.getSummary(projectId.value),
      sopApi.listChecks(projectId.value, { page: 1, size: 1 }),
      sopApi.listWorkOrders(projectId.value, { page: 1, size: 50 }),
    ])
    if (summaryRes.status === 'fulfilled') {
      summary.value = summaryRes.value.data?.data || {}
    }
    if (checksRes.status === 'fulfilled') {
      const items = checksRes.value.data?.data?.items || []
      latestRun.value = items[0] || null
      if (latestRun.value) {
        await loadResults(latestRun.value.id)
      }
    }
    if (ordersRes.status === 'fulfilled') {
      workOrders.value = ordersRes.value.data?.data?.items || []
    }
  } catch (e) {
    pageError.value = e?.response?.data?.message || '加载失败'
  } finally {
    pageLoading.value = false
  }
}

async function loadResults(runId) {
  resultsLoading.value = true
  try {
    const res = await sopApi.getCheckReport(projectId.value, runId)
    results.value = res.data?.data?.results || []
  } catch (e) {
    results.value = []
  } finally {
    resultsLoading.value = false
  }
}

async function runCheck() {
  checkLoading.value = true
  try {
    const res = await sopApi.runCheck(projectId.value, { triggerType: 'MANUAL' })
    ElMessage.success('检查完成')
    await loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '检查失败')
  } finally {
    checkLoading.value = false
  }
}

async function evaluateGate() {
  gateLoading.value = true
  try {
    const idempotencyKey = crypto.randomUUID ? crypto.randomUUID() : Date.now().toString()
    const res = await sopApi.evaluateGate(projectId.value, 'production-admission', {
      gateType: 'PRODUCTION_ADMISSION',
      idempotencyKey,
    })
    const data = res.data?.data || {}
    if (data.allowed) {
      ElMessage.success('准入通过')
    } else {
      ElMessage.warning(`准入被阻断：${data.blockerCount} 项问题`)
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '准入评估失败')
  } finally {
    gateLoading.value = false
  }
}

// Work order handlers
function handleCreateWorkOrder(result) {
  selectedResult.value = result
  newOrderRole.value = ''
  createDialogVisible.value = true
}

async function confirmCreateWorkOrder() {
  try {
    await sopApi.createWorkOrder(projectId.value, {
      resultId: selectedResult.value.id,
      responsibleRole: newOrderRole.value || 'director',
    })
    ElMessage.success('工单已创建')
    createDialogVisible.value = false
    await loadWorkOrders()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  }
}

function handleTransition(order, toStatus) {
  transitionTarget.value = order
  transitionToStatus.value = toStatus
  transitionNote.value = ''
  transitionDialogVisible.value = true
}

async function confirmTransition() {
  try {
    await sopApi.transitionWorkOrder(projectId.value, transitionTarget.value.id, {
      toStatus: transitionToStatus.value,
      note: transitionNote.value,
    })
    ElMessage.success('操作成功')
    transitionDialogVisible.value = false
    await loadWorkOrders()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function handleReview(order, approved) {
  try {
    await sopApi.reviewWorkOrder(projectId.value, order.id, {
      approved,
      note: approved ? '审核通过' : '审核驳回，需重新修复',
    })
    ElMessage.success(approved ? '已通过' : '已驳回')
    await loadWorkOrders()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '审核操作失败')
  }
}

async function loadWorkOrders() {
  ordersLoading.value = true
  try {
    const res = await sopApi.listWorkOrders(projectId.value, { page: 1, size: 50 })
    workOrders.value = res.data?.data?.items || []
  } finally {
    ordersLoading.value = false
  }
}

watch(() => projectId.value, () => {
  if (projectId.value) loadData()
})

onMounted(loadData)
</script>

<style scoped>
.sop-workspace {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  flex: 1;
}
.header-actions {
  display: flex;
  gap: 8px;
}
</style>
