<template>
  <div class="panel" data-testid="rules-panel">
    <div class="panel-header"><h3>创意修订规则</h3></div>
    <el-empty v-if="!rules.length" description="暂无创意规则" />
    <el-table v-else :data="rules" size="small">
      <el-table-column prop="ruleType" label="类型" width="100" />
      <el-table-column prop="dimensionName" label="维度" />
      <el-table-column prop="principle" label="原则" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="80" />
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { storyboardV2Api } from '@/api/storyboardV2'

const props = defineProps({ projectId: Number, storyboardId: Number, versionId: Number, isLocked: Boolean })
const rules = ref([])

onMounted(async () => {
  try {
    const res = await storyboardV2Api.listCreativeRules(props.projectId, props.storyboardId, props.versionId)
    rules.value = res.data || []
  } catch (_) { /* ignore */ }
})
</script>

<style scoped>
.panel { padding: 16px; height: 100%; overflow-y: auto; }
.panel-header { margin-bottom: 16px; }
.panel-header h3 { margin: 0; font-size: 16px; }
</style>
