<template>
  <div class="sop-work-order-table">
    <el-table :data="orders" stripe size="small" v-loading="loading">
      <el-table-column prop="ruleCode" label="规则" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="workOrderStatusColor(row.status)" size="small">{{ workOrderStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="severity" label="等级" width="70">
        <template #default="{ row }">
          <el-tag :type="severityColor(row.severity)" size="small" effect="dark">{{ row.severity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="responsibleRole" label="责任岗位" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" min-width="200">
        <template #default="{ row }">
          <template v-if="row.status === 'OPEN'">
            <el-button size="small" @click="$emit('transition', row, 'ASSIGNED')">分配</el-button>
          </template>
          <template v-else-if="row.status === 'ASSIGNED' || row.status === 'REOPENED'">
            <el-button size="small" type="primary" @click="$emit('transition', row, 'FIXING')">开始修复</el-button>
          </template>
          <template v-else-if="row.status === 'FIXING'">
            <el-button size="small" type="warning" @click="$emit('transition', row, 'PENDING_REVIEW')">提交审核</el-button>
          </template>
          <template v-else-if="row.status === 'PENDING_REVIEW'">
            <el-button size="small" type="success" @click="$emit('review', row, true)">通过</el-button>
            <el-button size="small" type="danger" @click="$emit('review', row, false)">驳回</el-button>
          </template>
          <span v-else class="text-muted">--</span>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && orders.length === 0" description="暂无返工工单" />
  </div>
</template>

<script setup>
import { workOrderStatusLabel, workOrderStatusColor, severityColor } from './sopState.js'

defineProps({
  orders: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

defineEmits(['transition', 'review'])
</script>
