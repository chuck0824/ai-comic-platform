<template>
  <div class="panel" data-testid="emotion-panel">
    <div class="panel-header"><h3>情绪节奏</h3></div>
    <el-empty v-if="!segments.length" description="暂无情绪节奏数据" />
    <el-table v-else :data="segments" size="small">
      <el-table-column prop="emotionType" label="情绪类型" />
      <el-table-column prop="shotRange" label="镜头范围" />
      <el-table-column prop="intensity" label="强度" />
      <el-table-column prop="coreExpression" label="核心表达" />
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { storyboardV2Api } from '@/api/storyboardV2'

const props = defineProps({ projectId: Number, storyboardId: Number, versionId: Number, isLocked: Boolean })
const segments = ref([])

onMounted(async () => {
  try {
    const res = await storyboardV2Api.listEmotionSegments(props.projectId, props.storyboardId, props.versionId)
    segments.value = res.data || []
  } catch (_) { /* ignore */ }
})
</script>

<style scoped>
.panel { padding: 16px; height: 100%; overflow-y: auto; }
.panel-header { margin-bottom: 16px; }
.panel-header h3 { margin: 0; font-size: 16px; }
</style>
