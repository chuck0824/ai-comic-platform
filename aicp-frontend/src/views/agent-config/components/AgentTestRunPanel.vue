<script setup>
import { ref } from 'vue'
import { testRunStatusLabel } from '@/utils/agentConfigHelpers'

const props = defineProps({
  versionId: { type: String, required: true }
})

const emit = defineEmits(['test'])

const taskInput = ref('')
const running = ref(false)
const lastResult = ref(null)
const lastError = ref('')

const handleRun = async () => {
  running.value = true
  lastError.value = ''
  try {
    const result = await emit('test', { taskInput: taskInput.value || '测试输入文本' })
    lastResult.value = result
  } catch (e) {
    lastError.value = e?.message || '试跑失败'
  } finally {
    running.value = false
  }
}
</script>

<template>
  <div class="test-panel">
    <div class="tp-input">
      <h4>测试输入</h4>
      <el-input
        v-model="taskInput"
        type="textarea"
        :rows="4"
        placeholder="输入测试文本（可选，将使用默认测试内容）"
      />
      <el-button type="primary" :loading="running" @click="handleRun" style="margin-top:8px">
        <el-icon><VideoPlay /></el-icon>{{ running ? '运行中...' : '开始试跑' }}
      </el-button>
    </div>

    <div v-if="lastResult" class="tp-result">
      <h4>试跑结果</h4>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="状态">
          <el-tag :type="lastResult.status === 'SUCCEEDED' ? 'success' : 'danger'" size="small">
            {{ testRunStatusLabel(lastResult.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="模型">{{ lastResult.modelId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ lastResult.durationMs ? lastResult.durationMs + 'ms' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="Schema 校验">{{ lastResult.outputSchemaValid ? '通过' : '未通过' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="lastResult.outputJson" class="tp-output">
        <h4>模型输出</h4>
        <pre>{{ JSON.stringify(lastResult.outputJson, null, 2) }}</pre>
      </div>
    </div>

    <div v-if="lastError" class="tp-error">
      <el-alert :title="lastError" type="error" show-icon :closable="false" />
    </div>
  </div>
</template>

<style scoped>
.test-panel { display: flex; flex-direction: column; gap: 16px; }
.tp-input h4, .tp-result h4, .tp-output h4 { font-size: 14px; font-weight: 600; color: var(--text-secondary); margin: 0 0 8px; }
.tp-output pre {
  background: var(--bg-surface);
  padding: 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  white-space: pre-wrap;
  max-height: 300px;
  overflow-y: auto;
}
</style>
