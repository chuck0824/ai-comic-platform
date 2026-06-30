<template>
  <div>
    <div class="content-header">
      <div>
        <h2 class="text-xl font-bold">{{ type === 'synopsis' ? '简介' : '总纲' }}</h2>
        <p class="text-sm text-muted mt-sm">{{ type === 'synopsis' ? '作品的简要概述。' : '作品的分章大纲。' }}</p>
      </div>
      <span class="save-state" :class="statusClass">
        <el-icon v-if="saving" class="is-loading"><Loading /></el-icon>
        <el-icon v-else-if="saveStatus === 'error'"><WarningFilled /></el-icon>
        <el-icon v-else-if="saveStatus === 'conflict'"><WarningFilled /></el-icon>
        <el-icon v-else><CircleCheck /></el-icon>
        {{ statusText }}
      </span>
    </div>
    <el-input
      :model-value="modelValue"
      type="textarea"
      :rows="type === 'synopsis' ? 8 : 14"
      :placeholder="type === 'synopsis' ? '输入作品简介…' : '输入总纲内容…'"
      :disabled="readOnly"
      @input="onInput"
    />
    <div class="content-actions align-end">
      <el-button size="small" type="primary" :loading="saving" :disabled="!isDirty || readOnly" @click="$emit('save')">
        保存{{ type === 'synopsis' ? '简介' : '总纲' }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Loading, WarningFilled, CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({
  type: { type: String, default: 'synopsis' },
  modelValue: String,
  saving: Boolean,
  saveStatus: { type: String, default: 'idle' },
  isDirty: Boolean,
  readOnly: Boolean
})

const emit = defineEmits(['update:modelValue', 'save'])

const statusClass = computed(() => {
  if (props.saving) return 'saving'
  if (props.saveStatus === 'error' || props.saveStatus === 'conflict') return 'error'
  return ''
})

const statusText = computed(() => {
  if (props.saving) return '保存中…'
  if (props.saveStatus === 'saved') return '已保存'
  if (props.saveStatus === 'error') return '保存失败'
  if (props.saveStatus === 'conflict') return '冲突'
  return '就绪'
})

let debounceTimer = null
function onInput(val) {
  emit('update:modelValue', val)
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => emit('save'), 2000)
}
</script>

<style scoped>
.content-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
.save-state { display: inline-flex; align-items: center; gap: 5px; border-radius: 999px; padding: 5px 10px; font-size: 12px; background: var(--success-bg); color: var(--success); }
.save-state.saving { background: var(--warning-bg); color: var(--warning); }
.save-state.error { background: var(--danger-bg); color: var(--danger); }
.content-actions { display: flex; align-items: center; gap: 10px; }
.align-end { justify-content: flex-end; margin-top: 20px; }
</style>
