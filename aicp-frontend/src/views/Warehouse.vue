<template>
  <div class="warehouse-page">
    <div class="center-header">
      <h2>剧本仓库</h2>
      <el-button type="primary" @click="$router.push('/script-gen/new')">+ 新建剧本</el-button>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="lifecycleFilter" @tab-change="onTabChange" class="warehouse-tabs">
      <el-tab-pane label="全部" name="active" />
      <el-tab-pane label="草稿" name="draft" />
      <el-tab-pane label="审核中" name="reviewing" />
      <el-tab-pane label="已锁稿" name="locked" />
      <el-tab-pane label="生产中" name="producing" />
      <el-tab-pane label="已完成" name="completed" />
      <el-tab-pane label="已归档" name="archived" />
      <el-tab-pane label="回收站" name="trash" />
    </el-tabs>

    <!-- Filters -->
    <div class="center-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索项目名称..."
        clearable
        style="width: 280px"
        @input="onSearchInput"
        @clear="search"
      />
      <el-select v-model="contentFilter" placeholder="内容状态" clearable style="width: 130px" @change="search">
        <el-option v-for="(v,k) in CONTENT_STATUS_LABELS" :key="k" :label="v" :value="k" />
      </el-select>
      <el-select v-model="productionFilter" placeholder="生产状态" clearable style="width: 130px" @change="search">
        <el-option v-for="(v,k) in PRODUCTION_STATUS_LABELS" :key="k" :label="v" :value="k" />
      </el-select>
      <el-select v-model="commercialFilter" placeholder="商业状态" clearable style="width: 130px" @change="search">
        <el-option v-for="(v,k) in COMMERCIAL_STATUS_LABELS" :key="k" :label="v" :value="k" />
      </el-select>
      <el-select v-model="sortFilter" placeholder="排序" style="width: 120px" @change="search">
        <el-option label="最近更新" value="updated_desc" />
        <el-option label="最早更新" value="updated_asc" />
        <el-option label="最近创建" value="created_desc" />
        <el-option label="名称 A-Z" value="name_asc" />
      </el-select>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="center-grid">
      <div v-for="i in 8" :key="i" class="card-skeleton">
        <el-skeleton :rows="3" animated />
      </div>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="error-state">
      <el-result icon="error" title="加载失败" :sub-title="error">
        <template #extra><el-button type="primary" @click="search">重试</el-button></template>
      </el-result>
    </div>

    <!-- Empty -->
    <div v-else-if="items.length === 0" class="empty-state">
      <el-empty :description="emptyDesc">
        <el-button v-if="hasFilters" @click="clearFilters">清除筛选</el-button>
        <el-button v-else type="primary" @click="$router.push('/script-gen/new')">新建剧本</el-button>
      </el-empty>
    </div>

    <!-- Grid -->
    <div v-else class="center-grid">
      <ProjectCard
        v-for="project in items"
        :key="project.id"
        :project="project"
        @open-detail="openDetail"
        @command="onCommand"
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contentProjectApi } from '@/api/contentProject'
import {
  buildWarehouseQuery, primaryActionRoute,
  CONTENT_STATUS_LABELS, PRODUCTION_STATUS_LABELS, COMMERCIAL_STATUS_LABELS
} from './warehouse/projectWarehouseViewModel'
import ProjectCard from './warehouse/ProjectCard.vue'

const router = useRouter()

const loading = ref(true)
const error = ref(null)
const items = ref([])
const keyword = ref('')
const contentFilter = ref('')
const productionFilter = ref('')
const commercialFilter = ref('')
const lifecycleFilter = ref('active')
const sortFilter = ref('updated_desc')
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)

let searchTimer = null
onUnmounted(() => clearTimeout(searchTimer))

const hasFilters = computed(() => keyword.value || contentFilter.value || productionFilter.value || commercialFilter.value)
const emptyDesc = computed(() => hasFilters.value ? '没有匹配的项目' : '剧本仓库还是空的')

function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(search, 300)
}

function onTabChange(tab) {
  lifecycleFilter.value = tab
  currentPage.value = 1
  search()
}

