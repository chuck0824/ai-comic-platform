<template>
  <div class="workflow-rail">
    <div class="rail-title">创作流程</div>
    <div v-for="(stage, i) in stages" :key="stage.key" class="stage-group">
      <button
        type="button"
        :class="['stage-item', 'stage-' + stage.status, { 'stage-entered': canNavigate(stage) }]"
        :aria-current="stage.status === 'current' ? 'step' : undefined"
        :aria-disabled="canNavigate(stage) ? undefined : 'true'"
        @click="navigate(stage)"
      >
        <div class="stage-indicator">
          <span :class="['stage-dot', stage.status]"></span>
          <span class="stage-label" :class="{ 'font-semibold': stage.status === 'current' }">
            {{ stageLabel(stage.key, stage.label) }}
          </span>
        </div>
        <span v-if="stage.status === 'current'" class="stage-current-badge">← 当前</span>
        <el-icon v-else-if="stage.status === 'completed'" class="stage-check" :size="14"><CircleCheck /></el-icon>
        <span v-else-if="stage.status === 'locked'" class="stage-locked">已锁定</span>
        <span v-else-if="stage.status === 'possibly_stale'" class="stage-stale">需确认</span>
        <span v-else-if="stage.status === 'regen_required'" class="stage-regen">需要重做</span>
        <span v-else-if="stage.status === 'error'" class="stage-error">需处理</span>
        <span v-else-if="stage.status === 'in_progress'" class="stage-progress">编辑中</span>
      </button>
      <div v-if="i < stages.length - 1" :class="['stage-connector', { active: isConnectorActive(stage) }]"></div>
    </div>

    <!-- Progress -->
    <div class="rail-progress">
      <div class="rail-progress-label">完成度</div>
      <el-progress :percentage="progress" :stroke-width="6" :color="'var(--accent)'" />
    </div>

    <div class="stage-footer">
      <el-button size="small" @click="emit('previous')">上一步</el-button>
      <el-button size="small" @click="emit('save-draft')">保存草稿</el-button>
      <el-button size="small" type="primary" @click="emit('confirm-next')">确认并进入下一步</el-button>
    </div>
  </div>
</template>

<script setup>
import { CircleCheck } from '@element-plus/icons-vue'
import { STAGES } from '../workbench/scriptWorkbenchModel'

const APPROVED_LABELS = Object.fromEntries(STAGES.map(stage => [stage.key, stage.label]))

const props = defineProps({
  stages: { type: Array, default: () => [] },
  progress: { type: Number, default: 0 },
  enteredStages: { type: Array, default: () => [] }
})

const emit = defineEmits(['navigate', 'previous', 'save-draft', 'confirm-next'])

function stageLabel(key, fallback) {
  return APPROVED_LABELS[key] || fallback || key
}

function canNavigate(stage) {
  return props.enteredStages.includes(stage.key)
}

function isConnectorActive(stage) {
  return ['completed', 'locked', 'current'].includes(stage.status)
}

function navigate(stage) {
  if (canNavigate(stage)) emit('navigate', stage.key)
}
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
  width: 100%;
  border: 0;
  font: inherit;
  text-align: left;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 8px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  transition: background .15s ease;
}
.stage-item.stage-entered { cursor: pointer; }
.stage-item:not(.stage-entered) { cursor: default; }
.stage-item.stage-current {
  background: var(--accent-bg);
}
.stage-item.stage-possibly_stale {
  background: rgba(217, 119, 6, 0.08);
}
.stage-item.stage-regen_required,
.stage-item.stage-error {
  background: rgba(220, 38, 38, 0.06);
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
.stage-dot.in_progress {
  background: var(--accent);
}
.stage-dot.possibly_stale {
  background: #d97706;
}
.stage-dot.regen_required {
  background: var(--danger);
}
.stage-dot.error {
  background: var(--danger);
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
.stage-error,
.stage-regen {
  font-size: 11px;
  color: var(--danger);
}
.stage-stale {
  font-size: 11px;
  color: #d97706;
  font-weight: 600;
}
.stage-locked,
.stage-progress {
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
.stage-footer { display: grid; gap: 8px; margin-top: 18px; }

@media (max-width: 768px) {
  .workflow-rail {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--border);
    padding: 12px;
  }
}
</style>
