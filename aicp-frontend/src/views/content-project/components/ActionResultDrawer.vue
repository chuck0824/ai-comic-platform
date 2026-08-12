<template>
  <el-drawer :model-value="visible" title="生成结果" size="440px" @close="emit('close')">
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="任务 ID">{{ result?.taskId || '—' }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ result?.status || '—' }}</el-descriptions-item>
      <el-descriptions-item label="影响">{{ result?.impact || '无' }}</el-descriptions-item>
      <el-descriptions-item label="产物路径">{{ result?.artifact?.path || '尚未返回' }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ result?.artifact?.version ?? '—' }}</el-descriptions-item>
    </el-descriptions>
    <el-alert v-if="result?.artifact?.message" class="result-error" type="warning" :title="result.artifact.message" :closable="false" show-icon />
    <section v-if="diffView.visible" class="result-diff">
      <h3>修订对比</h3>
      <p v-if="diffView.summary" class="diff-summary">{{ diffView.summary }}</p>
      <div class="diff-columns">
        <article><strong>修订前</strong><pre>{{ displayDiffValue(diffView.before) }}</pre></article>
        <article><strong>修订后</strong><pre>{{ displayDiffValue(diffView.after) }}</pre></article>
      </div>
      <div v-if="diffView.changes.length" class="diff-changes">
        <el-tag v-for="change in diffView.changes" :key="String(change)" size="small">{{ displayDiffValue(change) }}</el-tag>
      </div>
    </section>
    <el-alert v-if="result?.error" class="result-error" type="error" :title="result.error" :closable="false" show-icon />
    <template #footer>
      <el-button @click="emit('discard', result?.taskId)">丢弃结果</el-button>
      <el-button type="primary" @click="emit('accept', result?.taskId)">采用结果</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { generationDiffView } from '../workbench/scriptWorkbenchModel.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  result: { type: Object, default: null }
})

const emit = defineEmits(['accept', 'discard', 'close'])
const diffView = computed(() => generationDiffView(props.result ?? {}))

function displayDiffValue(value) {
  if (value == null) return '—'
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2)
}
</script>

<style scoped>
.result-error { margin-top: 16px; }
.result-diff { margin-top: 18px; }
.result-diff h3 { margin: 0 0 10px; }
.diff-summary { color: var(--el-text-color-secondary); }
.diff-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.diff-columns article { border: 1px solid var(--el-border-color); border-radius: 8px; padding: 10px; min-width: 0; }
.diff-columns pre { white-space: pre-wrap; overflow-wrap: anywhere; margin: 8px 0 0; color: var(--el-text-color-secondary); font: inherit; }
.diff-changes { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
@media (max-width: 560px) { .diff-columns { grid-template-columns: 1fr; } }
</style>
