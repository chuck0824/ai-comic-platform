<template>
  <div class="shot-grid" data-testid="shot-grid">
    <div class="grid-toolbar">
      <el-button size="small" @click="$emit('add-shot', scenes[0]?.id)" :disabled="isLocked">
        <el-icon><Plus /></el-icon> 添加镜头
      </el-button>
      <span class="grid-count">{{ shots.length }} 镜</span>
    </div>
    <div class="grid-table">
      <el-table
        :data="shots"
        highlight-current-row
        @row-click="(row) => $emit('select-shot', row.id)"
        :row-class-name="(row) => row.id === selectedShotId ? 'selected-row' : ''"
        stripe
        size="small"
        max-height="calc(100vh - 180px)"
      >
        <el-table-column prop="shotCode" label="镜号" width="80" />
        <el-table-column prop="durationMs" label="时长" width="70">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column prop="shotSize" label="景别" width="70" />
        <el-table-column prop="visualDescriptionSummary" label="画面描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="dialogueText" label="对白" min-width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click.stop="$emit('duplicate-shot', row.id)" :disabled="isLocked">复制</el-button>
            <el-button text size="small" type="danger" @click.stop="$emit('remove-shot', row.id)" :disabled="isLocked">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
import { formatDuration } from '../storyboardData'

defineProps({
  shots: { type: Array, default: () => [] },
  scenes: { type: Array, default: () => [] },
  selectedShotId: Number,
  isLocked: Boolean,
  loading: Boolean
})
defineEmits(['select-shot', 'add-shot', 'duplicate-shot', 'remove-shot', 'split-shot'])

function statusType(s) {
  if (s === 'confirmed') return 'success'
  if (s === 'needs_review') return 'warning'
  return 'info'
}
function statusLabel(s) {
  const map = { draft: '草稿', confirmed: '已确认', needs_review: '待检查' }
  return map[s] || s
}
</script>

<style scoped>
.shot-grid { display: flex; flex-direction: column; height: 100%; background: var(--el-bg-color, #fff); }
.grid-toolbar { display: flex; align-items: center; gap: 12px; padding: 8px; border-bottom: 1px solid var(--el-border-color-light, #e4e7ed); }
.grid-count { font-size: 12px; color: var(--el-text-color-secondary); }
.grid-table { flex: 1; overflow: hidden; }
:deep(.selected-row) { background-color: var(--el-color-primary-light-9) !important; }
</style>
