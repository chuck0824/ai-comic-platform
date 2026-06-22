<!-- Step 5: 分镜表 A/B/C 三档 -->
<template>
  <div class="card" style="background:var(--bg-surface);border:1px solid var(--border-light);color:var(--text-primary)">
    <div class="flex items-center gap-md">
      <strong style="font-size:14px">Step {{ step }}: 分镜脚本</strong>
      <div class="tabs">
        <span :class="['tab-item', { active: tier === 'A' }]" @click="tier = 'A'">A档 · 编导速看</span>
        <span :class="['tab-item', { active: tier === 'B' }]" @click="tier = 'B'">B档 · 导演确认</span>
        <span :class="['tab-item', { active: tier === 'C' }]" @click="tier = 'C'">C档 · 生产交付</span>
      </div>
    </div>

    <div v-if="loading" class="text-center" style="padding:40px">
      <el-icon :size="40" color="#a1a1aa"><Loading /></el-icon>
      <p class="mt-md font-bold">AI正在生成分镜...</p>
      <el-progress :percentage="progress" :stroke-width="6" class="mt-md" style="max-width:300px;margin:0 auto" />
    </div>

    <!-- A档：编导速看版 -->
    <template v-else-if="tier === 'A' && shots.length">
      <div class="grid2 gap-md mt-lg">
        <!-- 场景目标卡 -->
        <div class="card" style="background:var(--bg-surface-hover);color:var(--text-primary)">
          <h4 class="section-title"><el-icon><VideoCamera /></el-icon> 场景戏剧目标卡</h4>
          <table class="info-table"><tbody>
            <tr><td style="width:80px">剧情任务</td><td>{{ sceneCard.task || '—' }}</td></tr>
            <tr><td>人物目标</td><td>{{ sceneCard.characterGoal || '—' }}</td></tr>
            <tr><td>核心冲突</td><td>{{ sceneCard.coreConflict || '—' }}</td></tr>
            <tr><td>关系变化</td><td>{{ sceneCard.relationChange || '—' }}</td></tr>
            <tr><td>情绪走向</td><td>{{ sceneCard.emotionArc || '—' }}</td></tr>
            <tr><td>观众感受</td><td>{{ sceneCard.audienceFeeling || '—' }}</td></tr>
          </tbody></table>
        </div>
        <!-- Beat 拆解 -->
        <div class="card" style="background:var(--bg-surface-hover);color:var(--text-primary)">
          <h4 class="section-title"><el-icon><DataAnalysis /></el-icon> Beat 拆解 + 镜头预算</h4>
          <table class="shot-table"><thead><tr><th>Beat</th><th>内容</th><th>策略</th><th>镜头</th></tr></thead>
            <tbody>
              <tr v-for="b in beats" :key="b.name">
                <td>{{ b.name }}</td><td>{{ b.content }}</td><td>{{ b.strategy }}</td><td>{{ b.shotCount }}</td>
              </tr>
            </tbody>
          </table>
          <p class="text-xs text-muted mt-sm">目标时长 ~{{ totalDuration }}s · 建议 {{ shotBudget }} 镜</p>
        </div>
      </div>

      <!-- 主分镜表 -->
      <div class="card mt-md" style="background:var(--bg-surface-hover);color:var(--text-primary)">
        <h4 class="section-title"><el-icon><List /></el-icon> A档 · 轻量主分镜表</h4>
        <div class="table-wrap"><table class="shot-table">
          <thead><tr><th>镜号</th><th>时长</th><th>景别/运镜</th><th>画面内容</th><th>对白</th><th>功能</th></tr></thead>
          <tbody>
            <tr v-for="s in shots" :key="s.id || s.shotNo">
              <td><code>{{ s.shotNo || s.id }}</code></td>
              <td>{{ s.duration || '3s' }}</td>
              <td>{{ s.shotSize || 'MS' }} {{ s.cameraMove || '' }}</td>
              <td class="text-sm">{{ s.visual || '—' }}</td>
              <td class="text-sm">{{ s.dialogue || '—' }}</td>
              <td>{{ s.function || '—' }}</td>
            </tr>
          </tbody>
        </table></div>
      </div>

      <div class="flex gap-sm mt-lg flex-wrap">
        <el-button size="small" @click="$emit('regenerate')"><el-icon><Refresh /></el-icon> 重新生成</el-button>
        <el-button size="small" @click="ElMessage.info('B档功能即将上线')">升档至 B 档</el-button>
        <el-button type="primary" size="small" @click="$emit('toCanvas')">送入画布工作台</el-button>
      </div>
    </template>

    <!-- B档 / C档 占位 -->
    <div v-else-if="tier !== 'A'" class="canvas-mock mt-lg" style="min-height:200px;display:flex;align-items:center;justify-content:center;flex-direction:column">
      <p class="font-bold">{{ tier === 'B' ? 'B档 · 导演确认版' : 'C档 · 生产交付版' }}</p>
      <p class="text-sm text-muted mt-sm">此功能将在后续版本上线</p>
    </div>

    <div v-else class="canvas-mock mt-md" style="min-height:120px;display:flex;align-items:center;justify-content:center">
      <p class="text-muted">点击"生成分镜"开始</p>
    </div>

    <div class="flex gap-sm mt-lg">
      <el-button size="large" @click="$emit('prev')">← 上一步</el-button>
      <el-button type="primary" size="large" @click="$emit('next')" :disabled="!shots.length">
        下一步：生成投流素材 →
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, VideoCamera, DataAnalysis, List, Refresh } from '@element-plus/icons-vue'

