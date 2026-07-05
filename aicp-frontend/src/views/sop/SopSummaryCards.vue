<template>
  <div class="sop-summary-cards">
    <div class="summary-card" v-for="card in cards" :key="card.label">
      <div class="summary-card-value" :style="{ color: card.color }">{{ card.value }}</div>
      <div class="summary-card-label">{{ card.label }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { overallStatusLabel, overallStatusColor } from './sopState.js'

const props = defineProps({
  summary: { type: Object, default: () => ({}) },
})

const cards = computed(() => {
  const s = props.summary || {}
  return [
    {
      label: '总体状态',
      value: overallStatusLabel(s.overallStatus),
      color: `var(--el-color-${overallStatusColor(s.overallStatus)})`,
    },
    { label: '阻断', value: s.blockedCount ?? 0, color: 'var(--el-color-danger)' },
    { label: '告警', value: s.warningCount ?? 0, color: 'var(--el-color-warning)' },
    { label: '通过', value: s.passedCount ?? 0, color: 'var(--el-color-success)' },
  ]
})
</script>

<style scoped>
.sop-summary-cards {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}
.summary-card {
  flex: 1;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}
.summary-card-value {
  font-size: 28px;
  font-weight: 700;
}
.summary-card-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
