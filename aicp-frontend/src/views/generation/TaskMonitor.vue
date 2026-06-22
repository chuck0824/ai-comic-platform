<template>
  <div class="task-monitor-page">
    <div class="page-header">
      <h2><el-icon><DataAnalysis /></el-icon> 任务监控</h2>
      <el-button size="small" @click="refresh"><el-icon><Refresh /></el-icon> 刷新</el-button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-value">{{ stats.running }}</div>
        <div class="stat-label">运行中</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:#34d399">{{ stats.succeeded }}</div>
        <div class="stat-label">已完成</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:#f87171">{{ stats.failed }}</div>
        <div class="stat-label">失败</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.pending }}</div>
        <div class="stat-label">排队中</div>
      </div>
    </div>

    <div class="task-list">
      <div v-for="task in tasks" :key="task.uuid || task.id" class="task-item">
        <div class="task-info">
          <span :class="['badge', statusBadge(task.status)]">{{ task.status }}</span>
          <strong>{{ task.type }}</strong>
          <span class="text-muted text-sm">{{ task.model_id }}</span>
          <span class="text-muted text-sm">{{ task.uuid }}</span>
        </div>
        <div class="task-progress">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: task.progress + '%' }"
                 :class="task.status" />
          </div>
          <span class="text-sm">{{ task.progress }}%</span>
        </div>
        <div class="task-cost"><el-icon><Coin /></el-icon> {{ task.credit_cost || 0 }}积分</div>
        <div class="task-actions">
          <el-button v-if="task.status === 'failed'" size="small" @click="retry(task)"><el-icon><Refresh /></el-icon> 重试</el-button>
          <el-button v-if="task.status === 'pending' || task.status === 'running'" size="small" @click="cancel(task)"><el-icon><VideoPause /></el-icon> 取消</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { generationApi } from '@/api/generation'
import { scriptApi } from '@/api/script'

const tasks = ref([])
const stats = reactive({ running: 0, succeeded: 0, failed: 0, pending: 0 })
let refreshTimer = null

onMounted(() => { refresh(); refreshTimer = setInterval(refresh, 10000) })

// 组件卸载时清除定时器（Vue 3 自动处理，此处显式释放）
import { onUnmounted } from 'vue'
onUnmounted(() => clearInterval(refreshTimer))

async function refresh() {
  try {
    // 从脚本生成历史获取任务（gen_tasks 表）
    const res = await scriptApi.getTaskHistory({ page: 1, page_size: 20 })
    const items = res.data?.items || res.data?.records || []
    if (items.length) {
      tasks.value = items.map(t => ({
        uuid: t.task_id || t.uuid,
        type: t.gen_type || t.type || 'unknown',
        model_id: t.model_used || t.model_id || '—',
        status: t.status || 'pending',
        progress: t.progress || 0,
        credit_cost: t.tokens_used || 0,
        error_message: t.error_msg || t.error_message || ''
      }))
    }
  } catch {
    // 后端不可用时保留已有数据
  }
  stats.running = tasks.value.filter(t => t.status === 'running' || t.status === 'processing').length
  stats.succeeded = tasks.value.filter(t => t.status === 'succeeded' || t.status === 'completed').length
  stats.failed = tasks.value.filter(t => t.status === 'failed').length
  stats.pending = tasks.value.filter(t => t.status === 'pending').length
}

async function retry(task) {
  try { await generationApi.retryTask(task.uuid); refresh() }
  catch (e) { console.error(e) }
}

async function cancel(task) {
  try { await generationApi.cancelTask(task.uuid); refresh() }
  catch (e) { console.error(e) }
}

function statusBadge(s) {
  return {
    'badge-warning': s === 'running' || s === 'pending',
    'badge-success': s === 'succeeded',
    'badge-danger': s === 'failed' || s === 'canceled'
  }
}
</script>

<style scoped>
.task-monitor-page { padding: 24px; background: #0f172a; min-height: 100vh; color: #e0e0e0; --text-secondary:#a1a1aa; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
h2 { margin: 0; }
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 24px; }
.stat-card { background: #1a1a2e; border-radius: 10px; padding: 20px; text-align: center; color:#e0e0e0; }
.stat-value { font-size: 28px; font-weight: 700; }
.stat-label { font-size: 12px; color: #888; margin-top: 4px; }
.task-list { display: flex; flex-direction: column; gap: 8px; }
.task-item { background: #1a1a2e; border-radius: 8px; padding: 14px 18px; color:#e0e0e0;
  display: flex; align-items: center; gap: 16px; }
.task-info { display: flex; align-items: center; gap: 10px; flex: 1; }
.task-progress { display: flex; align-items: center; gap: 8px; width: 200px; }
.progress-bar { flex: 1; height: 6px; background: #2a2a3e; border-radius: 3px; overflow: hidden; }
.progress-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
.progress-fill.running, .progress-fill.pending { background: #f59e0b; }
.progress-fill.succeeded { background: #34d399; }
.progress-fill.failed { background: #f87171; }
.task-cost { color: #fbbf24; font-size: 13px; min-width: 60px; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; }
.badge-warning { background: #78350f; color: #fbbf24; }
.badge-success { background: #064e3b; color: #34d399; }
.badge-danger { background: #7f1d1d; color: #f87171; }
.text-muted { color: #a1a1aa; } .text-sm { font-size: 12px; }
</style>
