<template>
  <div class="workflow-rail">
    <div class="rail-title">创作流程</div>
    <div v-for="(stage, i) in stages" :key="stage.key" class="stage-group">
      <div :class="['stage-item', 'stage-' + stage.status]">
        <div class="stage-indicator">
          <span :class="['stage-dot', stage.status]"></span>
          <span class="stage-label" :class="{ 'font-semibold': stage.status === 'current' }">
            {{ stageLabel(stage.key) }}
          </span>
        </div>
        <span v-if="stage.status === 'current'" class="stage-current-badge">← 当前</span>
        <el-icon v-else-if="stage.status === 'completed'" class="stage-check" :size="14"><CircleCheck /></el-icon>
        <span v-else-if="stage.status === 'skipped'" class="stage-skipped">跳过</span>
      </div>
      <div v-if="i < stages.length - 1" :class="['stage-connector', { active: stage.status === 'completed' }]"></div>
    </div>

    <!-- Progress -->
    <div class="rail-progress">
      <div class="rail-progress-label">完成度</div>
      <el-progress :percentage="progress" :stroke-width="6" :color="'var(--accent)'" />
    </div>
  </div>
</template>

<script setup>
import { CircleCheck } from '@element-plus/icons-vue'
import { stageLabel } from '../utils/workflowPath'

defineProps({
  stages: { type: Array, default: () => [] },
  progress: { type: Number, default: 0 }
})
</script>

<style scoped>
.workflow-rail {
  width: 220px;
  border-right: 1px solid var(--border);
  padding: 16px 12px;
  overflow-y: auto;
  background: var(--bg-surface);
  flex-shrink: 0;
}
.rail-title {
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .04em;
  color: var(--text-secondary);
  margin-bottom: 16px;
  padding: 0 4px;
}

.stage-group {
  margin-bottom: 2px;
}
.stage-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 8px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  transition: background .15s ease;
}
.stage-item.stage-current {
  background: var(--accent-bg);
}
.stage-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}
.stage-label {
  font-size: 13px;
  color: var(--text-primary);
}
.stage-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}
.stage-dot.completed {
  background: var(--success);
}
.stage-dot.current {
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-bg);
}
.stage-dot.pending {
  background: var(--border);
}
.stage-dot.skipped,
.stage-dot.locked {
  background: var(--text-tertiary);
}

.stage-current-badge {
  font-size: 11px;
  color: var(--accent);
  font-weight: 600;
}
.stage-check {
  color: var(--success);
}
.stage-skipped {
  font-size: 11px;
  color: var(--text-tertiary);
}

.stage-connector {
  width: 2px;
  height: 14px;
  margin-left: 11px;
  background: var(--border);
}
.stage-connector.active {
  background: var(--success);
}

.rail-progress {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}
.rail-progress-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 8px;
}

@media (max-width: 768px) {
  .workflow-rail {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--border);
    padding: 12px;
  }
}
</style>
