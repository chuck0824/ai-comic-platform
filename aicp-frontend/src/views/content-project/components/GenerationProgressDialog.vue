<template>
  <el-dialog :model-value="visible" title="AI 正在生成" width="460px" :close-on-click-modal="false" :show-close="false">
    <el-descriptions :column="1" size="small" border>
      <el-descriptions-item label="模型">{{ task?.modelName || task?.modelId || '—' }}</el-descriptions-item>
      <el-descriptions-item label="预计积分">{{ task?.estimatedPoints ?? 0 }} 积分</el-descriptions-item>
      <el-descriptions-item label="当前任务">{{ task?.subtask || '正在准备生成任务' }}</el-descriptions-item>
    </el-descriptions>
    <el-progress class="progress" :percentage="task?.progress ?? 0" :status="task?.error ? 'exception' : undefined" />
    <el-alert v-if="task?.error" type="error" :title="task.error" :closable="false" show-icon />
    <template #footer>
      <el-button v-if="task?.cancelable" @click="emit('cancel', task?.id)">取消任务</el-button>
      <el-button v-else @click="emit('close')">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  task: { type: Object, default: null }
})

const emit = defineEmits(['cancel', 'close'])
</script>

<style scoped>
.progress { margin: 20px 0 12px; }
</style>
