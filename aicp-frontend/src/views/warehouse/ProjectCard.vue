<template>
  <div class="project-card" @click="$emit('open-detail', project)">
    <div class="card-header">
      <h4 class="card-title">{{ project.name }}</h4>
      <div class="card-meta">
        <el-tag size="small" type="info">{{ creationLabel }}</el-tag>
        <el-tag size="small" type="info">{{ sourceLabel }}</el-tag>
      </div>
    </div>

    <div class="card-statuses">
      <div class="status-row" v-for="s in viewModel.statuses" :key="s.axis">
        <span class="status-axis">{{ axisLabel(s.axis) }}</span>
        <el-tag :type="statusTagType(s.axis, s.value)" size="small">{{ s.label }}</el-tag>
      </div>
    </div>

    <div class="card-footer">
      <span class="card-time">{{ formatTime(project.updated_at) }}</span>
      <div class="card-actions" @click.stop>
        <el-button size="small" type="primary" @click="$emit('command', { action: 'continue', project })">
          {{ viewModel.primaryLabel }}
        </el-button>
        <el-dropdown trigger="click" @command="(cmd) => $emit('command', { action: cmd, project })">
          <el-button size="small">更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="rename">重命名</el-dropdown-item>
              <el-dropdown-item command="duplicate">复制项目</el-dropdown-item>
              <el-dropdown-item v-if="!viewModel.archived" command="archive">归档</el-dropdown-item>
              <el-dropdown-item v-if="viewModel.archived" command="restore">恢复</el-dropdown-item>
              <el-dropdown-item v-if="viewModel.archived" command="trash" divided>移入回收站</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import dayjs from 'dayjs'
import {
  projectCardViewModel, CREATION_MODE_LABELS, SOURCE_MODE_LABELS,
  CONTENT_STATUS_LABELS, PRODUCTION_STATUS_LABELS, COMMERCIAL_STATUS_LABELS
} from './projectWarehouseViewModel'

const props = defineProps({
  project: { type: Object, required: true }
})

defineEmits(['open-detail', 'command'])

const viewModel = computed(() => projectCardViewModel(props.project))

const creationLabel = computed(() => CREATION_MODE_LABELS[props.project.creation_mode] || props.project.creation_mode || '')
const sourceLabel = computed(() => SOURCE_MODE_LABELS[props.project.source_mode] || props.project.source_mode || '')

function axisLabel(axis) {
  return { content: '内容', production: '生产', commercial: '商业' }[axis] || axis
}

function statusTagType(axis, value) {
  if (axis === 'content') {
    return { draft: 'info', reviewing: 'warning', needs_revision: 'danger', approved: 'success', locked: '' }[value] || 'info'
  }
  if (axis === 'production') {
    return { not_started: 'info', storyboarding: 'warning', canvas_producing: '', completed: 'success' }[value] || 'info'
  }
  return { not_listed: 'info', listing_review: 'warning', listed: 'success', delisted: 'danger' }[value] || 'info'
}

function formatTime(time) {
  if (!time) return ''
  return dayjs(time).fromNow()
}
</script>

<style scoped>
.project-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
  cursor: pointer;
  transition: border-color .2s, box-shadow .2s;
}
.project-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64,158,255,.12);
}
.card-header {
  margin-bottom: 12px;
}
.card-title {
  margin: 0 0 6px 0;
  font-size: 15px;
  font-weight: 600;
}
.card-meta {
  display: flex;
  gap: 6px;
}
.card-statuses {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}
.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.status-axis {
  font-size: 12px;
  color: #909399;
  width: 40px;
}
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid #f2f3f5;
}
.card-time {
  font-size: 12px;
  color: #c0c4cc;
}
.card-actions {
  display: flex;
  gap: 6px;
}
</style>
