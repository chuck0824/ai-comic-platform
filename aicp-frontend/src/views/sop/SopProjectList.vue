<template>
  <div class="sop-project-list">
    <h2>生产 SOP</h2>
    <p class="text-muted">选择项目查看生产准入状态与返工工单</p>

    <el-skeleton v-if="loading" :rows="5" animated />

    <el-result v-else-if="error" icon="error" title="加载失败" :sub-title="error">
      <template #extra>
        <el-button type="primary" @click="loadProjects">重试</el-button>
      </template>
    </el-result>

    <el-empty v-else-if="projects.length === 0" description="暂无有权限的项目" />

    <div v-else class="project-grid">
      <div
        v-for="project in projects"
        :key="project.projectId"
        class="project-card"
        @click="$router.push(`/content-projects/${project.projectId}/sop`)"
      >
        <div class="project-card-header">
          <span class="project-name">{{ project.projectName || `项目 #${project.projectId}` }}</span>
          <el-tag :type="overallStatusColor(project.overallStatus)" size="small">
            {{ overallStatusLabel(project.overallStatus) }}
          </el-tag>
        </div>
        <div class="project-card-stats">
          <span class="stat blocked">阻断: {{ project.blockedCount ?? 0 }}</span>
          <span class="stat warning">告警: {{ project.warningCount ?? 0 }}</span>
        </div>
        <div class="project-card-footer">
          <span class="text-muted" v-if="project.lastCheckAt">
            最近检查: {{ new Date(project.lastCheckAt).toLocaleString('zh-CN') }}
          </span>
          <span class="text-muted" v-else>尚未检查</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { sopApi } from '@/api/sop.js'
import { overallStatusLabel, overallStatusColor } from './sopState.js'

const loading = ref(true)
const error = ref('')
const projects = ref([])

async function loadProjects() {
  loading.value = true
  error.value = ''
  try {
    const res = await sopApi.listProjects({ page: 1, size: 50 })
    // API may return null for Phase 1 — handle gracefully
    projects.value = res.data?.data?.items || []
  } catch (e) {
    error.value = e?.response?.data?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadProjects)
</script>

<style scoped>
.sop-project-list {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  margin-top: 16px;
}
.project-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: box-shadow 0.2s;
  background: var(--el-bg-color);
}
.project-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.project-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.project-name {
  font-weight: 600;
  font-size: 15px;
}
.project-card-stats {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 13px;
}
.stat.blocked { color: var(--el-color-danger); }
.stat.warning { color: var(--el-color-warning); }
.project-card-footer {
  font-size: 12px;
}
.text-muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
h2 { margin-bottom: 4px; }
</style>
