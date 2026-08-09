<template>
  <el-dialog :model-value="modelValue" title="为场景选择资产" width="680px" :close-on-click-modal="false" @close="emit('update:modelValue', false)">
    <el-tabs v-model="mode">
      <el-tab-pane label="引用已有资产" name="existing">
        <el-alert v-if="degraded" title="资产服务只读，暂不能创建新的引用" type="warning" :closable="false" show-icon />
        <el-select v-model="assetId" placeholder="选择可用母资产" filterable class="full-width">
          <el-option v-for="asset in bindableAssets" :key="asset.id" :label="`${asset.name} · v${asset.currentVersionNo || asset.version || 1}`" :value="asset.id" />
        </el-select>
        <el-select v-model="variantId" placeholder="选择场景变体" class="full-width">
          <el-option v-for="variant in selectedAsset?.variants || []" :key="variant.id" :label="`${variant.name || variant.id} · v${variant.version}`" :value="variant.id" />
        </el-select>
      </el-tab-pane>
      <el-tab-pane label="创建新资产" name="create"><el-form label-position="top"><el-form-item label="资产名称"><el-input v-model="draft.name" /></el-form-item><el-form-item label="空间类型"><el-input v-model="draft.spaceType" /></el-form-item></el-form></el-tab-pane>
      <el-tab-pane label="暂不绑定" name="defer"><el-alert title="可以继续编辑草稿，但进入文字分镜前会持续提示绑定场景资产。" type="warning" :closable="false" show-icon /></el-tab-pane>
    </el-tabs>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" @click="confirm">确认选择</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'

const props = defineProps({ modelValue: Boolean, assets: { type: Array, default: () => [] }, degraded: Boolean })
const emit = defineEmits(['update:modelValue', 'bind-existing', 'create-new', 'defer', 'guidance'])
const mode = ref('existing'); const assetId = ref(null); const variantId = ref(null); const draft = reactive({ name: '', spaceType: '' })
const bindableAssets = computed(() => props.assets.filter(asset => String(asset.status || '').toUpperCase() !== 'ARCHIVED'))
const selectedAsset = computed(() => bindableAssets.value.find(asset => asset.id === assetId.value) || null)
watch(assetId, () => { variantId.value = null })
function reject(code, message, targetAction) { const result = { allowed: false, code, title: '无法完成场景绑定', message, targetAction }; emit('guidance', result); return result }
function confirm() {
  if (mode.value === 'defer') { emit('defer'); emit('update:modelValue', false); return }
  if (props.degraded) return reject('DEGRADED_READ_ONLY', '恢复资产服务后才能创建新绑定。', 'retry_scene_assets')
  if (mode.value === 'create') {
    if (!draft.name.trim() || !draft.spaceType.trim()) return reject('SCENE_ASSET_FIELDS_REQUIRED', '填写资产名称和空间类型。', 'focus_scene_asset_draft')
    emit('create-new', { ...draft }); return
  }
  const variant = selectedAsset.value?.variants?.find(item => item.id === variantId.value)
  if (!selectedAsset.value || !variant) return reject('SCENE_ASSET_VERSION_REQUIRED', '请选择母资产及其变体版本。', 'choose_scene_asset_version')
  emit('bind-existing', { asset: selectedAsset.value, variant })
}
</script>

<style scoped>.full-width{width:100%;margin-top:12px}</style>
