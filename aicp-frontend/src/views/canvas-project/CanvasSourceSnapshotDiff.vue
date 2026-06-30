<template>
  <div class="source-diff" v-if="diff">
    <div class="diff-summary">
      <span v-if="!diff.hasChanges" class="diff-clean">来源无变化</span>
      <template v-else>
        <el-tag v-for="d in diff.dimensions" :key="d.dimension" :type="severityType(d.severity)" size="small" effect="dark">
          {{ d.label }}: {{ d.changes?.length || 0 }} 项变更
        </el-tag>
      </template>
    </div>
    <div v-if="diff.hasChanges" class="diff-dimensions">
      <div v-for="dim in diff.dimensions" :key="dim.dimension" class="diff-dimension">
        <h4 :class="`severity-${dim.severity}`">{{ dim.label }}</h4>
        <div v-for="change in dim.changes" :key="change.field" class="diff-change">
          <span class="diff-field">{{ change.label }}</span>
          <span class="diff-old">{{ change.snapshotValue || '—' }}</span>
          <el-icon><ArrowRight /></el-icon>
          <span class="diff-new">{{ change.upstreamValue || '—' }}</span>
          <el-tag :type="change.changeType === 'added' ? 'success' : change.changeType === 'deleted' ? 'danger' : 'warning'" size="small">
            {{ change.changeType }}
          </el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { SEVERITY_COLORS } from './canvasProjectViewModel.js'

defineProps({ diff: { type: Object, default: null } })

function severityType(s) { return SEVERITY_COLORS[s] || 'info' }
</script>

<style scoped>
.source-diff { padding: 16px; border: 1px solid #e4e7ed; border-radius: 8px; }
.diff-summary { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.diff-clean { color: #67c23a; font-weight: 500; }
.diff-dimension { margin-bottom: 16px; }
.diff-dimension h4 { margin: 0 0 8px; font-size: 14px; }
.diff-dimension h4.severity-blocking { color: #f56c6c; }
.diff-dimension h4.severity-warning { color: #e6a23c; }
.diff-dimension h4.severity-info { color: #909399; }
.diff-change { display: flex; align-items: center; gap: 8px; padding: 4px 0; font-size: 13px; }
.diff-field { font-weight: 500; min-width: 80px; }
.diff-old { color: #f56c6c; text-decoration: line-through; }
.diff-new { color: #67c23a; }
</style>
