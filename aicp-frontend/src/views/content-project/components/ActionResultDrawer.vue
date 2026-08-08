<template>
  <el-drawer :model-value="visible" title="生成结果" size="440px" @close="emit('close')">
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="任务 ID">{{ result?.taskId || '—' }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ result?.status || '—' }}</el-descriptions-item>
      <el-descriptions-item label="影响">{{ result?.impact || '无' }}</el-descriptions-item>
      <el-descriptions-item label="产物路径">{{ result?.artifact?.path || '尚未采用' }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ result?.artifact?.version ?? '—' }}</el-descriptions-item>
    </el-descriptions>
    <el-alert v-if="result?.error" class="result-error" type="error" :title="result.error" :closable="false" show-icon />
    <template #footer>
      <el-button @click="emit('discard', result?.taskId)">丢弃结果</el-button>
      <el-button type="primary" @click="emit('accept', result?.taskId)">采用结果</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  result: { type: Object, default: null }
})

const emit = defineEmits(['accept', 'discard', 'close'])
</script>

<style scoped>
.result-error { margin-top: 16px; }
</style>
