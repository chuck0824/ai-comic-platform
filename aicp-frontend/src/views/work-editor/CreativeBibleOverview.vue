<template>
  <div class="bible-overview">
    <!-- Loading -->
    <div v-if="loading" class="skeleton-group">
      <div class="skeleton" style="height:80px" />
      <div class="skeleton" style="height:120px;margin-top:12px" />
    </div>

    <!-- Error -->
    <el-alert v-else-if="error" type="error" :title="error" show-icon :closable="false">
      <template #default>
        <el-button size="small" @click="$emit('refresh')">重试</el-button>
      </template>
    </el-alert>

    <!-- Empty -->
    <div v-else-if="!bible || bible.status === 'missing'" class="empty-state">
      <el-icon :size="48" color="var(--text-muted)"><Collection /></el-icon>
      <p class="text-lg font-bold mt-md">尚未创建创作圣经</p>
      <p class="text-muted mt-sm">
        创作圣经是项目的正式事实源，确认后 AI 生成将使用其中的设定。
      </p>
      <el-button type="primary" class="mt-lg" @click="handleCreateDraft">
        创建首个版本
      </el-button>
    </div>

    <!-- Content -->
    <div v-else>
      <!-- Status card -->
      <div class="card mb-md">
        <div class="flex items-center justify-between">
          <div>
            <span class="text-lg font-bold">版本 {{ bible.current_version_no }}</span>
            <el-tag :type="statusTagType" size="small" class="ml-sm">
              {{ statusLabel }}
            </el-tag>
          </div>
          <div class="flex gap-sm">
            <el-button v-if="bible.status === 'draft'" type="primary" size="small"
              @click="handleConfirm" :loading="confirming">
              确认版本
            </el-button>
            <el-button size="small" @click="handleCreateDraft" :disabled="bible.status === 'draft'">
              新建草稿
            </el-button>
          </div>
        </div>
      </div>

      <!-- Stats -->
      <div class="grid3 gap-md mb-md">
        <div class="card text-center">
          <div class="text-2xl font-bold">{{ health.confirmed_fact_count }}</div>
          <div class="text-sm text-muted">已确认事实</div>
        </div>
        <div class="card text-center">
          <div class="text-2xl font-bold">{{ health.pending_change_count }}</div>
          <div class="text-sm text-muted">待处理变更</div>
        </div>
        <div class="card text-center">
          <div class="text-2xl font-bold">
            <el-icon v-if="health.ready_for_generation" color="var(--success)"><CircleCheck /></el-icon>
            <el-icon v-else color="var(--warning)"><WarningFilled /></el-icon>
          </div>
          <div class="text-sm text-muted">生成就绪</div>
        </div>
      </div>

      <!-- Confirm dialog -->
      <el-dialog v-model="showConfirmDialog" title="确认创作圣经版本" width="480px">
        <p>确认后，此版本的事实将作为 AI 生成的正式上下文。</p>
        <p class="text-muted text-sm mt-sm">
          确认操作不可撤销。已确认的生态规则和写作口径将锁定。
        </p>
        <template #footer>
          <el-button @click="showConfirmDialog = false">取消</el-button>
          <el-button type="primary" @click="handleConfirm" :loading="confirming">确认</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Collection, CircleCheck, WarningFilled } from '@element-plus/icons-vue'
import { contentProjectApi } from '@/api/contentProject'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  bible: { type: Object, default: null },
  health: { type: Object, default: () => ({ ready_for_generation: false }) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: null }
})

const emit = defineEmits(['refresh', 'created', 'confirmed'])

const confirming = ref(false)
const showConfirmDialog = ref(false)

const statusTagType = computed(() => {
  const map = { draft: 'warning', confirmed: 'success', superseded: 'info', archived: 'info' }
  return map[props.bible?.status] || 'info'
})

const statusLabel = computed(() => {
  const map = { draft: '草稿', reviewable: '待确认', confirmed: '已确认', superseded: '已替代', archived: '已归档' }
  return map[props.bible?.status] || props.bible?.status
})

async function handleCreateDraft() {
  try {
    await contentProjectApi.createBibleDraft(props.projectId, { summary: '新的创作圣经草稿' })
    emit('created')
    emit('refresh')
  } catch (e) {
    // error handled by interceptor
  }
}

async function handleConfirm() {
  if (!props.bible?.id) return
  confirming.value = true
  try {
    await contentProjectApi.confirmBible(props.projectId, props.bible.id)
    showConfirmDialog.value = false
    emit('confirmed')
    emit('refresh')
  } finally {
    confirming.value = false
  }
}
</script>

<style scoped>
.bible-overview {
  padding: 16px;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 16px;
  text-align: center;
}
</style>
