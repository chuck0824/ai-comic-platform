<template>
  <el-dialog :model-value="visible" title="生成预览与确认" width="520px" @close="$emit('close')">
    <div v-if="preview" class="preview-content">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="推荐模型">{{ preview.modelId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="适配器版本">{{ preview.adapterVersion }}</el-descriptions-item>
        <el-descriptions-item label="预估积分">
          <span :class="{ 'text-warning': preview.estimatedCredits > 100 }">{{ preview.estimatedCredits }} 积分</span>
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="preview.images?.length" class="preview-section">
        <h4>图片参考 ({{ preview.images.length }})</h4>
        <el-tag v-for="ref in preview.images" :key="ref.slotIndex" size="small" style="margin:2px">
          {{ ref.role }} #{{ ref.slotIndex }}
        </el-tag>
      </div>

      <div v-if="preview.videos?.length" class="preview-section">
        <h4>视频参考 ({{ preview.videos.length }})</h4>
        <el-tag v-for="ref in preview.videos" :key="ref.slotIndex" size="small" type="success" style="margin:2px">
          {{ ref.role }}
        </el-tag>
      </div>

      <el-alert v-if="preview.warnings?.length" title="注意事项" type="warning" :closable="false" style="margin-top:12px">
        <ul><li v-for="w in preview.warnings" :key="w">{{ w }}</li></ul>
      </el-alert>

      <el-alert v-if="!canConfirmNow" title="预览已过期，请重新预览" type="error" :closable="false" style="margin-top:12px" />
    </div>

    <template #footer>
      <el-button @click="$emit('close')">取消</el-button>
      <el-button type="primary" :disabled="!canConfirmNow" @click="$emit('confirm', preview)">
        确认生成 ({{ preview?.estimatedCredits || 0 }} 积分)
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { canConfirm } from './modelRequestState.js'

const props = defineProps({
  visible: Boolean,
  preview: Object,
  currentFingerprint: String
})

defineEmits(['close', 'confirm'])

const canConfirmNow = computed(() =>
  canConfirm({
    previewFingerprint: props.preview?.previewFingerprint,
    currentFingerprint: props.currentFingerprint,
    estimatedCredits: props.preview?.estimatedCredits
  })
)
</script>

<style scoped>
.preview-section { margin-top: 12px; }
.preview-section h4 { font-size: 13px; margin: 0 0 6px; color: #666; }
.text-warning { color: #e6a23c; font-weight: 600; }
</style>
