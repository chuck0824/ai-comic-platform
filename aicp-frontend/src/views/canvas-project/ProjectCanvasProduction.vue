<template>
  <div class="project-canvas-production">
    <div class="pcp-left">
      <h3>生产单元</h3>
      <div v-for="unit in units" :key="unit.id" class="unit-item" :class="{ active: selectedUnitId === unit.id }" @click="selectUnit(unit)">
        <span>{{ unit.title || `单元 ${unit.id}` }}</span>
        <el-badge :value="unit.canvasCount || 0" type="primary" />
      </div>
      <div v-if="units.length === 0" class="unit-empty">暂无生产单元</div>
    </div>
    <div class="pcp-right">
      <div v-if="selectedUnitId" class="unit-canvases">
        <div class="unit-canvases-header">
          <h4>{{ selectedUnit?.title || '画布方案' }}</h4>
          <el-button size="small" type="primary" @click="showCreateDialog = true">创建画布</el-button>
        </div>
        <div v-if="canvases.length === 0" class="empty-state">
          <el-empty description="该生产单元暂无画布" :image-size="80">
            <el-button size="small" type="primary" @click="showCreateDialog = true">创建画布</el-button>
          </el-empty>
        </div>
        <div v-else class="canvas-list">
          <CanvasProjectCard v-for="c in canvases" :key="c.uuid" :canvas="c" @edit="goToEditor" />
        </div>
      </div>
      <div v-else class="no-selection">
        <el-empty description="请从左侧选择一个生产单元" :image-size="80" />
      </div>
    </div>
    <CreateCanvasDialog v-model:visible="showCreateDialog" @created="onCreated" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { canvasApi } from '@/api/canvas.js'
import { contentProjectApi } from '@/api/contentProject.js'
import CanvasProjectCard from './CanvasProjectCard.vue'
import CreateCanvasDialog from './CreateCanvasDialog.vue'

const route = useRoute()
const router = useRouter()
const projectId = ref(route.params.projectId)

const units = ref([])
const selectedUnitId = ref(null)
const selectedUnit = ref(null)
const canvases = ref([])
const showCreateDialog = ref(false)

onMounted(async () => {
  try {
    const res = await contentProjectApi.listUnits(projectId.value)
    units.value = (res?.data || []).map(u => ({ ...u, canvasCount: 0 }))
    // Load canvas counts per unit
    const cr = await canvasApi.listByContentProject(projectId.value, { page: 1, page_size: 100 })
    const allCanvases = cr?.data?.items || []
    for (const c of allCanvases) {
      const unit = units.value.find(u => u.id === c.productionUnitId)
      if (unit) unit.canvasCount = (unit.canvasCount || 0) + 1
    }
  } catch { /* degrade */ }
})

async function selectUnit(unit) {
  selectedUnitId.value = unit.id
  selectedUnit.value = unit
  try {
    const res = await canvasApi.listByContentProject(projectId.value, { page: 1, page_size: 50, production_unit_id: unit.id })
    canvases.value = res?.data?.items || []
  } catch { canvases.value = [] }
}

function goToEditor(canvas) {
  const id = canvas?.uuid || canvas?.id
  if (!id) return
  router.push({ name: 'Canvas', params: { projectId: String(id) } })
}

function onCreated() {
  showCreateDialog.value = false
  if (selectedUnitId.value) selectUnit(selectedUnit.value)
}
</script>

<style scoped>
.project-canvas-production { display: flex; gap: 24px; height: 100%; }
.pcp-left { width: 240px; border-right: 1px solid #e4e7ed; padding-right: 16px; }
.pcp-left h3 { font-size: 14px; margin: 0 0 12px; }
.unit-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; border-radius: 6px; cursor: pointer; margin-bottom: 4px; }
.unit-item:hover { background: #f5f7fa; }
.unit-item.active { background: #ecf5ff; color: #409eff; }
.unit-empty { color: #a1a1aa; font-size: 13px; padding: 16px; }
.pcp-right { flex: 1; }
.unit-canvases-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.unit-canvases-header h4 { margin: 0; }
.canvas-list { display: flex; flex-direction: column; gap: 12px; }
.empty-state, .no-selection { padding: 40px 0; text-align: center; }
</style>
