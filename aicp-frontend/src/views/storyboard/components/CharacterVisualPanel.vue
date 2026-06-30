<template>
  <div class="panel" data-testid="visuals-panel">
    <div class="panel-header"><h3>人物视觉规范</h3></div>
    <el-empty v-if="!visuals.length" description="暂无人物视觉规范" />
    <el-table v-else :data="visuals" size="small">
      <el-table-column prop="characterName" label="角色" />
      <el-table-column prop="coreIdentity" label="核心识别" show-overflow-tooltip />
      <el-table-column prop="dailyLook" label="日常造型" show-overflow-tooltip />
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { storyboardV2Api } from '@/api/storyboardV2'

const props = defineProps({ projectId: Number, storyboardId: Number, versionId: Number, isLocked: Boolean })
const visuals = ref([])

onMounted(async () => {
  try {
    const res = await storyboardV2Api.listCharacterVisuals(props.projectId, props.storyboardId, props.versionId)
    visuals.value = res.data || []
  } catch (_) { /* ignore */ }
})
</script>

<style scoped>
.panel { padding: 16px; height: 100%; overflow-y: auto; }
.panel-header { margin-bottom: 16px; }
.panel-header h3 { margin: 0; font-size: 16px; }
</style>
