<!-- Step 2: 故事梗概编辑器 -->
<template>
  <div class="card" style="background:var(--bg-surface);border:1px solid var(--border-light);color:var(--text-primary)">
    <strong style="font-size:14px">Step {{ step }}: 故事梗概</strong>

    <div v-if="loading" class="text-center" style="padding:40px">
      <el-icon :size="40" color="#a1a1aa"><Loading /></el-icon>
      <p class="mt-md font-bold">AI正在生成梗概...</p>
      <el-progress :percentage="progress" :stroke-width="6" class="mt-md" style="max-width:300px;margin:0 auto" />
    </div>

    <template v-else-if="data">
      <!-- 世界观 -->
      <div class="section mt-lg">
        <h4 class="section-title"><el-icon><Globe /></el-icon> 世界观</h4>
        <el-input v-model="data.worldBuilding" type="textarea" :rows="2" class="mt-sm"
                  placeholder="时代背景、世界规则、特殊设定..." />
      </div>

      <!-- 故事梗概 -->
      <div class="section mt-md">
        <h4 class="section-title"><el-icon><Reading /></el-icon> 故事梗概</h4>
        <el-input v-model="data.synopsis" type="textarea" :rows="6" class="mt-sm"
                  placeholder="300-500字完整故事梗概..." />
      </div>

      <!-- 主线剧情 -->
      <div class="section mt-md">
        <h4 class="section-title"><el-icon><TrendCharts /></el-icon> 主线剧情</h4>
        <div class="grid2 gap-sm mt-sm">
          <div v-for="(phase, i) in data.plotPhases" :key="i" class="phase-card">
            <span class="phase-label">{{ phaseLabels[i] }}</span>
            <el-input v-model="data.plotPhases[i]" size="small" />
          </div>
        </div>
      </div>

      <!-- 核心冲突 -->
      <div class="section mt-md">
        <h4 class="section-title"><el-icon><Warning /></el-icon> 核心冲突</h4>
        <el-input v-model="data.coreConflict" type="textarea" :rows="2" class="mt-sm"
                  placeholder="内部冲突 + 外部冲突..." />
      </div>

      <!-- 故事亮点 -->
      <div class="section mt-md">
        <h4 class="section-title"><el-icon><Star /></el-icon> 故事亮点</h4>
        <div class="flex gap-sm flex-wrap mt-sm">
          <el-tag v-for="(h, i) in data.highlights" :key="i" closable @close="data.highlights.splice(i, 1)">
            {{ h }}
          </el-tag>
          <el-input v-if="showAddHighlight" v-model="newHighlight" size="small" style="width:120px"
                    @blur="addHighlight" @keyup.enter="addHighlight" placeholder="新增亮点" />
          <el-button v-else size="small" @click="showAddHighlight = true">+ 添加</el-button>
        </div>
      </div>
    </template>

    <!-- 空状态 -->
    <div v-else class="canvas-mock mt-md" style="min-height:120px;display:flex;align-items:center;justify-content:center">
      <p class="text-muted">点击"生成梗概"开始</p>
    </div>

    <!-- 操作按钮 -->
    <div class="flex gap-sm mt-lg">
      <el-button size="large" @click="$emit('prev')">← 上一步</el-button>
      <el-button size="large" @click="$emit('regenerate')" :loading="loading"><el-icon><Refresh /></el-icon> 重新生成</el-button>
      <el-button size="small" @click="ElMessage.info('微调功能：选择段落→输入修改指令')"><el-icon><Tools /></el-icon> 微调</el-button>
      <el-button type="primary" size="large" @click="$emit('next')" :disabled="!data">
        下一步：生成分集大纲 →
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { Loading, Globe, Reading, TrendCharts, Warning, Star, Refresh, Tools } from '@element-plus/icons-vue'

defineProps({
  step: { type: Number, default: 2 },
  data: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  progress: { type: Number, default: 0 }
})

defineEmits(['prev', 'next', 'regenerate'])

const phaseLabels = ['起 · 开端', '承 · 发展', '转 · 高潮', '合 · 结局']
</script>

<style scoped>
.section { margin-bottom: 4px; }
.section-title { font-size:13px; font-weight:600; color:var(--text-primary); display:flex; align-items:center; gap:4px; }
.phase-card { background:var(--bg-surface-hover); padding:8px; border-radius:6px; border:1px solid var(--border-light); color:var(--text-primary); }
.phase-label { font-size:10px; color:var(--text-secondary); display:block; margin-bottom:4px; }
</style>
