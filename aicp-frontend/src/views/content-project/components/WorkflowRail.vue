<template>
  <div class="workflow-rail" style="width:220px;border-right:1px solid var(--el-border-color-light);padding:12px;overflow-y:auto">
    <div class="text-sm font-semibold mb-md">创作流程</div>
    <div v-for="(stage, i) in stages" :key="stage.key" class="mb-xs">
      <div :class="['stage-item', 'stage-' + stage.status]">
        <div class="flex items-center gap-xs">
          <span :class="['stage-dot', stage.status]"></span>
          <span class="text-sm" :style="{ fontWeight: stage.status === 'current' ? 'bold' : 'normal' }">
            {{ stageLabel(stage.key) }}
          </span>
        </div>
        <span v-if="stage.status === 'current'" class="text-xs text-primary">← 当前</span>
        <span v-else-if="stage.status === 'completed'" class="text-xs text-success">✓</span>
        <span v-else-if="stage.status === 'skipped'" class="text-xs text-muted">跳过</span>
      </div>
      <div v-if="i < stages.length - 1" class="stage-connector" :class="{ active: stage.status === 'completed' }"></div>
    </div>

    <!-- Progress -->
    <div class="mt-md pt-md" style="border-top:1px solid var(--el-border-color-light)">
      <div class="text-xs text-muted mb-xs">完成度</div>
      <el-progress :percentage="progress" :stroke-width="6" />
    </div>
  </div>
</template>

<script setup>
import { stageLabel } from '../utils/workflowPath'

defineProps({
  stages: { type: Array, default: () => [] },
  progress: { type: Number, default: 0 }
})
</script>

<style scoped>
.stage-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 8px; border-radius: 4px;
}
.stage-item.stage-current { background: var(--el-color-primary-light-9); }
.stage-dot {
  width: 8px; height: 8px; border-radius: 50%; display: inline-block;
}
.stage-dot.completed { background: var(--el-color-success); }
.stage-dot.current { background: var(--el-color-primary); }
.stage-dot.pending { background: var(--el-border-color); }
.stage-dot.skipped, .stage-dot.locked { background: var(--el-text-color-placeholder); }
.stage-connector {
  width: 2px; height: 12px; margin-left: 11px; background: var(--el-border-color);
}
.stage-connector.active { background: var(--el-color-success); }
</style>
