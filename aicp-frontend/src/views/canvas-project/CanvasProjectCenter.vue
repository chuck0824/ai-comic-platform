<template>
  <div class="canvas-center">
    <div class="center-header">
      <h2>画布项目中心</h2>
      <el-button type="primary" @click="showCreateDialog = true">新建画布</el-button>
    </div>

    <div class="center-toolbar">
      <el-input v-model="keyword" placeholder="搜索内容项目、生产单元或画布名称" clearable style="width: 320px" @input="onSearchInput" @clear="search" />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px" @change="search">
        <el-option label="编辑中" value="editing" />
        <el-option label="生成中" value="generating" />
        <el-option label="已完成" value="completed" />
        <el-option label="已归档" value="archived" />
      </el-select>
      <el-select v-model="modeFilter" placeholder="全部模式" clearable style="width: 120px" @change="search">
        <el-option label="短剧" value="short_drama" />
        <el-option label="长篇" value="long_form" />
        <el-option label="TVC" value="tvc" />
      </el-select>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="center-grid">
      <div v-for="i in 8" :key="i" class="canvas-card-skeleton"><el-skeleton :rows="2" animated /></div>
    </div>

    <!-- Empty -->
    <div v-else-if="items.length === 0 && !error" class="empty-state">
      <el-empty :description="keyword ? `未找到匹配 '${keyword}' 的画布` : '暂无画布项目'">
        <el-button v-if="keyword" @click="keyword = ''; search()">清除搜索条件</el-button>
        <el-button v-else type="primary" @click="showCreateDialog = true">新建画布</el-button>
      </el-empty>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="error-state">
      <el-result icon="error" title="加载失败" :sub-title="error">
        <template #extra><el-button type="primary" @click="search">重试</el-button></template>
      </el-result>
    </div>

    <!-- Grid -->
    <div v-else class="center-grid">
      <CanvasProjectCard
        v-for="canvas in items"
        :key="canvas.uuid"
        :canvas="canvas"
        @edit="goToEditor"
        @command="onCanvasCommand"
      />
    </div>

    <!-- Pagination -->
    <div v-if="total > pageSize" class="center-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="search"
      />
    </div>

    <CreateCanvasDialog v-model:visible="showCreateDialog" @created="onCreated" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { canvasApi } from '@/api/canvas.js'
import { buildQueryParams } from './canvasProjectViewModel.js'
import CanvasProjectCard from './CanvasProjectCard.vue'
import CreateCanvasDialog from './CreateCanvasDialog.vue'

const router = useRouter()

const loading = ref(true)
const error = ref(null)
const items = ref([])
const keyword = ref('')
const statusFilter = ref('')
const modeFilter = ref('')
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)
const showCreateDialog = ref(false)

let searchTimer = null
onUnmounted(() => clearTimeout(searchTimer))

function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(search, 300)
}

async function search() {
  loading.value = true
  error.value = null
  try {
    const params = buildQueryParams({
      page: currentPage.value, pageSize, keyword: keyword.value,
      status: statusFilter.value, mode: modeFilter.value
    })
    const res = await canvasApi.listProjects(params)
    const data = res?.data
    items.value = data?.items || []
    total.value = data?.pagination?.total || 0
  } catch (e) {
    error.value = e?.response?.data?.message || '加载画布列表失败'
  } finally {
    loading.value = false
  }
}

function goToEditor(canvas) {
  router.push(`/canvas/${canvas.uuid}`)
}

async function onCanvasCommand({ action, canvas }) {
  const label = canvas.name || canvas.uuid
  try {
    switch (action) {
      case 'copy':
        await ElMessageBox.confirm(`确认复制画布「${label}」？`, '复制画布', { type: 'info' })
        await canvasApi.copyProject(canvas.uuid, { name: `${canvas.name} 副本` })
        ElMessage.success('画布已复制')
        break
      case 'archive':
        await ElMessageBox.confirm(`确认归档画布「${label}」？归档后可恢复。`, '归档画布', { type: 'warning' })
        await canvasApi.archiveProject(canvas.uuid)
        ElMessage.success('画布已归档')
        break
      case 'restore':
        await canvasApi.restoreProject(canvas.uuid)
        ElMessage.success('画布已恢复')
        break
      case 'delete':
        await ElMessageBox.confirm(`确认删除画布「${label}」？此操作不可撤销。`, '删除画布', { type: 'error', confirmButtonClass: 'el-button--danger' })
        await canvasApi.deleteProject(canvas.uuid)
        ElMessage.success('画布已删除')
        break
      default:
        ElMessage.warning(`操作 ${action} 暂未实现`)
        return
    }
    search()
  } catch (e) {
    if (e !== 'cancel' && e?.response?.data?.message) {
      ElMessage.error(e.response.data.message)
    }
  }
}

function onCreated(canvas) {
  showCreateDialog.value = false
  ElMessage.success('画布创建成功')
  router.push(`/canvas/${canvas.uuid}`)
}

onMounted(search)
</script>

<style scoped>
.canvas-center { max-width: 1200px; margin: 0 auto; padding: 24px; }
.center-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.center-header h2 { margin: 0; font-size: 20px; }
.center-toolbar { display: flex; gap: 12px; margin-bottom: 24px; }
.center-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.center-pagination { display: flex; justify-content: center; margin-top: 24px; }
.empty-state, .error-state { padding: 60px 0; text-align: center; }
.canvas-card-skeleton { border: 1px solid #e4e7ed; border-radius: 8px; padding: 16px; }
</style>
