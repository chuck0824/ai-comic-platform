<template>
  <div class="shot-card" :class="{ 'is-upgraded': tier }">
    <div class="shot-header">
      <span class="shot-no">镜头 {{ shot.shot_no }}</span>
      <el-tag v-if="shot.shot_type" size="small" type="info">{{ shot.shot_type }}</el-tag>
      <span class="shot-duration">{{ shot.duration_sec || 0 }}s</span>
      <el-tag v-if="shot.status && shot.status !== 'draft'" size="small" :type="statusType">
        {{ shot.status }}
      </el-tag>
    </div>

    <p v-if="shot.description" class="shot-desc">{{ shot.description }}</p>
    <div v-if="shot.camera_action" class="shot-meta">
      <span class="text-muted">运镜：</span>{{ shot.camera_action }}
    </div>
    <div v-if="shot.dialogue_ref" class="shot-meta">
      <span class="text-muted">对白：</span>{{ shot.dialogue_ref }}
    </div>

    <!-- B-tier details -->
    <div v-if="shot.director_intention" class="tier-detail tier-b">
      <span class="tier-badge">B-tier</span>
      <div><span class="text-muted">导演意图：</span>{{ ellipsis(shot.director_intention, 80) }}</div>
    </div>

    <!-- C-tier details -->
    <div v-if="shot.image_prompt || shot.dub_text" class="tier-detail tier-c">
      <span class="tier-badge">C-tier</span>
      <div v-if="shot.image_prompt"><span class="text-muted">生图：</span>{{ ellipsis(shot.image_prompt, 80) }}</div>
      <div v-if="shot.dub_text"><span class="text-muted">配音：</span>{{ ellipsis(shot.dub_text, 80) }}</div>
      <el-tag v-if="shot.failure_strategy" size="small" type="warning">{{ shot.failure_strategy }}</el-tag>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  shot: { type: Object, required: true },
  tier: { type: String, default: '' }  // 'B' or 'C'
})

const statusType = computed(() => {
  const map = { locked: 'success', failed: 'danger', pending: 'warning' }
  return map[props.shot?.status] || 'info'
})

function ellipsis(text, max) {
  return text && text.length > max ? text.slice(0, max) + '...' : text
}
</script>

<style scoped>
.shot-card {
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  margin-bottom: 8px;
  background: var(--el-bg-color);
}
.shot-card.is-upgraded {
  border-left: 3px solid var(--el-color-primary);
}
.shot-header {
  display: flex; align-items: center; gap: 8px; margin-bottom: 4px;
}
.shot-no { font-weight: 600; font-size: 13px; }
.shot-duration { margin-left: auto; color: var(--el-text-color-secondary); font-size: 12px; }
.shot-desc { font-size: 13px; margin: 4px 0; line-height: 1.5; }
.shot-meta { font-size: 12px; margin: 2px 0; }
.tier-detail { margin-top: 6px; padding: 6px 8px; border-radius: 4px; font-size: 12px; }
.tier-b { background: #f0f5ff; }
.tier-c { background: #fff7e6; }
.tier-badge { font-weight: 600; font-size: 11px; margin-right: 8px; }
.text-muted { color: var(--el-text-color-secondary); }
</style>
