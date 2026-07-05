<script setup>
import { filterDefinitions, roleTypeLabel, lifecycleStatusLabel } from '@/utils/agentConfigHelpers'

const props = defineProps({
  blueprints: { type: Array, default: () => [] },
  definitions: { type: Array, default: () => [] },
  filter: { type: Object, default: () => ({ roleType: '', status: 'ACTIVE' }) },
  selected: { type: Object, default: null }
})

const emit = defineEmits(['select', 'copy', 'archive'])

const filteredDefs = computed(() =>
  filterDefinitions(props.definitions, props.filter)
)

const getBlueprintName = (bpId) => {
  const bp = props.blueprints.find(b => b.id === bpId)
  return bp ? bp.name : bpId
}
</script>

<template>
  <div class="def-list">
    <!-- Filters -->
    <div class="dl-filters">
      <el-select v-model="filter.roleType" placeholder="角色筛选" clearable size="small" style="width:100%">
        <el-option v-for="bp in blueprints" :key="bp.id" :label="roleTypeLabel(bp.roleType)" :value="bp.roleType" />
      </el-select>
    </div>

    <!-- List -->
    <div v-if="filteredDefs.length === 0" class="dl-empty">
      <el-empty description="暂无 Agent，点击右上角新增" :image-size="60" />
    </div>
    <div v-else class="dl-items">
      <div
        v-for="def in filteredDefs"
        :key="def.id"
        class="dl-item"
        :class="{ active: selected?.id === def.id }"
        @click="emit('select', def)"
      >
        <div class="dli-top">
          <el-tag size="small" type="primary" effect="plain">{{ roleTypeLabel(def.roleType) }}</el-tag>
          <el-tag size="small" :type="def.lifecycleStatus === 'ACTIVE' ? 'success' : 'info'">
            {{ lifecycleStatusLabel(def.lifecycleStatus) }}
          </el-tag>
        </div>
        <div class="dli-name">{{ def.name }}</div>
        <div class="dli-meta">
          <span>{{ getBlueprintName(def.blueprintId) }}</span>
          <span v-if="def.currentVersionNo">v{{ def.currentVersionNo }}</span>
        </div>
        <div class="dli-actions" @click.stop>
          <el-button size="small" text @click="emit('copy', def.id)">复制</el-button>
          <el-button size="small" text type="danger" @click="emit('archive', def.id)">归档</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.def-list { display: flex; flex-direction: column; gap: 12px; }
.dl-filters { padding-bottom: 8px; }
.dl-items { display: flex; flex-direction: column; gap: 8px; }
.dl-item {
  padding: 12px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: border-color .2s, box-shadow .2s;
}
.dl-item:hover { border-color: var(--accent); }
.dl-item.active { border-color: var(--accent); box-shadow: var(--shadow-sm); }
.dli-top { display: flex; gap: 6px; margin-bottom: 6px; }
.dli-name { font-weight: 600; font-size: 14px; color: var(--text-primary); margin-bottom: 4px; }
.dli-meta { font-size: 12px; color: var(--text-tertiary); display: flex; gap: 8px; margin-bottom: 6px; }
.dli-actions { display: flex; gap: 4px; }
.dl-empty { padding: 40px 0; }
</style>
