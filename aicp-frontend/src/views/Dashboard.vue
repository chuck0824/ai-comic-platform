<template>
  <div class="platform-home">
    <div class="home-header">
      <h1>AI 漫剧生产工作台</h1>
      <span class="home-header-subtitle">选择创作模式，开始新的内容项目</span>
    </div>

    <section class="home-section">
      <div class="creation-cards">
        <div v-for="card in creationCards" :key="card.mode" class="creation-card" @click="$router.push(`/script-gen/new?mode=${card.mode}`)">
          <el-icon :size="28" class="creation-card-icon"><component :is="card.icon" /></el-icon>
          <h3>{{ card.label }}</h3>
          <p>{{ card.description }}</p>
        </div>
      </div>
    </section>

    <section class="home-section">
      <h2 class="section-title">继续创作与生产</h2>
      <div v-if="loading" class="continue-list">
        <div v-for="i in 3" :key="i" class="continue-skeleton"><el-skeleton :rows="1" animated /></div>
      </div>
      <div v-else-if="viewModel.continueWorkingEmpty" class="empty-state">
        <el-empty description="暂无进行中的创作">
          <el-button type="primary" @click="$router.push('/script-gen/new')">开始新创作</el-button>
        </el-empty>
      </div>
      <div v-else class="continue-list">
        <div v-for="item in viewModel.continueWorking" :key="item.uuid || item.id" class="continue-item" @click="item.action.path && $router.push(item.action.path)">
          <div class="continue-item-main">
            <el-tag v-if="item.hasErrors" type="danger" size="small">异常</el-tag>
            <span class="continue-item-name">{{ item.name }}</span>
            <el-tag size="small">{{ item.stage || item.status }}</el-tag>
          </div>
          <div class="continue-item-meta">
            <span class="continue-item-time">{{ item.timeAgo }}</span>
            <el-button :type="item.hasErrors ? 'danger' : 'primary'" size="small" text>{{ item.action.label }}</el-button>
          </div>
        </div>
      </div>
    </section>

    <section class="home-section">
      <h2 class="section-title">画布生产 <router-link to="/canvas-projects" class="section-link">进入画布项目中心 →</router-link></h2>
      <div class="canvas-summary-grid">
        <div class="summary-item"><span class="summary-value">{{ viewModel.canvasSummary.active || 0 }}</span><span class="summary-label">进行中画布</span></div>
        <div class="summary-item"><span class="summary-value">{{ viewModel.canvasSummary.generating || 0 }}</span><span class="summary-label">生成中</span></div>
        <div class="summary-item error"><span class="summary-value">{{ viewModel.canvasSummary.errors || 0 }}</span><span class="summary-label">异常任务</span></div>
      </div>
    </section>

    <section class="home-section">
      <h2 class="section-title">内容与交易</h2>
      <div class="link-grid">
        <router-link to="/warehouse" class="link-card"><el-icon><Collection /></el-icon> 剧本仓库</router-link>
        <router-link to="/market" class="link-card"><el-icon><ShoppingBag /></el-icon> 剧本交易市场</router-link>
        <router-link to="/asset-market" class="link-card"><el-icon><Layers /></el-icon> AI 资产市场</router-link>
      </div>
    </section>

    <section class="home-section">
      <div class="metrics-grid">
        <div class="metric-item"><span class="metric-value">{{ viewModel.metrics.contentProjects }}</span><span class="metric-label">内容项目数</span></div>
        <div class="metric-item"><span class="metric-value">{{ viewModel.metrics.monthlyLockedScripts }}</span><span class="metric-label">本月锁稿数</span></div>
        <div class="metric-item"><span class="metric-value">{{ viewModel.metrics.generatedAssets }}</span><span class="metric-label">生成资产数</span></div>
        <div class="metric-item"><span class="metric-value">{{ viewModel.metrics.pendingTasks }}</span><span class="metric-label">待处理任务</span></div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { buildHomeViewModel } from './dashboard/homeViewModel.js'
