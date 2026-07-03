<template>
  <el-dialog v-model="visible" title="发送到画布" width="420px" @close="$emit('close')">
    <el-form label-width="80px" size="small">
      <el-form-item label="目标项目"><el-input v-model="projectUuid" placeholder="画布项目UUID" /></el-form-item>
      <el-form-item label="画布"><el-input v-model="canvasUuid" placeholder="画布UUID（可选）" /></el-form-item>
      <el-form-item label="放置方式">
        <el-radio-group v-model="placement">
          <el-radio value="viewport_center">视口居中</el-radio>
          <el-radio value="auto">自动</el-radio>
          <el-radio value="absolute">指定坐标</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="placement === 'absolute'" label="坐标">
        <el-input-number v-model="x" :min="0" size="small" style="width:90px" /> ×
        <el-input-number v-model="y" :min="0" size="small" style="width:90px" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" type="primary" :loading="sending" @click="doPlace">发送</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
const props = defineProps({ modelValue: Boolean, assetUuid: String })
const emit = defineEmits(['update:modelValue', 'placed'])
const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => emit('update:modelValue', v))

const projectUuid = ref('')
const canvasUuid = ref('')
const placement = ref('viewport_center')
const x = ref(200)
const y = ref(200)
const sending = ref(false)

import { assetHistoryApi } from '@/api/assetHistory'
import { ElMessage } from 'element-plus'

async function doPlace() {
  if (!projectUuid.value) { ElMessage.warning('请输入目标项目UUID'); return }
  sending.value = true
  try {
    await assetHistoryApi.placeOnCanvas(props.assetUuid, {
      targetProjectUuid: projectUuid.value,
      targetCanvasUuid: canvasUuid.value,
      placement: placement.value,
      x: x.value,
      y: y.value
    })
    ElMessage.success('已发送到画布')
    visible.value = false
    emit('placed')
  } catch (e) { ElMessage.error(e.message || '发送失败') }
  finally { sending.value = false }
}
</script>
