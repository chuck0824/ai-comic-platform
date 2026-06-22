<!-- Step 角色提取：AI 从剧本自动提取角色 → 结构化卡片 -->
<template>
  <div class="card" style="background:var(--bg-surface);border:1px solid var(--border-light);color:var(--text-primary)">
    <div class="flex items-center justify-between">
      <strong style="font-size:14px"><el-icon><User /></el-icon> 角色阵容 — AI 自动提取</strong>
      <div class="flex gap-sm">
        <el-button size="small" @click="$emit('extract')" :loading="loading"><el-icon><Refresh /></el-icon> 重新提取</el-button>
        <el-button size="small" type="primary" @click="$emit('saveAll')" :disabled="!characters.length">
          <el-icon><FolderAdd /></el-icon> 全部保存到仓库
        </el-button>
      </div>
    </div>

    <div v-if="loading" class="text-center" style="padding:40px">
      <el-icon :size="40" color="#a1a1aa"><Loading /></el-icon>
      <p class="mt-md">AI正在分析剧本，提取角色...</p>
    </div>

    <div v-else-if="characters.length" class="character-grid mt-lg">
      <div v-for="(char, i) in characters" :key="i" class="character-card" :class="{ existing: char.is_existing }">
        <div class="card-header">
          <span class="char-name">{{ char.name }}</span>
          <span :class="['badge', roleBadge(char.role_type)]">{{ char.role_type }}</span>
          <span v-if="char.is_existing" class="badge badge-success">已有资产</span>
        </div>
        <div class="card-body">
          <div class="attr-row"><span class="attr-label">外貌</span>{{ char.appearance }}</div>
          <div class="attr-row"><span class="attr-label">性格</span>{{ char.personality }}</div>
          <div class="attr-row"><span class="attr-label">成长弧</span>{{ char.growth_arc }}</div>
          <div class="attr-row"><span class="attr-label">台词</span>{{ char.dialogue_style }}</div>
        </div>
        <div class="card-footer">
          <el-button size="small" @click="$emit('save', char)" :disabled="char.is_existing">
            {{ char.is_existing ? '已保存' : '保存为资产' }}
          </el-button>
        </div>
      </div>
    </div>

    <div v-else class="canvas-mock mt-md" style="min-height:120px;display:flex;align-items:center;justify-content:center">
      <p class="text-muted">点击"提取角色"开始分析</p>
    </div>
  </div>
</template>

<script setup>
import { Loading, User, Refresh, FolderAdd } from '@element-plus/icons-vue'

defineProps({
  characters: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

defineEmits(['extract', 'save', 'saveAll'])

function roleBadge(type) {
  return { '主角': 'badge-accent', '重要角色': 'badge-warning', '配角': 'badge-neutral' }[type] || 'badge-neutral'
}
</script>

<style scoped>
.character-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(280px, 1fr)); gap:12px; }
.character-card { background:var(--bg-surface-hover); border:1px solid var(--border-light); color:var(--text-primary); border-radius:10px; overflow:hidden; }
.character-card.existing { border-color:var(--success); }
.card-header { display:flex; align-items:center; gap:8px; padding:10px 14px; border-bottom:1px solid var(--border-light); }
.char-name { font-weight:700; font-size:14px; }
.card-body { padding:10px 14px; }
.attr-row { font-size:11px; margin-bottom:4px; line-height:1.5; }
.attr-label { color:var(--accent); margin-right:8px; font-weight:600; min-width:40px; display:inline-block; }
.card-footer { padding:8px 14px; border-top:1px solid var(--border-light); display:flex; justify-content:flex-end; }
</style>
