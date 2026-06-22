<!-- 钩子强度条：✓ 开场钩子 + 结尾悬念 双色显示 -->
<template>
  <div class="hook-bars">
    <span v-if="opening" class="hook-dot" :style="{ background: color(openingScore) }" :title="'开场钩子: ' + opening"><el-icon :size="10"><VideoCamera /></el-icon></span>
    <span v-else class="hook-dot" style="background:var(--border-light)" title="无开场钩子"><el-icon :size="10"><VideoCamera /></el-icon></span>
    <span v-if="closing" class="hook-dot" :style="{ background: color(closingScore) }" :title="'结尾悬念: ' + closing"><el-icon :size="10"><Link /></el-icon></span>
    <span v-else class="hook-dot" style="background:var(--border-light)" title="无结尾悬念"><el-icon :size="10"><Link /></el-icon></span>
  </div>
</template>

<script setup>
defineProps({
  opening: { type: String, default: '' },
  closing: { type: String, default: '' },
  openingScore: { type: Number, default: null },
  closingScore: { type: Number, default: null }
})

function color(score) {
  if (score == null) return 'var(--border-light)'
  if (score >= 0.8) return '#10b981'
  if (score >= 0.5) return '#f59e0b'
  return '#ef4444'
}
</script>

<style scoped>
.hook-bars { display:flex; gap:4px; align-items:center; }
.hook-dot { display:flex; align-items:center; justify-content:center; width:22px; height:22px; border-radius:50%; font-size:10px; }
</style>
