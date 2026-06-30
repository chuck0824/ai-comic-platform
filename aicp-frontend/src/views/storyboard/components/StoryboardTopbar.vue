<template>
  <div class="topbar" data-testid="topbar">
    <div class="topbar-left">
      <el-button text @click="$router.back()">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <div class="topbar-info">
        <span class="title">{{ storyboard?.title || '分镜编辑' }}</span>
        <el-tag v-if="activeVersion" size="small" :type="tierTagType">
          {{ activeVersion.tier }}档 v{{ activeVersion.versionNo }}
        </el-tag>
        <el-tag v-if="activeVersion" size="small" :type="statusTagType">
          {{ statusLabel }}
        </el-tag>
      </div>
    </div>

    <div class="topbar-center">
      <span class="save-state" :class="saveStateClass" data-testid="save-state">
        {{ saveStateLabel }}
      </span>
      <span class="stats">
        {{ totalShots }}镜 · {{ formatDuration(totalDurationMs) }}
      </span>
    </div>

    <div class="topbar-right">
      <el-button size="small" @click="$emit('switch-version', null)" :disabled="!versions.length">
        版本历史
      </el-button>
      <el-button size="small" type="primary" @click="$emit('lock')" :disabled="isLocked || !activeVersion">
        锁定版本
      </el-button>
      <el-button v-if="isLocked" size="small" type="success" @click="$emit('fork')">
        复制为新草稿
      </el-button>
      <el-dropdown v-if="isLocked && activeVersion?.tier !== 'C'" @command="(tier) => $emit('upgrade', tier)">
        <el-button size="small" type="warning">升档</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="activeVersion?.tier === 'A'" command="B">升到 B 档</el-dropdown-item>
            <el-dropdown-item v-if="activeVersion?.tier === 'B'" command="C">升到 C 档</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { formatDuration } from '../storyboardData'

const props = defineProps({
  storyboard: Object,
  activeVersion: Object,
  versions: Array,
  saveState: String,
  isLocked: Boolean,
  totalShots: Number,
  totalDurationMs: Number
})

defineEmits(['switch-version', 'lock', 'fork', 'upgrade'])

const tierTagType = computed(() =>
  props.activeVersion?.tier === 'C' ? 'danger' : props.activeVersion?.tier === 'B' ? 'warning' : '')

const statusTagType = computed(() => {
  const s = props.activeVersion?.status
  if (s === 'locked') return 'info'
  if (s === 'reviewing') return 'warning'
  return ''
})

const statusLabel = computed(() => {
  const s = props.activeVersion?.status
  const map = { draft: '草稿', reviewing: '审核中', locked: '已锁定', superseded: '已替代' }
  return map[s] || s
})

const saveStateClass = computed(() => 'save-' + (props.saveState || 'idle'))
const saveStateLabel = computed(() => {
  const map = { idle: '', waiting: '等待保存', saving: '保存中...', saved: '已保存', failed: '保存失败', conflict: '版本冲突' }
  return map[props.saveState] || ''
})
</script>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-bg-color, #fff);
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
  min-height: 48px;
  gap: 16px;
}
.topbar-left { display: flex; align-items: center; gap: 8px; }
.topbar-info { display: flex; align-items: center; gap: 8px; }
.title { font-weight: 600; font-size: 15px; }
.topbar-center { display: flex; align-items: center; gap: 12px; font-size: 13px; color: var(--el-text-color-secondary); }
.topbar-right { display: flex; gap: 8px; }
.save-saved { color: var(--el-color-success); }
.save-failed, .save-conflict { color: var(--el-color-danger); }
.save-saving, .save-waiting { color: var(--el-color-warning); }
</style>
