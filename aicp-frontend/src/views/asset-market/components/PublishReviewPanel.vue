<template>
  <div>
    <h3 class="font-semibold mb-md">审批中心</h3>

    <el-skeleton v-if="market.publishRequests.loading" :rows="4" animated />
    <el-empty v-else-if="market.publishRequests.error" :description="market.publishRequests.error" />
    <div v-else-if="market.publishRequests.data">
      <el-empty v-if="!market.publishRequests.data.items?.length" description="暂无待审批申请" />
      <div v-for="item in (market.publishRequests.data.items || []).filter(i => i.status === 'PENDING')" :key="item.id" class="card mb-md" style="padding:16px">
        <div class="flex justify-between items-center">
          <div>
            <span class="font-semibold">资产 #{{ item.assetId }} · 版本 #{{ item.versionId }}</span>
            <el-tag type="warning" size="small" class="ml-sm">待审批</el-tag>
          </div>
        </div>
        <p class="text-sm text-muted mt-sm">申请人: #{{ item.requesterId }}</p>
        <p class="text-sm text-muted" v-if="item.reason">申请说明: {{ item.reason }}</p>
        <div class="flex gap-sm mt-md">
          <el-button size="small" type="success" @click="onApprove(item)">批准</el-button>
          <el-button size="small" type="danger" @click="onReject(item)">驳回</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({ market: { type: Object, required: true } })

async function onApprove(item) {
  try {
    await props.market.approveRequest(item.id, { row_version: item.rowVersion })
    ElMessage.success('已批准发布')
    props.market.fetchPublishRequests({ status: 'PENDING' })
    props.market.fetchListings()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '审批失败')
  }
}

async function onReject(item) {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入驳回原因', '驳回申请', { confirmButtonText: '确认驳回', type: 'warning' })
    if (reason) {
      await props.market.rejectRequest(item.id, { row_version: item.rowVersion, reason })
      ElMessage.success('已驳回')
      props.market.fetchPublishRequests({ status: 'PENDING' })
    }
  } catch { /* cancelled */ }
}
</script>
