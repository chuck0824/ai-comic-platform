<template>
  <div class="script-creation-home">
    <div class="page-header">
      <h2>剧本创作</h2>
      <p class="subtitle">选择创建方式，开始新的创作</p>
    </div>

    <!-- Creation Methods -->
    <div class="creation-methods">
      <div
        v-for="method in methods"
        :key="method.key"
        class="method-card card card-interactive"
        @click="router.push(method.route)"
        role="button"
        :aria-label="method.label"
        tabindex="0"
        @keydown.enter="router.push(method.route)"
      >
        <div class="method-icon">
          <el-icon :size="24"><component :is="method.icon" /></el-icon>
        </div>
        <div class="method-body">
          <h4>{{ method.label }}</h4>
          <p>{{ method.desc }}</p>
        </div>
        <el-icon class="method-arrow"><ArrowRight /></el-icon>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="section">
      <div class="skeleton-card card" style="height:60px"></div>
      <div class="skeleton-card card" style="height:60px;margin-top:8px"></div>
      <div class="skeleton-card card" style="height:60px;margin-top:8px"></div>
    </div>

    <!-- Error state -->
    <div v-if="error" class="section">
      <el-alert type="warning" :title="error" show-icon :closable="false" />
      <el-button type="primary" @click="loadData" style="margin-top:12px">重试</el-button>
    </div>

    <!-- Todos -->
    <div v-if="!loading && !error && todos.length > 0" class="section">
      <h3 class="section-title">待办提醒</h3>
      <div class="todo-list">
        <div
          v-for="todo in todos"
          :key="todo.project_id"
          class="todo-item card card-interactive"
          @click="router.push(todo.route)"
        >
          <el-tag :type="todo.type === 'pending_review' ? 'warning' : 'danger'" size="small" effect="light">
            {{ todo.label }}
          </el-tag>
          <span class="todo-name">{{ todo.project_name }}</span>
          <span class="todo-time">{{ formatTime(todo.updated_at) }}</span>
          <el-icon :size="16"><ArrowRight /></el-icon>
        </div>
      </div>
    </div>

    <!-- Recent Projects -->
    <div v-if="!loading && !error" class="section">
      <h3 class="section-title">最近创作</h3>
      <div v-if="recent.length === 0" class="empty-hint">
        <div class="empty-state-icon">📝</div>
        <p>还没有创作项目，选择一个创建方式开始吧</p>
      </div>
      <div class="recent-list">
        <div
          v-for="project in recent"
          :key="project.id"
          class="recent-card card"
        >
          <div class="recent-info" @click="openDetail(project)" role="button" :aria-label="`打开${project.name}`" tabindex="0" @keydown.enter="openDetail(project)">
            <h4>{{ project.name }}</h4>
            <div class="recent-meta">
              <el-tag size="small" type="info" effect="light">{{ modeLabel(project.creation_mode) }}</el-tag>
              <span class="recent-status" :class="'status-' + project.content_status">{{ statusLabel(project.content_status) }}</span>
              <span class="recent-time">{{ formatTime(project.updated_at) }}</span>
            </div>
          </div>
          <div class="recent-actions">
            <el-button size="small" type="primary" @click="continueCreation(project)">
              <el-icon><EditPen /></el-icon> 继续创作
            </el-button>
            <el-button size="small" @click="openDetail(project)">
              查看详情
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, EditPen } from '@element-plus/icons-vue'
import { contentProjectApi } from '@/api/contentProject'
import { CREATION_METHODS } from './scriptCreationHomeViewModel'
import { CONTENT_STATUS_LABELS, CREATION_MODE_LABELS, primaryActionRoute } from '../warehouse/projectWarehouseViewModel'
import dayjs from 'dayjs'

const router = useRouter()
const methods = CREATION_METHODS
const recent = ref([])
const todos = ref([])
const loading = ref(true)
const error = ref('')

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [recentRes, todosRes] = await Promise.allSettled([
      contentProjectApi.recent(5),
      contentProjectApi.todos()
    ])
    recent.value = recentRes.status === 'fulfilled' ? (recentRes.value.data?.data || []) : []
    todos.value = todosRes.status === 'fulfilled' ? (todosRes.value.data?.data || []) : []
  } catch (e) {
    error.value = '加载失败，请检查网络后重试'
    console.error('ScriptCreationHome load error:', e)
  } finally {
    loading.value = false
  }
}

function openDetail(project) {
  router.push(primaryActionRoute({ id: project.id, primary_action: project.primary_action }))
}

function continueCreation(project) {
  router.push(`/script-gen/${project.id}/workspace`)
}

function modeLabel(mode) {
  return CREATION_MODE_LABELS[mode] || mode || '未知'
}

function statusLabel(status) {
  return CONTENT_STATUS_LABELS[status] || status || '草稿'
}

function formatTime(time) {
  if (!time) return ''
  return dayjs(time).fromNow()
}

onMounted(loadData)
</script>

<style scoped>
.script-creation-home {
  max-width: 960px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}
.page-header h2 {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -.01em;
  margin: 0 0 4px 0;
}
.subtitle {
  color: var(--text-secondary);
  font-size: 14px;
  margin: 0;
}

/* ===== Creation Methods ===== */
.creation-methods {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 32px;
}
.method-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}
.method-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-sm);
  background: var(--accent-bg);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background .2s ease, color .2s ease;
}
.method-card:hover .method-icon {
  background: var(--accent);
  color: #fff;
}
.method-body h4 {
  margin: 0 0 4px 0;
  font-size: 15px;
  font-weight: 600;
}
.method-body p {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}
.method-arrow {
  color: var(--text-tertiary);
  margin-left: auto;
  flex-shrink: 0;
  transition: transform .2s ease, color .2s ease;
}
.method-card:hover .method-arrow {
  transform: translateX(2px);
  color: var(--accent);
}

/* ===== Sections ===== */
.section {
  margin-bottom: 28px;
}
.section-title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -.01em;
  margin: 0 0 12px 0;
}

/* ===== Recent List ===== */
.recent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.recent-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
}
.recent-info {
  cursor: pointer;
  flex: 1;
  min-width: 0;
}
.recent-info h4 {
  margin: 0 0 6px 0;
  font-size: 15px;
  font-weight: 600;
}
.recent-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
.recent-status {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
}
.recent-status.status-approved {
  color: var(--success);
  background: var(--success-bg);
}
.recent-status.status-reviewing {
  color: var(--warning);
  background: var(--warning-bg);
}
.recent-status.status-needs_revision {
  color: var(--danger);
  background: var(--danger-bg);
}
.recent-time {
  color: var(--text-tertiary);
}
.recent-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 16px;
}

/* ===== Todo List ===== */
.todo-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.todo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
}
.todo-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
}
.todo-time {
  font-size: 12px;
  color: var(--text-tertiary);
}

/* ===== Empty ===== */
.empty-hint {
  text-align: center;
  padding: 40px 24px;
  color: var(--text-secondary);
  font-size: 14px;
}

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .creation-methods {
    grid-template-columns: 1fr;
  }
  .recent-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .recent-actions {
    margin-left: 0;
    width: 100%;
  }
}
</style>
