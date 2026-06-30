<template>
  <div class="panel" data-testid="prompt-panel">
    <div class="panel-header"><h3>提示词模板</h3></div>
    <el-empty v-if="!templates.length" description="暂无提示词模板" />
    <el-table v-else :data="templates" size="small">
      <el-table-column prop="templateCode" label="模板编号" />
      <el-table-column prop="emotionName" label="情绪" />
      <el-table-column prop="imagePrompt" label="图片提示词" show-overflow-tooltip />
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { storyboardV2Api } from '@/api/storyboardV2'

const props = defineProps({ projectId: Number, storyboardId: Number, versionId: Number, isLocked: Boolean })
const templates = ref([])

onMounted(async () => {
  try {
    const res = await storyboardV2Api.listPromptTemplates(props.projectId, props.storyboardId, props.versionId)
    templates.value = res.data || []
  } catch (_) { /* ignore */ }
})
</script>

<style scoped>
.panel { padding: 16px; height: 100%; overflow-y: auto; }
.panel-header { margin-bottom: 16px; }
.panel-header h3 { margin: 0; font-size: 16px; }
</style>
