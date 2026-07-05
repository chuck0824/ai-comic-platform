<template>
  <div class="sop-check-table">
    <div class="table-toolbar">
      <el-select v-model="filterResult" placeholder="按结果筛选" clearable size="small" style="width: 140px">
        <el-option v-for="(label, key) in RESULT_LABELS" :key="key" :label="label" :value="key" />
      </el-select>
      <el-select v-model="filterSeverity" placeholder="按严重等级筛选" clearable size="small" style="width: 140px; margin-left: 8px">
        <el-option v-for="(label, key) in SEVERITY_LABELS" :key="key" :label="label" :value="key" />
      </el-select>
    </div>

    <el-table :data="filteredResults" stripe size="small" v-loading="loading">
      <el-table-column prop="ruleCode" label="规则" width="180" />
      <el-table-column prop="result" label="结果" width="100">
        <template #default="{ row }">
          <el-tag :type="resultColor(row.result)" size="small">{{ resultLabel(row.result) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="severity" label="等级" width="80">
        <template #default="{ row }">
          <el-tag :type="severityColor(row.severity)" size="small" effect="dark">{{ row.severity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标" width="140">
        <template #default="{ row }">
          {{ row.targetType }}/{{ row.targetId }}
        </template>
      </el-table-column>
      <el-table-column prop="suggestion" label="建议" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="row.result !== 'PASS'" size="small" type="primary" @click="$emit('create-work-order', row)">
            创建工单
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && filteredResults.length === 0" description="暂无检查结果" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { RESULT_LABELS, SEVERITY_LABELS, resultLabel, resultColor, severityColor } from './sopState.js'

const props = defineProps({
  results: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

defineEmits(['create-work-order'])

const filterResult = ref('')
const filterSeverity = ref('')

const filteredResults = computed(() => {
  let list = props.results
  if (filterResult.value) list = list.filter((r) => r.result === filterResult.value)
  if (filterSeverity.value) list = list.filter((r) => r.severity === filterSeverity.value)
  return list
})
</script>

<style scoped>
.table-toolbar {
  margin-bottom: 12px;
}
</style>
