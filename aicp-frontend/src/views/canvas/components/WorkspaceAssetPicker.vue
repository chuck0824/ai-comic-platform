<template>
  <el-dialog v-model="visible" title="Workspace 资产库" width="720px" @close="$emit('close')">
    <div class="flex gap-sm mb-lg">
      <el-select v-model="typeFilter" placeholder="全部分类" clearable style="width:140px" @change="load">
        <el-option v-for="cat in supportedTypes" :key="cat.key" :label="cat.label" :value="cat.key" />
      </el-select>
      <el-input v-model="searchText" placeholder="搜索资产名称..." clearable style="width:220px" @keyup.enter="load" />
      <el-button type="primary" @click="load">搜索</el-button>
    </div>

    <el-skeleton v-if="loading" :rows="3" animated />
    <el-empty v-else-if="error" :description="error">
      <el-button @click="load">重试</el-button>
    </el-empty>
    <div v-else>
      <el-empty v-if="!assets.length" description="暂无可用资产，从公共市场领取或创建新资产" />
      <div v-for="item in assets" :key="item.id" class="card mb-md flex justify-between items-center" style="padding:14px">
        <div>
          <div class="font-semibold">{{ item.name }}</div>
          <p class="text-sm text-muted">{{ item.assetType }} · {{ sourceTypeLabel(item.sourceType) }}</p>
        </div>
        <div class="flex gap-sm">
          <el-button size="small" type="primary" :loading="applying === item.id" @click="onApply(item)">
            {{ applying === item.id ? '应用中...' : '应用到画布' }}
          </el-button>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { listLibrary, applyAsset, undoApplication } from '@/api/asset'
import { assetTypeLabel, sourceTypeLabel } from '@/views/asset-market/assetMarketState'

const props = defineProps({
  modelValue: Boolean,
  projectId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue', 'applied'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const assets = ref([])
const loading = ref(false)
const error = ref(null)
const applying = ref(null)
const typeFilter = ref('')
const searchText = ref('')

const supportedTypes = ['STYLE_PACK', 'CHARACTER', 'SCENE', 'PROMPT'].map(k => ({
  key: k, label: assetTypeLabel(k)
}))

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await listLibrary({
      assetType: typeFilter.value || undefined,
      page: 1, page_size: 50
    })
    let items = res.data?.data?.items || []
    if (searchText.value) {
      const q = searchText.value.toLowerCase()
      items = items.filter(a => a.name.toLowerCase().includes(q))
    }
    // Only show supported types for canvas application
    items = items.filter(a => ['STYLE_PACK', 'CHECKPOINT', 'LORA', 'CHARACTER', 'SCENE', 'PROMPT'].includes(a.assetType))
    assets.value = items
  } catch (e) {
    error.value = e.response?.data?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function onApply(item) {
  if (!props.projectId) {
    ElMessage.warning('请先在画布中创建或选择项目')
    return
  }
  applying.value = item.id
  try {
    const body = {
      project_id: Number(props.projectId),
      target_type: 'PROJECT',
      idempotency_key: crypto.randomUUID?.() ?? `${Date.now()}-${item.id}`
    }
    const res = await applyAsset(item.id, body)
    ElMessage.success(res.data?.data?.changeSummary || `已将「${item.name}」应用到画布`)
    emit('applied', { asset: item, result: res.data?.data })
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '应用失败')
  } finally {
    applying.value = null
  }
}

// Auto-load on open
import { watch } from 'vue'
watch(visible, (v) => { if (v) load() })
</script>
