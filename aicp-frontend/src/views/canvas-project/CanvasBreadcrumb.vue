<template>
  <el-breadcrumb class="canvas-breadcrumb" separator="/">
    <el-breadcrumb-item v-for="(crumb, i) in crumbs" :key="i" :to="crumb.path ? { path: crumb.path } : undefined">
      {{ crumb.label }}
    </el-breadcrumb-item>
  </el-breadcrumb>
  <span v-if="statusTag" class="breadcrumb-tags">
    <el-tag size="small">{{ statusTag }}</el-tag>
    <el-tag v-if="purposeTag" size="small" type="info">{{ purposeTag }}</el-tag>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { buildBreadcrumb, STATUS_LABELS, PURPOSE_LABELS } from './canvasProjectViewModel.js'

const props = defineProps({
  page: { type: String, required: true },
  projectName: { type: String, default: null },
  canvasName: { type: String, default: null },
  projectId: [String, Number],
  referrer: { type: String, default: null },
  status: { type: String, default: null },
  purpose: { type: String, default: null }
})

const crumbs = computed(() => buildBreadcrumb(props.page, {
  projectName: props.projectName,
  canvasName: props.canvasName,
  projectId: props.projectId,
  referrer: props.referrer
}))

const statusTag = computed(() => STATUS_LABELS[props.status] || null)
const purposeTag = computed(() => PURPOSE_LABELS[props.purpose] || null)
</script>

<style scoped>
.canvas-breadcrumb { display: inline-flex; }
.breadcrumb-tags { display: inline-flex; gap: 6px; margin-left: 12px; vertical-align: middle; }
</style>
