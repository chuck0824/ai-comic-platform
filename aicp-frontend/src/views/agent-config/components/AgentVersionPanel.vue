<script setup>
import { ref } from 'vue'
import { versionStatusLabel } from '@/utils/agentConfigHelpers'

const props = defineProps({
  versions: { type: Array, default: () => [] },
  draft: { type: Object, default: null }
})

const emit = defineEmits(['publish', 'refresh'])

const changeSummary = ref('')
const publishing = ref(false)

const handlePublish = async () => {
  publishing.value = true
  try {
    await emit('publish', {
      rowVersion: props.draft.rowVersion,
      changeSummary: changeSummary.value || '发布新版本'
    })
    changeSummary.value = ''
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <div class="version-panel">
    <!-- Draft section -->
    <div v-if="draft" class="vp-draft">
      <h4>当前草稿 (v{{ draft.versionNo }})</h4>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="状态">
          <el-tag size="small" type="warning">{{ versionStatusLabel(draft.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="版本号">{{ draft.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ draft.createdAt }}</el-descriptions-item>
      </el-descriptions>
      <div class="vp-publish">
        <el-input v-model="changeSummary" placeholder="版本说明（可选）" size="small" maxlength="500" style="margin-bottom:8px" />
        <el-button type="primary" size="small" :loading="publishing" @click="handlePublish">
          发布此版本
        </el-button>
      </div>
    </div>

    <!-- Version history -->
    <div class="vp-history">
      <h4>版本历史</h4>
      <el-table :data="versions" size="small" style="width:100%">
        <el-table-column prop="versionNo" label="版本" width="60" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : row.status === 'DRAFT' ? 'warning' : 'info'">
              {{ versionStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="changeSummary" label="说明" min-width="150" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.version-panel { display: flex; flex-direction: column; gap: 20px; }
.vp-draft { }
.vp-draft h4, .vp-history h4 { font-size: 14px; font-weight: 600; color: var(--text-secondary); margin: 0 0 8px; }
.vp-publish { margin-top: 12px; }
</style>
