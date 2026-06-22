<!-- Step 4: 剧本编辑器 -->
<template>
  <div class="card" style="background:var(--bg-surface);border:1px solid var(--border-light);color:var(--text-primary)">
    <strong style="font-size:14px">Step {{ step }}: 剧本编辑器</strong>

    <div v-if="loading" class="text-center" style="padding:40px">
      <el-icon :size="40" color="#a1a1aa"><Loading /></el-icon>
      <p class="mt-md font-bold">AI正在生成剧本...</p>
      <el-progress :percentage="progress" :stroke-width="6" class="mt-md" style="max-width:300px;margin:0 auto" />
    </div>

    <div v-else-if="scriptText" class="script-layout mt-lg">
      <!-- 左侧集数导航 -->
      <div class="ep-nav">
        <div v-for="(ep, i) in episodes" :key="i"
             :class="['ep-nav-item', { active: currentEpisode === i }]"
             @click="currentEpisode = i">
          <span class="ep-nav-num">{{ i + 1 }}</span>
          <span>{{ ep.title || '第' + (i+1) + '集' }}</span>
        </div>
      </div>
      <!-- 右侧剧本内容 -->
      <div class="script-content">
        <!-- 工具栏 -->
        <div class="script-toolbar">
          <span class="text-xs text-muted">第{{ currentEpisode + 1 }}集 · {{ wordCount }}字 · 预计{{ estDuration }}</span>
          <div class="flex gap-sm">
            <el-button size="small" @click="ElMessage.info('Tab键触发AI续写')"><el-icon><Cpu /></el-icon> AI续写</el-button>
            <el-button size="small" @click="ElMessage.info('格式检查中...')"><el-icon><CircleCheck /></el-icon> 格式检查</el-button>
          </div>
        </div>
        <!-- 剧本文本 -->
        <el-input v-model="scriptText" type="textarea" :rows="18"
                  class="script-editor"
                  placeholder="[场景1：办公室] 时间·地点·人物&#10;△ 动作描述...&#10;角色名：对白内容&#10;【旁白】：旁白内容" />
        <!-- 角色台词统计 -->
        <div class="dialogue-stats mt-md" v-if="characterLines.length">
          <h4 class="text-xs font-bold mb-sm">台词统计</h4>
          <div class="flex gap-sm flex-wrap">
            <span v-for="c in characterLines" :key="c.name" class="badge badge-accent">
              {{ c.name }}: {{ c.lines }}句
            </span>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="canvas-mock mt-md" style="min-height:120px;display:flex;align-items:center;justify-content:center">
      <p class="text-muted">点击"生成剧本"开始</p>
    </div>

    <div class="flex gap-sm mt-lg">
      <el-button size="large" @click="$emit('prev')">← 上一步</el-button>
      <el-button size="large" @click="$emit('regenerate')" :loading="loading"><el-icon><Refresh /></el-icon> 重新生成</el-button>
      <el-button type="primary" size="large" @click="$emit('next')" :disabled="!scriptText">
        下一步：生成分镜 →
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, Cpu, CircleCheck, Refresh } from '@element-plus/icons-vue'

const props = defineProps({
  step: { type: Number, default: 4 },
  scriptText: { type: String, default: '' },
  episodes: { type: Array, default: () => [] },
  currentEpisode: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  progress: { type: Number, default: 0 }
})

defineEmits(['prev', 'next', 'regenerate'])

const wordCount = computed(() => (props.scriptText || '').length)
const estDuration = computed(() => {
  const mins = Math.round(wordCount.value / 250)
  return mins < 1 ? '<1分钟' : `约${mins}分钟`
})

// 简单台词统计：匹配 "角色名：对白" 模式
const characterLines = computed(() => {
  const lines = (props.scriptText || '').split('\n')
  const count = {}
  lines.forEach(line => {
    const m = line.match(/^([^：:△\【\（]+)[：:]/)
    if (m) {
      const name = m[1].trim()
      if (name.length < 20) count[name] = (count[name] || 0) + 1
    }
  })
  return Object.entries(count).map(([name, lines]) => ({ name, lines }))
})
</script>

<style scoped>
.script-layout { display:grid; grid-template-columns:140px 1fr; gap:16px; }
.ep-nav { display:flex; flex-direction:column; gap:4px; max-height:400px; overflow-y:auto; }
.ep-nav-item { padding:8px 10px; border-radius:6px; cursor:pointer; font-size:12px;
  border:1px solid transparent; transition:all .15s; }
.ep-nav-item:hover { background:var(--accent-bg); }
.ep-nav-item.active { background:var(--accent-bg); border-color:var(--accent); }
.ep-nav-num { font-weight:700; color:var(--accent); margin-right:6px; }
.script-content { min-width:0; }
.script-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:8px; }
.script-editor :deep(textarea) { font-family:'SF Mono',monospace; font-size:12px; line-height:1.8; }
</style>
