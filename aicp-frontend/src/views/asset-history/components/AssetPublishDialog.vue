<template>
  <el-dialog v-model="visible" title="发布到资产市场" width="440px" @close="$emit('close')">
    <el-form label-width="80px" size="small">
      <el-form-item label="标题"><el-input v-model="title" placeholder="发布标题" maxlength="200" /></el-form-item>
      <el-form-item label="简介"><el-input v-model="description" type="textarea" :rows="3" placeholder="简要介绍资产" maxlength="1000" /></el-form-item>
      <el-form-item label="标签"><el-input v-model="tags" placeholder="逗号分隔" /></el-form-item>
      <el-form-item label="许可">
        <el-select v-model="licenseType" style="width:100%">
          <el-option label="免费" value="FREE" />
          <el-option label="付费" value="PAID" />
          <el-option label="订阅" value="SUBSCRIPTION" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" type="primary" :loading="publishing" @click="doPublish">发布</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
const props = defineProps({ modelValue: Boolean, assetUuid: String })
const emit = defineEmits(['update:modelValue', 'published'])
const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => emit('update:modelValue', v))

const title = ref('')
const description = ref('')
const tags = ref('')
const licenseType = ref('FREE')
const publishing = ref(false)

import { assetHistoryApi } from '@/api/assetHistory'
import { ElMessage } from 'element-plus'

async function doPublish() {
  if (!title.value) { ElMessage.warning('请输入标题'); return }
  publishing.value = true
  try {
    await assetHistoryApi.publish(props.assetUuid, {
      title: title.value,
      description: description.value,
      tags: tags.value,
      licenseType: licenseType.value
    })
    ElMessage.success('发布申请已提交')
    visible.value = false
    emit('published')
  } catch (e) { ElMessage.error(e.message || '发布失败') }
  finally { publishing.value = false }
}
</script>
