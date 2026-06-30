<template>
  <div>
    <div class="content-header">
      <div>
        <h2 class="text-xl font-bold">{{ settingLabel }}设定</h2>
        <p class="text-sm text-muted mt-sm">共 {{ total }} 条{{ settingLabel }}设定。</p>
      </div>
      <el-button size="small" type="primary" @click="showCreate = true" :disabled="readOnly">新建{{ settingLabel }}</el-button>
    </div>

    <!-- Search -->
    <div class="toolbar">
      <el-input v-model="searchKeyword" placeholder="搜索名称或摘要…" size="small" clearable style="width: 220px" @input="onSearch" />
      <el-select v-model="filterStatus" size="small" placeholder="状态" clearable style="width: 120px" @change="fetchList">
        <el-option label="草稿" value="draft" />
        <el-option label="已确认" value="confirmed" />
        <el-option label="待补充" value="needs_enrichment" />
      </el-select>
    </div>

    <!-- List + Detail -->
    <div class="setting-layout">
      <div class="setting-list">
        <div v-if="loading" v-loading="loading" style="min-height:200px" />
        <div v-else-if="items.length === 0" class="empty">暂无{{ settingLabel }}设定</div>
        <div v-for="item in items" :key="item.id"
          class="setting-item" :class="{ active: selectedId === item.id }"
          @click="selectItem(item)">
          <div class="item-name">{{ item.canonical_name }}</div>
          <div class="item-summary">{{ item.summary || '无摘要' }}</div>
          <div class="item-status">
            <el-tag size="small" :type="statusTagType(item.status)">{{ statusLabel(item.status) }}</el-tag>
          </div>
        </div>
      </div>

      <div class="setting-detail" v-if="selectedItem">
        <el-form label-position="top" size="small">
          <el-form-item label="规范名">
            <el-input v-model="selectedItem.canonical_name" :disabled="readOnly" />
          </el-form-item>
          <el-form-item label="摘要">
            <el-input v-model="selectedItem.summary" type="textarea" :rows="2" :disabled="readOnly" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="selectedItem.status" :disabled="readOnly">
              <el-option label="草稿" value="draft" />
              <el-option label="已确认" value="confirmed" />
              <el-option label="待补充" value="needs_enrichment" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="detail-actions">
          <el-button size="small" :disabled="readOnly" @click="saveDetail">保存</el-button>
          <el-button size="small" @click="copyItem">复制</el-button>
          <el-button size="small" type="danger" :disabled="readOnly" @click="archiveItem">归档</el-button>
        </div>
      </div>
      <div class="setting-detail empty-detail" v-else>
        选择左侧设定查看详情
      </div>
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreate" :title="'新建' + settingLabel" width="400px">
      <el-form label-position="top" size="small">
        <el-form-item label="规范名">
          <el-input v-model="newName" placeholder="输入名称" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="newSummary" type="textarea" :rows="2" placeholder="输入摘要" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="createItem">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { contentProjectApi } from '@/api/contentProject'
import { SETTING_TYPE_LABELS } from './workEditorData'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  projectId: Number,
  settingType: { type: String, required: true },
  readOnly: Boolean
})

const settingLabel = computed(() => SETTING_TYPE_LABELS[props.settingType] || props.settingType)

const loading = ref(false)
const items = ref([])
const selectedId = ref(null)
const selectedItem = ref(null)
const searchKeyword = ref('')
const filterStatus = ref('')
const total = ref(0)

const showCreate = ref(false)
const newName = ref('')
const newSummary = ref('')

async function fetchList() {
  if (!props.projectId) return
  loading.value = true
  try {
    const res = await contentProjectApi.listSettings(props.projectId, {
      type: props.settingType,
      keyword: searchKeyword.value || undefined,
      status: filterStatus.value || undefined,
      page: 1, page_size: 100
    })
    const data = res?.data ?? res
    items.value = data?.items ?? data?.records ?? []
    total.value = data?.pagination?.total ?? items.value.length
  } finally {
    loading.value = false
  }
}

function selectItem(item) {
  selectedId.value = item.id
  selectedItem.value = { ...item }
}

async function saveDetail() {
  try {
    const res = await contentProjectApi.updateSetting(props.projectId, selectedItem.value.id, {
      canonical_name: selectedItem.value.canonical_name,
      summary: selectedItem.value.summary,
      status: selectedItem.value.status,
      revision: selectedItem.value.revision
    })
    ElMessage.success('已保存')
    fetchList()
  } catch {
    ElMessage.error('保存失败')
  }
}

async function copyItem() {
  await contentProjectApi.copySetting(props.projectId, selectedItem.value.id)
  ElMessage.success('已复制')
  fetchList()
}

async function archiveItem() {
  try {
    await ElMessageBox.confirm('归档后将不再出现在列表中。', '确认归档？', { type: 'warning' })
    await contentProjectApi.archiveSetting(props.projectId, selectedItem.value.id)
    ElMessage.success('已归档')
    selectedId.value = null
    selectedItem.value = null
    fetchList()
  } catch { /* cancelled */ }
}

async function createItem() {
  if (!newName.value.trim()) return
  await contentProjectApi.createSetting(props.projectId, {
    setting_type: props.settingType,
    canonical_name: newName.value.trim(),
    summary: newSummary.value.trim()
  })
  ElMessage.success('已创建')
  showCreate.value = false
  newName.value = ''
  newSummary.value = ''
  fetchList()
}

function onSearch() { fetchList() }

function statusTagType(s) {
  if (s === 'confirmed') return 'success'
  if (s === 'needs_enrichment') return 'warning'
  if (s === 'archived') return 'info'
  return ''
}

function statusLabel(s) {
  if (s === 'confirmed') return '已确认'
  if (s === 'needs_enrichment') return '待补充'
  if (s === 'archived') return '已归档'
  return '草稿'
}

onMounted(fetchList)
watch(() => props.projectId, fetchList)
</script>

<style scoped>
.content-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 16px; }
.setting-layout { display: grid; grid-template-columns: 240px 1fr; gap: 16px; min-height: 360px; }
.setting-list { border: 1px solid var(--border); border-radius: 8px; overflow-y: auto; max-height: 460px; }
.setting-item { padding: 10px 12px; cursor: pointer; border-bottom: 1px solid var(--border); }
.setting-item:hover { background: var(--accent-bg); }
.setting-item.active { background: var(--accent-bg); border-left: 3px solid var(--accent); }
.item-name { font-weight: 600; font-size: 13px; }
.item-summary { font-size: 12px; color: var(--text-secondary); margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-status { margin-top: 6px; }
.setting-detail { padding: 16px; border: 1px solid var(--border); border-radius: 8px; }
.empty-detail { display: flex; align-items: center; justify-content: center; color: var(--text-tertiary); }
.empty { padding: 40px; text-align: center; color: var(--text-tertiary); }
.detail-actions { display: flex; gap: 8px; margin-top: 16px; }
</style>