import { canvasApi } from '@/api/canvas.js'

const creationCards = [
  { mode: 'short_drama', label: '短剧创作', description: '创建短剧内容项目，进入完整创作生产流程', icon: 'VideoCamera' },
  { mode: 'long_form', label: '长篇创作', description: '创建长篇内容项目，按章节管理故事结构', icon: 'Document' },
  { mode: 'tvc', label: 'TVC 创作', description: '创建 TVC 广告项目，按版本方案管理', icon: 'Promotion' }
]

const loading = ref(true)
const viewModel = ref(buildHomeViewModel({ continueWorking: [], canvasSummary: { active: 0, generating: 0, errors: 0 }, metrics: {} }))

onMounted(async () => {
  try {
    const res = await canvasApi.getHomeContinueWorking()
    const items = res?.data || []
    viewModel.value = buildHomeViewModel({ continueWorking: items, canvasSummary: computeCanvasSummary(items), metrics: {} })
  } catch { /* degrade gracefully */ }
  loading.value = false
})

function computeCanvasSummary(items) {
  const canvasItems = items.filter(i => i.itemType === 'canvas_project')
  return {
    active: canvasItems.filter(i => i.status !== 'archived' && i.status !== 'completed').length,
    generating: canvasItems.filter(i => i.status === 'generating').length,
    errors: canvasItems.filter(i => i.hasErrors).length
  }
}
</script>

<style scoped>
.platform-home { max-width: 1200px; margin: 0 auto; padding: 24px; }
.home-header { margin-bottom: 32px; }
.home-header h1 { font-size: 24px; margin: 0 0 4px; }
.home-header-subtitle { color: #71717a; font-size: 14px; }
.home-section { margin-bottom: 32px; }
.section-title { font-size: 16px; margin: 0 0 16px; display: flex; align-items: center; gap: 12px; }
.section-link { font-size: 13px; color: #409eff; text-decoration: none; margin-left: auto; }
.creation-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.creation-card { padding: 24px; border: 1px solid #e4e7ed; border-radius: 8px; cursor: pointer; transition: box-shadow .2s, border-color .2s; }
.creation-card:hover { border-color: #409eff; box-shadow: 0 2px 8px rgba(64,158,255,.15); }
.creation-card-icon { color: #409eff; margin-bottom: 12px; }
.creation-card h3 { margin: 0 0 8px; font-size: 15px; }
.creation-card p { margin: 0; color: #71717a; font-size: 13px; }
.continue-list { display: flex; flex-direction: column; gap: 8px; }
.continue-item { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border: 1px solid #e4e7ed; border-radius: 6px; cursor: pointer; }
.continue-item:hover { background: #f5f7fa; }
.continue-item-main { display: flex; align-items: center; gap: 8px; }
.continue-item-name { font-weight: 500; }
.continue-item-meta { display: flex; align-items: center; gap: 12px; }
.continue-item-time { color: #a1a1aa; font-size: 12px; }
.canvas-summary-grid { display: flex; gap: 24px; }
.summary-item { text-align: center; }
.summary-value { font-size: 28px; font-weight: 700; }
.summary-label { display: block; color: #71717a; font-size: 13px; margin-top: 4px; }
.summary-item.error .summary-value { color: #f56c6c; }
.link-grid { display: flex; gap: 16px; }
.link-card { flex: 1; display: flex; align-items: center; gap: 8px; padding: 16px; border: 1px solid #e4e7ed; border-radius: 6px; color: inherit; text-decoration: none; font-size: 14px; }
.link-card:hover { border-color: #409eff; color: #409eff; }
.metrics-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.metric-item { text-align: center; padding: 16px; background: #f5f7fa; border-radius: 6px; }
.metric-value { font-size: 24px; font-weight: 700; display: block; }
.metric-label { color: #71717a; font-size: 12px; margin-top: 4px; display: block; }
.empty-state { padding: 40px 0; }
.continue-skeleton { border: 1px solid #e4e7ed; border-radius: 6px; padding: 16px; }
</style>
