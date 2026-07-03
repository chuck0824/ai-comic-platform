<template>
  <div v-if="count > 0" class="batch-bar">
    <span class="batch-label">已选 {{ count }} 项</span>
    <el-button size="small" @click="$emit('select-all')">全选</el-button>
    <el-button size="small" type="danger" @click="$emit('trash')"><el-icon><Delete /></el-icon>删除</el-button>
    <el-button size="small" type="primary" @click="$emit('restore')"><el-icon><RefreshLeft /></el-icon>恢复</el-button>
    <el-button size="small" @click="$emit('clear')">取消选择</el-button>
    <slot />
  </div>
</template>

<script setup>
defineProps({ count: { type: Number, default: 0 } })
defineEmits(['select-all', 'trash', 'restore', 'clear'])
</script>

<style scoped>
.batch-bar { position: fixed; bottom: 0; left: 0; right: 0; background: #1e1e3f; border-top: 1px solid #2a2a4e; padding: 12px 24px; display: flex; align-items: center; gap: 12px; z-index: 100; }
.batch-label { font-size: 14px; font-weight: 600; }
</style>
