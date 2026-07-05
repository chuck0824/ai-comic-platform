<template>
  <div class="project-detail-page" v-if="project">
    <!-- Header -->
    <div class="detail-header">
      <div class="header-left">
        <el-button @click="$router.push('/warehouse')" text><el-icon><ArrowLeft /></el-icon> 返回仓库</el-button>
        <h2>{{ project.name }}</h2>
        <div class="header-meta">
          <el-tag size="small">{{ modeLabel }}</el-tag>
          <el-tag size="small" type="info">{{ sourceLabel }}</el-tag>
          <span class="header-time">更新于 {{ formatTime(project.updated_at) }}</span>
        </div>
      </div>
      <div class="header-right">
        <el-button
          type="primary"
          :disabled="primary.disabled"
          @click="runPrimary"
        >
          {{ primary.label }}
        </el-button>
        <span v-if="primary.blockedReason" class="blocked-hint">{{ primary.blockedReason }}</span>
      </div>
    </div>

    <!-- Status Axes -->
    <div class="status-axes" v-if="summary">
      <div class="axis-chip" v-for="s in axes" :key="s.key">
        <span class="axis-label">{{ s.label }}</span>
        <el-tag :type="s.tagType" size="small">{{ s.value }}</el-tag>
      </div>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane v-for="tab in tabs" :key="tab.key" :label="tab.label" :name="tab.key" />
    </el-tabs>

    <!-- Tab Content -->
    <div class="tab-content">
      <!-- Overview -->
      <div v-if="activeTab === 'overview'" class="tab-panel">
        <div class="timeline-section">
          <h4>项目流转</h4>
          <el-steps :active="flowStep" align-center>
            <el-step title="创建" />
            <el-step title="创作" />
            <el-step title="审核" />
            <el-step title="锁稿" />
            <el-step title="分镜" />
            <el-step title="画布" />
            <el-step title="完成" />
          </el-steps>
        </div>
        <div class="info-grid" v-if="summary">
          <div class="info-item">
            <span class="info-label">内容状态</span>
            <span class="info-value">{{ summary.content_status }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">生产状态</span>
            <span class="info-value">{{ summary.production_status }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">商业状态</span>
            <span class="info-value">{{ summary.commercial_status }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">生命周期</span>
            <span class="info-value">{{ summary.lifecycle_status }}</span>
          </div>
        </div>
      </div>

      <!-- Versions -->
      <div v-else-if="activeTab === 'versions'" class="tab-panel">
        <el-empty description="版本管理功能开发中" />
      </div>

      <!-- Settings -->
      <div v-else-if="activeTab === 'settings'" class="tab-panel">
        <el-empty description="设定资料功能开发中" />
      </div>

      <!-- Review -->
      <div v-else-if="activeTab === 'review'" class="tab-panel">
        <el-empty description="审核记录功能开发中" />
      </div>

      <!-- Storyboard -->
      <div v-else-if="activeTab === 'storyboard'" class="tab-panel">
        <el-empty description="分镜关联功能开发中" />
      </div>

      <!-- Production -->
      <div v-else-if="activeTab === 'production'" class="tab-panel">
        <el-empty description="生产关联功能开发中" />
      </div>

      <!-- Commerce -->
      <div v-else-if="activeTab === 'commerce'" class="tab-panel">
        <el-empty description="商业记录功能开发中" />
      </div>
    </div>
  </div>

  <!-- Loading -->
  <div v-else-if="loading" class="loading-state">
    <el-skeleton :rows="8" animated />
  </div>

  <!-- Error -->
  <div v-else-if="error" class="error-state">
    <el-result icon="error" title="加载失败" :sub-title="error">
      <template #extra><el-button @click="$router.push('/warehouse')">返回仓库</el-button></template>
    </el-result>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { contentProjectApi } from '@/api/contentProject'
import { primaryActionRoute, PRIMARY_LABELS, CREATION_MODE_LABELS, SOURCE_MODE_LABELS, CONTENT_STATUS_LABELS, PRODUCTION_STATUS_LABELS, COMMERCIAL_STATUS_LABELS } from '../warehouse/projectWarehouseViewModel'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()

const PROJECT_DETAIL_TABS = [
  { key: 'overview', label: '概览' },
  { key: 'versions', label: '正文与版本' },
  { key: 'settings', label: '设定资料' },
  { key: 'review', label: '审核记录' },
  { key: 'storyboard', label: '分镜' },
  { key: 'production', label: '生产关联' },
  { key: 'commerce', label: '商业记录' }
]

const loading = ref(true)
const error = ref('')
const project = ref(null)
const summary = ref(null)
const activeTab = ref('overview')

const tabs = PROJECT_DETAIL_TABS

const modeLabel = computed(() => CREATION_MODE_LABELS[project.value?.creation_mode] || '')
const sourceLabel = computed(() => SOURCE_MODE_LABELS[project.value?.source_mode] || '')

const primary = computed(() => {
  const action = summary.value?.primary_action
  return {
    action,
    label: PRIMARY_LABELS[action] || '查看详情',
    route: primaryActionRoute({ id: project.value?.id, primary_action: action }),
    disabled: Boolean(summary.value?.blocked_reason),
    blockedReason: summary.value?.blocked_reason || ''
  }
})

const axes = computed(() => {
  if (!summary.value) return []
  return [
    { key: 'content', label: '内容', value: CONTENT_STATUS_LABELS[summary.value.content_status] || summary.value.content_status, tagType: statusTagType('content', summary.value.content_status) },
    { key: 'production', label: '生产', value: PRODUCTION_STATUS_LABELS[summary.value.production_status] || summary.value.production_status, tagType: statusTagType('production', summary.value.production_status) },
    { key: 'commercial', label: '商业', value: COMMERCIAL_STATUS_LABELS[summary.value.commercial_status] || summary.value.commercial_status, tagType: statusTagType('commercial', summary.value.commercial_status) }
  ]
})

const flowStep = computed(() => {
  const s = project.value?.content_status
  if (!s) return 0
  if (s === 'draft') return 1
  if (s === 'reviewing' || s === 'needs_revision') return 2
  if (s === 'approved') return 3
  if (s === 'locked') {
    const ps = summary.value?.production_status
    if (ps === 'not_started') return 3
    if (ps === 'storyboarding') return 4
    if (ps === 'canvas_producing') return 5
    return 6
  }
  return 0
})

function statusTagType(axis, value) {
  if (axis === 'content') {
    return { draft: 'info', reviewing: 'warning', needs_revision: 'danger', approved: 'success', locked: '' }[value] || 'info'
  }
  if (axis === 'production') {
    return { not_started: 'info', storyboarding: 'warning', canvas_producing: '', completed: 'success' }[value] || 'info'
  }
  return { not_listed: 'info', listing_review: 'warning', listed: 'success', delisted: 'danger' }[value] || 'info'
}

function runPrimary() {
  if (!primary.value.disabled && primary.value.route) {
    router.push(primary.value.route)
  }
}

function formatTime(time) {
  if (!time) return ''
  return dayjs(time).fromNow()
}

async function loadData() {
  loading.value = true
  error.value = ''
  const projectId = route.params.projectId
  try {
    const [detailRes, summaryRes] = await Promise.allSettled([
      contentProjectApi.get(projectId),
      contentProjectApi.summary(projectId)
    ])
    if (detailRes.status === 'fulfilled') {
      project.value = detailRes.value.data?.data || null
    }
    if (summaryRes.status === 'fulfilled') {
      summary.value = summaryRes.value.data?.data?.summary || null
    }
    if (!project.value) {
      error.value = '项目不存在'
    }
  } catch (e) {
    error.value = e?.response?.data?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => route.params.projectId, () => {
  if (route.params.projectId) loadData()
})

// Set initial tab from query param
if (route.query.tab) {
  const validTabs = PROJECT_DETAIL_TABS.map(t => t.key)
  if (validTabs.includes(route.query.tab)) {
    activeTab.value = route.query.tab
  }
}

onMounted(loadData)
</script>

<style scoped>
.project-detail-page { max-width: 1000px; margin: 0 auto; padding: 24px; }
.detail-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
.header-left h2 { margin: 8px 0 4px 0; font-size: 22px; }
.header-meta { display: flex; gap: 8px; align-items: center; }
.header-time { font-size: 12px; color: #909399; }
.header-right { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.blocked-hint { font-size: 12px; color: #f56c6c; }
.status-axes { display: flex; gap: 16px; margin-bottom: 20px; padding: 12px 16px; background: #f5f7fa; border-radius: 8px; }
.axis-chip { display: flex; align-items: center; gap: 6px; }
.axis-label { font-size: 13px; color: #606266; }
.detail-tabs { margin-bottom: 8px; }
.tab-panel { padding: 16px 0; }
.timeline-section { margin-bottom: 24px; }
.timeline-section h4 { margin: 0 0 12px 0; }
.info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-top: 20px; }
.info-item { display: flex; flex-direction: column; gap: 4px; padding: 10px 14px; background: #f5f7fa; border-radius: 6px; }
.info-label { font-size: 12px; color: #909399; }
.info-value { font-size: 14px; font-weight: 500; }
.loading-state, .error-state { padding: 60px 0; text-align: center; max-width: 1000px; margin: 0 auto; }
</style>
