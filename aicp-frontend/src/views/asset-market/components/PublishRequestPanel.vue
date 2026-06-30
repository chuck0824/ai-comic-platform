<template>
  <div>
    <div class="flex gap-sm mb-lg">
      <el-select v-model="statusFilter" placeholder="状态" clearable style="width:120px" @change="load">
        <el-option label="全部" value="" />
        <el-option label="待审批" value="PENDING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已驳回" value="REJECTED" />
        <el-option label="已撤回" value="CANCELLED" />
      </el-select>
    </div>

    <el-skeleton v-if="market.publishRequests.loading" :rows="4" animated />
    <el-empty v-else-if="market.publishRequests.error" :description="market.publishRequests.error" />
    <div v-else-if="market.publishRequests.data">
      <el-empty v-if="!market.publishRequests.data.items?.length" description="暂无发布申请" />
      <div v-else>
        <div v-for="item in market.publishRequests.data.items" :key="item.id" class="card mb-md" style="padding:16px">
          <div class="flex justify-between items-center">
            <div>
              <span class="font-semibold">资产 #{{ item.assetId }}</span>
              <el-tag :type="publishStatusTagType(item.status)" size="small" class="ml-sm">{{ publishStatusLabel(item.status) }}</el-tag>
            </div>
            <div class="flex gap-sm">
              <el-button v-if="item.status === 'PENDING'" size="small" type="danger" @click="market.cancelRequest(item.id).then(load)">撤回</el-button>
            </div>
          </div>
          <p class="text-sm text-muted mt-sm" v-if="item.reason">原因: {{ item.reason }}</p>
          <p class="text-sm text-muted" v-if="item.reviewComment">审批备注: {{ item.reviewComment }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { publishStatusLabel, publishStatusTagType } from '../assetMarketState'

const props = defineProps({ market: { type: Object, required: true } })
const statusFilter = ref('')

function load() {
  props.market.fetchPublishRequests({ status: statusFilter.value || undefined })
}
load()
</script>
