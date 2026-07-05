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
.home-header h1 { font-size: 26px; font-weight: 800; margin: 0 0 6px; letter-spacing: -.01em; }
.home-header-subtitle { color: var(--text-secondary); font-size: 15px; }
.home-section { margin-bottom: 36px; }
.section-title { font-size: 18px; font-weight: 700; margin: 0 0 16px; display: flex; align-items: center; gap: 12px; }
.section-link { font-size: 13px; color: var(--accent); text-decoration: none; margin-left: auto; font-weight: 500; }
.section-link:hover { text-decoration: underline; }

/* Creation Cards */
.creation-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.creation-card {
  padding: 24px; border: 1px solid var(--border); border-radius: var(--radius-lg);
  cursor: pointer; transition: box-shadow .2s ease, border-color .2s ease, transform .15s ease;
  background: var(--bg-surface);
}
.creation-card:hover {
  border-color: var(--accent-border);
  box-shadow: var(--shadow-md);
}
.creation-card:active { transform: scale(0.985); }
.creation-card-icon { color: var(--accent); margin-bottom: 12px; }
.creation-card h3 { margin: 0 0 8px; font-size: 16px; font-weight: 600; }
.creation-card p { margin: 0; color: var(--text-secondary); font-size: 14px; line-height: 1.5; }

/* Continue Working List */
.continue-list { display: flex; flex-direction: column; gap: 8px; }
.continue-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 16px; border: 1px solid var(--border); border-radius: var(--radius-md);
  cursor: pointer; transition: background .15s ease, border-color .15s ease;
  background: var(--bg-surface);
}
.continue-item:hover { background: var(--bg-surface-hover); border-color: var(--border); }
.continue-item-main { display: flex; align-items: center; gap: 8px; min-width: 0; }
.continue-item-name { font-weight: 500; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.continue-item-meta { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.continue-item-time { color: var(--text-tertiary); font-size: 13px; }

/* Canvas Summary */
.canvas-summary-grid { display: flex; gap: 32px; }
.summary-item { text-align: center; }
.summary-value { font-size: 30px; font-weight: 800; letter-spacing: -.02em; }
.summary-label { display: block; color: var(--text-secondary); font-size: 13px; margin-top: 4px; }
.summary-item.error .summary-value { color: var(--danger); }

/* Link Cards */
.link-grid { display: flex; gap: 16px; }
.link-card {
  flex: 1; display: flex; align-items: center; gap: 10px;
  padding: 18px 20px; border: 1px solid var(--border); border-radius: var(--radius-md);
  color: inherit; text-decoration: none; font-size: 15px; font-weight: 500;
  transition: border-color .2s ease, box-shadow .2s ease, color .2s ease;
  background: var(--bg-surface);
}
.link-card:hover {
  border-color: var(--accent-border);
  color: var(--accent);
  box-shadow: var(--shadow-sm);
}

/* Metrics Grid */
.metrics-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.metric-item {
  text-align: center; padding: 20px 16px;
  background: var(--bg-surface); border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
}
.metric-value { font-size: 26px; font-weight: 800; display: block; letter-spacing: -.02em; }
.metric-label { color: var(--text-secondary); font-size: 13px; margin-top: 4px; display: block; }

.empty-state { padding: 40px 0; }
.continue-skeleton { border: 1px solid var(--border); border-radius: var(--radius-md); padding: 16px; background: var(--bg-surface); }

/* ===== RESPONSIVE ===== */
@media (max-width: 1024px) {
  .creation-cards { grid-template-columns: repeat(2, 1fr); }
  .metrics-grid { grid-template-columns: repeat(2, 1fr); }
  .link-grid { flex-direction: column; }
}
@media (max-width: 768px) {
  .platform-home { padding: 12px; }
  .home-header h1 { font-size: 22px; }
  .creation-cards { grid-template-columns: 1fr; }
  .metrics-grid { grid-template-columns: repeat(2, 1fr); gap: 8px; }
  .canvas-summary-grid { gap: 16px; flex-wrap: wrap; }
  .continue-item { flex-direction: column; align-items: flex-start; gap: 8px; }
  .continue-item-meta { width: 100%; justify-content: space-between; }
}
</style>