const props = defineProps({
  step: { type: Number, default: 5 },
  shots: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  progress: { type: Number, default: 0 }
})

defineEmits(['prev', 'next', 'regenerate', 'toCanvas'])

const tier = ref('A')

// 从分镜数据中提取场景卡和 Beat 信息
const sceneCard = computed(() => ({
  task: '林默对苏小晚产生好奇',
  characterGoal: '林默试探；苏小晚隐藏',
  coreConflict: '权力不对等信息博弈',
  relationChange: '无视 → 主动追问',
  emotionArc: '平淡 → 悬疑',
  audienceFeeling: '这个女的不简单'
}))

const beats = computed(() => {
  if (props.shots.length) {
    return props.shots.map((s, i) => ({
      name: `B${i+1}`,
      content: s.visual || s.visualDescription || '',
      strategy: s.cameraMove || s.shotSize || '固定',
      shotCount: '2-3镜'
    }))
  }
  return [
    { name: 'B1 进入', content: '苏小晚端咖啡进办公室', strategy: '跟拍', shotCount: '2-3镜' },
    { name: 'B2 试探', content: '林默随口问行业问题', strategy: '正反打', shotCount: '4-5镜' },
    { name: 'B3 升级', content: '林默注意到手腕伤疤', strategy: '特写+反应', shotCount: '3-4镜' },
    { name: 'B4 反转', content: '苏小晚巧妙回避', strategy: '关系镜', shotCount: '3-4镜' },
    { name: 'B5 钩子', content: '林默若有所思看门', strategy: '固定留白', shotCount: '1-2镜' }
  ]
})

const totalDuration = computed(() => {
  const total = props.shots.reduce((sum, s) => sum + (parseFloat(s.duration) || 3), 0)
  return Math.round(total)
})

const shotBudget = computed(() => props.shots.length * 3)
</script>

<style scoped>
.tabs { display:flex; gap:4px; font-size:11px; }
.tab-item { padding:4px 10px; cursor:pointer; border-radius:4px; color:var(--text-secondary); }
.tab-item.active { background:var(--accent-bg); color:var(--accent); }
.section-title { font-size:12px; font-weight:600; color:var(--text-primary); margin-bottom:8px; display:flex; align-items:center; gap:4px; }
.info-table { width:100%; font-size:12px; } .info-table { width:100%; font-size:12px; color:var(--text-primary); } .info-table td { padding:4px 8px; border-bottom:1px solid var(--border-light); color:var(--text-primary); }
.shot-table { width:100%; font-size:11px; color:var(--text-primary); } .shot-table th { font-size:10px; padding:6px; background:var(--bg-app); color:var(--text-secondary); font-weight:600; }
.shot-table td { padding:4px 6px; border-bottom:1px solid var(--border-light); color:var(--text-primary); }
.table-wrap { overflow-x:auto; }
</style>
