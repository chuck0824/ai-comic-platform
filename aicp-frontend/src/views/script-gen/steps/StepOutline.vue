<!-- Step 3: 分集大纲编辑器 + 钩子可视化 -->
<template>
  <div class="card" style="background:var(--bg-surface);border:1px solid var(--border-light);color:var(--text-primary)">
    <div class="flex items-center justify-between">
      <strong style="font-size:14px">Step {{ step }}: 分集大纲</strong>
      <el-button size="small" @click="$emit('optimizeHooks')"><el-icon><MagicStick /></el-icon> 自动优化钩子</el-button>
    </div>

    <div v-if="loading" class="text-center" style="padding:40px">
      <el-icon :size="40" color="#a1a1aa"><Loading /></el-icon>
      <p class="mt-md font-bold">AI正在生成大纲...</p>
      <el-progress :percentage="progress" :stroke-width="6" class="mt-md" style="max-width:300px;margin:0 auto" />
    </div>

    <div v-else-if="episodes.length" class="episode-list mt-lg">
      <div v-for="(ep, i) in episodes" :key="i" class="episode-card" :class="{ expanded: ep._expanded }">
        <div class="ep-header" @click="ep._expanded = !ep._expanded">
          <span class="ep-num">第{{ ep.number || (i+1) }}集</span>
          <span class="ep-title">{{ ep.title || '未命名' }}</span>
          <div class="ep-hooks">
            <span class="hook-badge" :style="{ background: hookColor(ep.openingHookStrength) }" title="开场钩子">
              <el-icon :size="10"><VideoCamera /></el-icon> {{ ep.openingHookStrength ? (ep.openingHookStrength * 100).toFixed(0) + '%' : '—' }}
            </span>
            <span class="hook-badge" :style="{ background: hookColor(ep.closingHookStrength) }" title="结尾悬念">
              <el-icon :size="10"><Link /></el-icon> {{ ep.closingHookStrength ? (ep.closingHookStrength * 100).toFixed(0) + '%' : '—' }}
            </span>
          </div>
          <span class="expand-icon">{{ ep._expanded ? '▾' : '▸' }}</span>
        </div>
        <div v-if="ep._expanded" class="ep-body">
          <el-input v-model="ep.title" size="small" placeholder="本集标题" class="mb-sm" />
          <el-input v-model="ep.coreEvent" type="textarea" :rows="2" size="small" placeholder="核心事件" class="mb-sm" />
          <div class="grid2 gap-sm">
            <div>
              <label class="text-xs text-muted">开场钩子</label>
              <el-input v-model="ep.openingHook" size="small" placeholder="前3秒抓住观众的悬念..." />
            </div>
            <div>
              <label class="text-xs text-muted">结尾悬念</label>
              <el-input v-model="ep.closingHook" size="small" placeholder="集末未解问题/危机/反转..." />
            </div>
          </div>
          <div class="mt-sm">
            <label class="text-xs text-muted">主要出场角色</label>
            <el-input v-model="ep.characters" size="small" placeholder="用逗号分隔" />
          </div>
        </div>
      </div>
    </div>

    <div v-else class="canvas-mock mt-md" style="min-height:120px;display:flex;align-items:center;justify-content:center">
      <p class="text-muted">点击"生成大纲"开始</p>
    </div>

    <div class="flex gap-sm mt-lg">
      <el-button size="large" @click="$emit('prev')">← 上一步</el-button>
      <el-button size="large" @click="$emit('regenerate')" :loading="loading"><el-icon><Refresh /></el-icon> 重新生成</el-button>
      <el-button type="primary" size="large" @click="$emit('next')" :disabled="!episodes.length">
        下一步：生成剧本 →
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { Loading, MagicStick, VideoCamera, Link, Refresh } from '@element-plus/icons-vue'

defineProps({
  step: { type: Number, default: 3 },
  episodes: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  progress: { type: Number, default: 0 }
})

defineEmits(['prev', 'next', 'regenerate', 'optimizeHooks'])

function hookColor(strength) {
  if (!strength && strength !== 0) return '#1e293b'
  if (strength >= 0.8) return '#065f46'
  if (strength >= 0.5) return '#78350f'
  return '#7f1d1d'
}
</script>

<style scoped>
.episode-list { display:flex; flex-direction:column; gap:8px; max-height:500px; overflow-y:auto; }
.episode-card { background:var(--bg-surface-hover); border:1px solid var(--border-light); color:var(--text-primary); border-radius:8px; overflow:hidden; }
.episode-card.expanded { border-color:var(--accent); }
.ep-header { display:flex; align-items:center; gap:10px; padding:10px 14px; cursor:pointer; user-select:none; }
.ep-header:hover { background:rgba(99,102,241,0.05); }
.ep-num { font-weight:700; font-size:12px; color:var(--accent); min-width:36px; }
.ep-title { flex:1; font-size:13px; }
.ep-hooks { display:flex; gap:6px; }
.hook-badge { padding:2px 8px; border-radius:4px; font-size:10px; color:#fff; }
.expand-icon { color:var(--text-secondary); font-size:12px; }
.ep-body { padding:0 14px 14px; }
</style>
