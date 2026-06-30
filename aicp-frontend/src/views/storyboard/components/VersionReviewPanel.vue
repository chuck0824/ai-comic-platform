<template>
  <div class="panel" data-testid="review-panel">
    <div class="panel-header"><h3>版本与审核</h3></div>

    <!-- Version list -->
    <el-card header="版本历史" class="section">
      <el-timeline v-if="versions.length">
        <el-timeline-item
          v-for="v in versions"
          :key="v.id"
          :timestamp="v.createdAt"
          :color="v.id === versionId ? '#409eff' : '#c0c4cc'"
        >
          {{ v.tier }}档 v{{ v.versionNo }} — {{ statusMap[v.status] || v.status }}
          <el-button v-if="v.id !== versionId" text size="small" type="primary" @click="$emit('switch-version', v.id)">
            切换
          </el-button>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- Review issues -->
    <el-card header="审核问题" class="section">
      <el-empty v-if="!issues.length" description="暂无审核问题" :image-size="60" />
      <div v-else v-for="issue in issues" :key="issue.id" class="issue-item">
        <div class="issue-header">
          <el-tag :type="issue.severity === 'error' ? 'danger' : 'warning'" size="small">{{ issue.severity }}</el-tag>
          <span class="issue-type">{{ issue.issueType }}</span>
          <el-tag size="small">{{ issue.status }}</el-tag>
        </div>
        <div class="issue-message">{{ issue.message }}</div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { storyboardV2Api } from '@/api/storyboardV2'

const props = defineProps({
  projectId: Number, storyboardId: Number, versionId: Number,
  versions: { type: Array, default: () => [] }, isLocked: Boolean
})
defineEmits(['switch-version'])

const issues = ref([])
const statusMap = { draft: '草稿', reviewing: '审核中', locked: '已锁定', superseded: '已替代' }

onMounted(async () => {
  try {
    const res = await storyboardV2Api.listReviewIssues(props.projectId, props.storyboardId, props.versionId)
    issues.value = res.data || []
  } catch (_) { /* ignore */ }
})
</script>

<style scoped>
.panel { padding: 16px; height: 100%; overflow-y: auto; }
.panel-header { margin-bottom: 16px; }
.panel-header h3 { margin: 0; font-size: 16px; }
.section { margin-bottom: 16px; }
.issue-item { padding: 8px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.issue-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.issue-type { font-weight: 500; font-size: 13px; }
.issue-message { font-size: 13px; color: var(--el-text-color-regular); }
</style>