async function search() {
  loading.value = true
  error.value = null
  try {
    let actualLifecycle = lifecycleFilter.value

    // Map tab names to actual filter values
    if (lifecycleFilter.value === 'trash') {
      // trash tab: query with archived lifecycle + deleted flag
      // Backend doesn't return soft-deleted records, so show archived only
      actualLifecycle = 'archived'
    } else if (['draft', 'reviewing', 'locked', 'approved'].includes(lifecycleFilter.value)) {
      actualLifecycle = 'active'
    } else if (lifecycleFilter.value === 'producing') {
      actualLifecycle = 'active'
    } else if (lifecycleFilter.value === 'completed') {
      actualLifecycle = 'active'
    }

    const params = buildWarehouseQuery({
      page: currentPage.value,
      pageSize,
      keyword: keyword.value || undefined,
      contentStatus: ['draft', 'reviewing', 'locked', 'approved', 'needs_revision'].includes(lifecycleFilter.value)
        ? lifecycleFilter.value === 'approved' ? 'approved' : lifecycleFilter.value === 'draft' ? 'draft' : lifecycleFilter.value === 'reviewing' ? 'reviewing' : lifecycleFilter.value === 'locked' ? 'locked' : undefined
        : contentFilter.value || undefined,
      productionStatus: lifecycleFilter.value === 'producing'
        ? undefined : lifecycleFilter.value === 'completed' ? 'completed' : productionFilter.value || undefined,
      commercialStatus: commercialFilter.value || undefined,
      lifecycleStatus: actualLifecycle,
      sort: sortFilter.value
    })

    const res = await contentProjectApi.list(params)
    const data = res?.data?.data
    items.value = data?.items || []
    total.value = data?.total || 0
  } catch (e) {
    error.value = e?.response?.data?.message || '加载项目列表失败'
  } finally {
    loading.value = false
  }
}

function clearFilters() {
  keyword.value = ''
  contentFilter.value = ''
  productionFilter.value = ''
  commercialFilter.value = ''
  lifecycleFilter.value = 'active'
  sortFilter.value = 'updated_desc'
  currentPage.value = 1
  search()
}

function openDetail(project) {
  router.push(`/warehouse/${project.id}`)
}

async function onCommand({ action, project }) {
  const label = project.name || project.id
  try {
    switch (action) {
      case 'rename': {
        const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
          inputValue: project.name,
          confirmButtonText: '确认',
          cancelButtonText: '取消'
        })
        if (value) {
          await contentProjectApi.update(project.id, { name: value, revision: project.revision })
          ElMessage.success('已重命名')
        }
        break
      }
      case 'archive':
        await ElMessageBox.confirm(`确认归档「${label}」？`, '归档项目', { type: 'info' })
        await contentProjectApi.archive(project.id, {
          revision: project.revision,
          idempotency_key: crypto.randomUUID(),
          comment: 'warehouse archive'
        })
        ElMessage.success('已归档')
        break
      case 'restore':
        await contentProjectApi.restore(project.id, {
          revision: project.revision,
          idempotency_key: crypto.randomUUID(),
          comment: 'warehouse restore'
        })
        ElMessage.success('已恢复')
        break
      case 'duplicate':
        await contentProjectApi.duplicate(project.id, {
          revision: project.revision,
          idempotency_key: crypto.randomUUID(),
          comment: 'warehouse duplicate'
        })
        ElMessage.success('已复制')
        break
      case 'trash':
        await ElMessageBox.confirm(
          `确认将「${label}」移入回收站？`,
          '移入回收站',
          { type: 'error', confirmButtonClass: 'el-button--danger' }
        )
        await contentProjectApi.moveToTrash(project.id, {
          revision: project.revision,
          idempotency_key: crypto.randomUUID(),
          comment: 'warehouse trash'
        })
        ElMessage.success('已移入回收站')
        break
      case 'continue':
        router.push(primaryActionRoute({ id: project.id, primary_action: project.primary_action }))
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

onMounted(search)
</script>

<style scoped>
.warehouse-page { max-width: 1200px; margin: 0 auto; padding: 24px; }
.center-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.center-header h2 { margin: 0; font-size: 22px; font-weight: 800; }
.warehouse-tabs { margin-bottom: 12px; }
.center-toolbar { display: flex; gap: 10px; margin-bottom: 20px; flex-wrap: wrap; }
.center-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 14px; }
.center-pagination { display: flex; justify-content: center; margin-top: 24px; }
.empty-state, .error-state { padding: 60px 0; text-align: center; }
.card-skeleton { border: 1px solid #ebeef5; border-radius: 8px; padding: 16px; background: #fff; }
</style>
