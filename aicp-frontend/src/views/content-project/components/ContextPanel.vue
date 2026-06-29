<template>
  <div class="context-panel" style="width:240px;border-left:1px solid var(--el-border-color-light);padding:12px;overflow-y:auto">
    <div class="text-sm font-semibold mb-md">项目上下文</div>

    <!-- Selected Versions -->
    <div v-if="versions && versions.length" class="mb-md">
      <div class="text-xs text-muted mb-xs">已选版本</div>
      <div v-for="v in versions" :key="v.key" class="text-xs mb-xs">
        <span class="font-semibold">{{ v.key }}</span>: v{{ v.versionNo }}
      </div>
    </div>

    <!-- Locked Facts -->
    <div class="mb-md">
      <div class="text-xs text-muted mb-xs">锁定事实</div>
      <div v-if="!lockedFacts || lockedFacts.length === 0" class="text-xs text-muted">暂无</div>
      <div v-for="(f, i) in lockedFacts" :key="i" class="text-xs mb-xs">
        <el-tag size="small">{{ f.label }}</el-tag> {{ f.value }}
      </div>
    </div>

    <!-- Impact Summary -->
    <div class="mb-md">
      <div class="text-xs text-muted mb-xs">影响范围</div>
      <div v-if="!impactSummary" class="text-xs text-muted">—</div>
      <p v-else class="text-xs">{{ impactSummary }}</p>
    </div>

    <!-- Storyboard Intent -->
    <div v-if="storyboardIntent" class="mb-md">
      <div class="text-xs text-muted mb-xs">分镜意向</div>
      <el-tag :type="storyboardIntent === 'requested' ? 'warning' : 'info'" size="small">
        {{ storyboardIntent === 'requested' ? '已请求' : storyboardIntent === 'skipped' ? '已跳过' : '未决定' }}
      </el-tag>
    </div>
  </div>
</template>

<script setup>
defineProps({
  versions: { type: Array, default: () => [] },
  lockedFacts: { type: Array, default: () => [] },
  impactSummary: { type: String, default: '' },
  storyboardIntent: { type: String, default: '' }
})
</script>
