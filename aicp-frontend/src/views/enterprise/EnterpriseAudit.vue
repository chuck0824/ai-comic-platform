<template>
  <div class="enterprise-audit">
    <h2>审计记录</h2>

    <el-row :gutter="12" class="filters">
      <el-col :span="4">
        <el-input v-model="filterActor" placeholder="操作者ID" clearable @change="fetchEvents" />
      </el-col>
      <el-col :span="4">
        <el-select v-model="filterAction" placeholder="动作" clearable @change="fetchEvents">
          <el-option label="创建" value="CREATE" />
          <el-option label="更新" value="UPDATE" />
          <el-option label="删除" value="DELETE" />
          <el-option label="审批" value="APPROVE" />
          <el-option label="驳回" value="REJECT" />
        </el-select>
      </el-col>
    </el-row>

    <el-table :data="events" stripe v-loading="loading" class="audit-table">
      <el-table-column prop="actorUserId" label="操作者" width="80" />
      <el-table-column prop="action" label="动作" width="80" />
      <el-table-column prop="objectType" label="对象类型" width="120" />
      <el-table-column prop="objectId" label="对象ID" width="120" />
      <el-table-column prop="result" label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sourceDomain" label="来源域" width="100" />
      <el-table-column prop="redactedSummary" label="摘要" min-width="200" />
      <el-table-column prop="createdAt" label="时间" width="170" />
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="prev,pager,next"
      @current-change="fetchEvents"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { enterpriseApi } from '@/api/enterprise'

const events = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterActor = ref('')
const filterAction = ref('')

async function fetchEvents() {
  loading.value = true
  try {
    const res = await enterpriseApi.listAuditEvents({
      page: page.value, size: size.value,
      actorUserId: filterActor.value || undefined,
      action: filterAction.value || undefined
    })
    if (res?.data) {
      events.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch {} finally { loading.value = false }
}

onMounted(fetchEvents)
</script>

<style scoped>
.filters { margin-bottom: 16px; }
.audit-table { margin-top: 8px; }
</style>
