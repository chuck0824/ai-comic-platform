<template>
  <div class="canvas-card" :class="{ archived: canvas.status === 'archived' }">
    <div class="canvas-card-header">
      <span class="canvas-card-name">{{ canvas.name }}</span>
      <el-tag v-if="canvas.hasUpstreamChanges" type="warning" size="small" effect="dark">上游已更新</el-tag>
    </div>
    <div class="canvas-card-meta">
      <el-tag :type="statusType" size="small">{{ statusLabel }}</el-tag>
      <el-tag v-if="purposeLabel" size="small" type="info">{{ purposeLabel }}</el-tag>
      <span class="canvas-card-project">{{ canvas.contentProjectName || '—' }}</span>
    </div>
    <div class="canvas-card-footer">
      <span class="canvas-card-time">{{ timeText }}</span>
      <div class="canvas-card-actions">
        <el-button v-if="actions.canEdit" size="small" @click="$emit('edit', canvas)">编辑</el-button>
        <el-dropdown v-if="hasMenuActions" trigger="click" @command="handleCommand">
          <el-button size="small" :icon="MoreFilled" />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="actions.canCopy" command="copy">复制为新方案</el-dropdown-item>
              <el-dropdown-item v-if="actions.canMove" command="move">移动到...</el-dropdown-item>
              <el-dropdown-item v-if="actions.canArchive" command="archive">归档</el-dropdown-item>
              <el-dropdown-item v-if="actions.canRestore" command="restore">恢复</el-dropdown-item>
              <el-dropdown-item v-if="actions.canDelete" command="delete" divided>删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { MoreFilled } from '@element-plus/icons-vue'
import { STATUS_LABELS, PURPOSE_LABELS, canvasActions } from './canvasProjectViewModel.js'

const props = defineProps({
  canvas: { type: Object, required: true }
})

const emit = defineEmits(['edit', 'command'])

const statusLabel = computed(() => STATUS_LABELS[props.canvas.status] || props.canvas.status)
const purposeLabel = computed(() => PURPOSE_LABELS[props.canvas.purpose])
const statusType = computed(() => {
  const map = { editing: '', generating: 'warning', composing: 'warning', completed: 'success', archived: 'info' }
  return map[props.canvas.status] || ''
})
const actions = computed(() => canvasActions(props.canvas))
const hasMenuActions = computed(() => actions.value.canCopy || actions.value.canMove || actions.value.canArchive || actions.value.canRestore || actions.value.canDelete)
const timeText = computed(() => {
  if (!props.canvas.updatedAt) return ''
  return new Date(props.canvas.updatedAt).toLocaleDateString('zh-CN')
})

function handleCommand(cmd) {
  emit('command', { action: cmd, canvas: props.canvas })
}
</script>

<style scoped>
.canvas-card { border: 1px solid #e4e7ed; border-radius: 8px; padding: 16px; transition: box-shadow .2s; }
.canvas-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.08); }
.canvas-card.archived { opacity: .6; }
.canvas-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.canvas-card-name { font-weight: 600; font-size: 14px; }
.canvas-card-meta { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; }
.canvas-card-project { color: #71717a; font-size: 12px; }
.canvas-card-footer { display: flex; justify-content: space-between; align-items: center; }
.canvas-card-time { color: #a1a1aa; font-size: 12px; }
.canvas-card-actions { display: flex; gap: 4px; }
</style>
