<template>
  <div class="content-project-list">
    <div class="flex items-center justify-between mb-lg">
      <h2 class="text-xl font-bold">内容项目</h2>
      <el-button type="primary" @click="$router.push('/script-gen/new')">
        <el-icon><Plus /></el-icon> 新建项目
      </el-button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex justify-center py-xl">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="text-center py-xl">
      <p class="text-muted mb-md">{{ error }}</p>
      <el-button @click="fetchProjects">重试</el-button>
    </div>

    <!-- Empty -->
    <div v-else-if="projects.length === 0" class="text-center py-xl">
      <p class="text-muted mb-md">暂无内容项目，立即创建一个开始创作</p>
      <el-button type="primary" @click="$router.push('/script-gen/new')">新建项目</el-button>
    </div>

    <!-- Project Cards -->
    <div v-else class="flex flex-col gap-md">
      <div v-for="p in projects" :key="p.id" class="card card-hover" style="padding:16px" @click="openProject(p)">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-sm">
            <span class="font-semibold">{{ p.name }}</span>
            <span :class="['badge', modeBadgeClass(p.creation_mode)]">{{ modeLabel(p.creation_mode) }}</span>
            <span :class="['badge', sourceBadgeClass(p.source_mode)]">{{ p.source_mode === 'uploaded' ? '上传' : 'AI创作' }}</span>
            <span :class="['badge', statusBadgeClass(p.content_status)]">{{ statusLabel(p.content_status) }}</span>
          </div>
          <span class="text-sm text-muted">{{ formatTime(p.updated_at) }}</span>
        </div>
        <div class="flex items-center justify-between mt-sm">
          <div class="flex items-center gap-sm">
            <span v-if="p.last_stage_key" class="text-sm text-muted">
              当前阶段：{{ stageLabel(p.last_stage_key) }}
            </span>
            <span v-if="p.storyboard_intent_status === 'requested'" class="badge badge-accent">分镜制作中</span>
          </div>
          <el-button size="small" type="primary" @click.stop="openProject(p)">继续创作</el-button>
        </div>
      </div>

      <!-- Load More -->
      <div v-if="hasMore" class="text-center py-md">
        <el-button :loading="loadingMore" @click="loadMore">加载更多</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Loading } from '@element-plus/icons-vue'
import { contentProjectApi } from '@/api/contentProject'
import { stageLabel } from './utils/workflowPath'

const router = useRouter()
const projects = ref([])
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const page = ref(1)
const hasMore = ref(false)

onMounted(() => fetchProjects())

async function fetchProjects() {
  loading.value = true
  error.value = ''
  try {
    const res = await contentProjectApi.list({ page: 1, page_size: 20 })
    const data = res.data
    projects.value = data.items || data.records || []
    hasMore.value = data.pagination?.has_more || false
  } catch (e) {
    error.value = e.response?.data?.message || '加载项目列表失败'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  loadingMore.value = true
  try {
    page.value++
    const res = await contentProjectApi.list({ page: page.value, page_size: 20 })
    const data = res.data
    const items = data.items || data.records || []
    projects.value.push(...items)
    hasMore.value = data.pagination?.has_more || false
  } catch (e) {
    page.value--
  } finally {
    loadingMore.value = false
  }
}

function openProject(p) {
  router.push(`/script-gen/${p.id}/workspace`)
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diff = now - d
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN')
}

function modeLabel(m) {
  return { short_drama: '短剧', long_form: '长篇', tvc: 'TVC' }[m] || m
}

function statusLabel(s) {
  return { draft: '草稿', reviewing: '审核中', approved: '已通过', needs_revision: '需修改', locked: '已锁定' }[s] || s
}

function modeBadgeClass(m) {
  return { short_drama: 'badge-primary', long_form: 'badge-accent', tvc: 'badge-warning' }[m] || ''
}

function sourceBadgeClass(s) {
  return s === 'uploaded' ? 'badge-warning' : 'badge-success'
}

function statusBadgeClass(s) {
  return { draft: 'badge-warning', reviewing: 'badge-info', approved: 'badge-success', needs_revision: 'badge-danger', locked: '' }[s] || ''
}
</script>
