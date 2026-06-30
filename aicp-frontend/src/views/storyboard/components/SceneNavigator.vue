<template>
  <div class="scene-nav" data-testid="scene-navigator">
    <div class="nav-header">
      <span class="nav-title">场景</span>
      <span class="nav-count">{{ scenes.length }}</span>
    </div>
    <div class="nav-list">
      <div
        v-for="scene in scenes"
        :key="scene.id"
        class="nav-scene"
      >
        <div class="scene-header">
          <span class="scene-no">S{{ scene.sceneNo }}</span>
          <span class="scene-title">{{ scene.title || '未命名' }}</span>
          <span class="scene-dur">{{ formatDuration(scene.durationMs) }}</span>
        </div>
        <div
          v-for="shot in scene.shots"
          :key="shot.id"
          class="nav-shot"
          :class="{ active: shot.id === selectedShotId }"
          @click="$emit('select-shot', shot.id)"
        >
          <span class="shot-code">{{ shot.shotCode }}</span>
          <span class="shot-dur">{{ formatDuration(shot.durationMs) }}</span>
        </div>
        <el-button text size="small" class="add-shot-btn" @click="$emit('add-shot', scene.id)">
          + 添加镜头
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { formatDuration } from '../storyboardData'

defineProps({
  scenes: { type: Array, default: () => [] },
  selectedShotId: Number
})
defineEmits(['select-shot', 'add-shot'])
</script>

<style scoped>
.scene-nav {
  height: 100%;
  overflow-y: auto;
  background: var(--el-bg-color, #fff);
  border-right: 1px solid var(--el-border-color-light, #e4e7ed);
  padding: 8px;
}
.nav-header { display: flex; align-items: center; gap: 8px; padding: 4px 8px 8px; font-weight: 600; font-size: 13px; }
.nav-count { color: var(--el-text-color-secondary); font-size: 12px; }
.nav-scene { margin-bottom: 4px; }
.scene-header { display: flex; align-items: center; gap: 4px; padding: 4px 8px; font-size: 12px; color: var(--el-text-color-secondary); background: var(--el-fill-color-light); border-radius: 4px; }
.scene-no { font-weight: 600; color: var(--el-color-primary); }
.scene-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nav-shot { display: flex; align-items: center; gap: 4px; padding: 4px 8px 4px 16px; cursor: pointer; font-size: 12px; border-radius: 4px; }
.nav-shot:hover { background: var(--el-fill-color-light); }
.nav-shot.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.shot-code { font-family: monospace; font-size: 11px; }
.shot-dur { margin-left: auto; color: var(--el-text-color-placeholder); font-size: 11px; }
.add-shot-btn { width: 100%; margin-top: 2px; font-size: 11px; }
</style>
