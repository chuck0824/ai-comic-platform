<!-- Step 6: 投流素材生成 -->
<template>
  <div class="card" style="background:var(--bg-surface);border:1px solid var(--border-light);color:var(--text-primary)">
    <strong style="font-size:14px">Step {{ step }}: 投流素材</strong>

    <div v-if="loading" class="text-center" style="padding:40px">
      <el-icon :size="40" color="#a1a1aa"><Loading /></el-icon>
      <p class="mt-md font-bold">AI正在生成投流素材...</p>
      <el-progress :percentage="progress" :stroke-width="6" class="mt-md" style="max-width:300px;margin:0 auto" />
    </div>

    <template v-else-if="data">
      <!-- 爆款标题 -->
      <div class="section mt-lg">
        <h4 class="section-title"><el-icon><TrendCharts /></el-icon> 爆款标题 <span class="text-xs text-muted">(5选1)</span></h4>
        <div class="title-grid mt-sm">
          <div v-for="(t, i) in (data.titles || [])" :key="i"
               :class="['title-card', { picked: selectedTitle === i }]"
               @click="selectedTitle = i">
            <span class="title-type">{{ titleTypes[i] }}</span>
            <p>{{ t }}</p>
          </div>
        </div>
      </div>

      <!-- 封面文案 -->
      <div class="section mt-lg">
        <h4 class="section-title"><el-icon><Picture /></el-icon> 封面文案 <span class="text-xs text-muted">(3套)</span></h4>
        <div v-for="(c, i) in (data.coverCopy || [])" :key="i" class="cover-card mt-sm">
          <p class="text-sm"><strong>方案{{ i+1 }}:</strong> {{ c }}</p>
        </div>
      </div>

      <!-- 3秒钩子 -->
      <div class="section mt-lg">
        <h4 class="section-title"><el-icon><Link /></el-icon> 3秒钩子 <span class="text-xs text-muted">(5条高注意力开场)</span></h4>
        <div class="flex flex-col gap-sm mt-sm">
          <div v-for="(h, i) in (data.threeSecHooks || [])" :key="i"
               class="hook-line">
            <span class="hook-num">{{ i + 1 }}</span>
            <span>{{ h }}</span>
          </div>
        </div>
      </div>

      <!-- 切片脚本 -->
      <div class="section mt-lg">
        <h4 class="section-title"><el-icon><Scissor /></el-icon> 短视频切片脚本 <span class="text-xs text-muted">(2-3段)</span></h4>
        <div v-for="(s, i) in (data.clipScripts || [])" :key="i" class="clip-card mt-sm">
          <p class="text-sm">{{ s }}</p>
        </div>
      </div>

      <!-- 评论区引导 -->
      <div class="section mt-lg">
        <h4 class="section-title"><el-icon><ChatLineRound /></el-icon> 评论区引导 <span class="text-xs text-muted">(3条)</span></h4>
        <div class="flex flex-col gap-sm mt-sm">
          <div v-for="(c, i) in (data.commentGuides || [])" :key="i"
               class="comment-line">
            <span><el-icon :size="14"><ChatLineRound /></el-icon></span>
            <span class="text-sm">{{ c }}</span>
          </div>
        </div>
      </div>
    </template>

    <div v-else class="canvas-mock mt-md" style="min-height:120px;display:flex;align-items:center;justify-content:center">
      <p class="text-muted">点击"生成投流素材"开始</p>
    </div>

    <div class="flex gap-sm mt-lg">
      <el-button size="large" @click="$emit('prev')">← 上一步</el-button>
      <el-button size="large" @click="$emit('regenerate')" :loading="loading"><el-icon><Refresh /></el-icon> 重新生成</el-button>
      <el-button type="success" size="large" @click="$emit('save')">
        <el-icon><FolderAdd /></el-icon> 保存到仓库
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Loading, TrendCharts, Picture, Link, Scissor, ChatLineRound, Refresh, FolderAdd } from '@element-plus/icons-vue'

defineProps({
  step: { type: Number, default: 6 },
  data: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  progress: { type: Number, default: 0 }
})

defineEmits(['prev', 'regenerate', 'save'])

const selectedTitle = ref(0)
const titleTypes = ['悬念式', '反转式', '痛点式', '数据式', '对比式']
</script>

<style scoped>
.section { margin-bottom: 4px; }
.section-title { font-size:13px; font-weight:600; color:var(--text-primary); display:flex; align-items:center; gap:4px; }
.title-grid { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
.title-card { padding:12px; background:var(--bg-surface); border:2px solid var(--border-light); color:var(--text-primary); border-radius:8px; cursor:pointer; transition:all .15s; font-size:13px; }
.title-card:hover { border-color:var(--accent); }
.title-card.picked { border-color:var(--accent); background:var(--accent-bg); }
.title-type { font-size:10px; color:var(--accent); display:block; margin-bottom:4px; }
.cover-card { padding:10px; background:#1a1a2e; border-radius:6px; color:var(--text-primary); }
.hook-line { display:flex; align-items:flex-start; gap:10px; padding:8px 12px; background:#1a1a2e; border-radius:6px; color:var(--text-primary); }
.hook-num { font-weight:700; color:var(--warning); min-width:20px; }
.clip-card { padding:10px; background:#1a1a2e; border-radius:6px; color:var(--text-primary); }
.comment-line { display:flex; gap:8px; padding:6px 10px; background:#1a1a2e; border-radius:6px; }
</style>
