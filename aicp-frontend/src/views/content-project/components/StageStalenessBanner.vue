<template>
  <el-alert
    v-if="visible"
    :type="alertType"
    :title="title"
    show-icon
    :closable="false"
    class="staleness-banner"
  >
    <p class="staleness-message">{{ message }}</p>
    <div class="staleness-actions">
      <el-button size="small" type="primary" :loading="loading" @click="emitResolve('CONFIRM_CURRENT')">
        确认当前成果仍有效
      </el-button>
      <el-button size="small" :loading="loading" @click="emitResolve('CREATE_DRAFT')">
        基于旧成果创建草稿
      </el-button>
      <el-button size="small" :loading="loading" @click="emitResolve('REGENERATE')">
        重新生成
      </el-button>
      <el-button
        v-if="causeForkId"
        size="small"
        :loading="loading"
        @click="emitResolve('DISCARD_FORK')"
      >
        放弃本次 Fork
      </el-button>
    </div>
  </el-alert>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  serverState: { type: String, default: '' },
  staleReasons: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['resolve'])

const visible = computed(() =>
  props.serverState === 'POSSIBLY_STALE' || props.serverState === 'REGEN_REQUIRED')

const alertType = computed(() =>
  props.serverState === 'REGEN_REQUIRED' ? 'error' : 'warning')

const title = computed(() =>
  props.serverState === 'REGEN_REQUIRED' ? '上游关键变化，需要重做' : '上游已变化，请确认本阶段成果')

const openReasons = computed(() =>
  (props.staleReasons || []).filter(reason => String(reason.status || 'OPEN') !== 'RESOLVED'))

const causeForkId = computed(() => {
  const reason = openReasons.value[0]
  return reason?.causeForkId || reason?.cause_fork_id || ''
})

const message = computed(() => {
  if (!openReasons.value.length) {
    return '本阶段成果可能已过期，请选择处理方式。'
  }
  const triggers = [...new Set(openReasons.value.map(r => r.triggerStage || r.trigger_stage).filter(Boolean))]
  return `触发阶段：${triggers.join('、') || '未知'}。共 ${openReasons.value.length} 条未处理过期原因。`
})

function emitResolve(action) {
  emit('resolve', {
    action,
    reasonFilter: causeForkId.value ? { cause_fork_id: causeForkId.value } : undefined
  })
}
</script>

<style scoped>
.staleness-banner { margin-bottom: 12px; }
.staleness-message { margin: 0 0 10px; font-size: 13px; color: var(--text-secondary); }
.staleness-actions { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
