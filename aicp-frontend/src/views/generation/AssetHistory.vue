<template>
  <div class="asset-history-page">
    <div class="page-header">
      <h2><el-icon><FolderOpened /></el-icon> 资产生成历史</h2>
      <div class="header-controls">
        <el-input v-model="state.keyword" placeholder="搜索资产..." size="small" style="width:200px" clearable />
        <el-select v-model="state.assetType" size="small" clearable placeholder="分类" style="width:130px" @change="onFilterChange">
          <el-option label="全部" value="" />
          <el-option v-for="(label, key) in typeLabels" :key="key" :label="label" :value="key" />
        </el-select>
        <el-select v-model="state.collection" size="small" clearable placeholder="集合" style="width:110px" @change="onFilterChange">
          <el-option label="全部资产" value="" />
          <el-option label="收藏" value="FAVORITES" />
          <el-option label="回收站" value="TRASH" />
        </el-select>
        <el-select v-model="state.pageSize" size="small" style="width:90px" @change="setPageSize(state.pageSize)">
          <el-option v-for="s in [24,48,96]" :key="s" :label="`${s}条/页`" :value="s" />
        </el-select>
        <el-button size="small" @click="clearFilters">清空筛选</el-button>
      </div>
    </div>

    <div v-if="records.loading" class="center py-xl">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span class="ml-sm text-muted">加载中...</span>
    </div>

    <div v-else-if="records.error" class="center py-xl text-muted">
      加载失败：{{ records.error }}
      <el-button size="small" class="ml-sm" @click="fetchRecords()">重试</el-button>
    </div>

    <div v-else class="asset-grid">
      <div v-for="rec in records.items" :key="rec.recordId"
           class="asset-card"
           :class="{ 'is-task': rec.recordKind === 'TASK' }"
           @click="openDetail(rec.recordKind, rec.recordId)">
        <div class="asset-thumb">
          <img v-if="rec.previewUrl" :src="rec.previewUrl" :alt="rec.name" />
          <div v-else class="thumb-placeholder">
            <el-icon :size="32"><component :is="mediaIcon(rec.mediaType)" /></el-icon>
          </div>
          <span class="status-tag" :class="'status-' + (rec.status || '').toLowerCase()">
            {{ statusLabel(rec.status) }}
          </span>
        </div>
        <div class="asset-info">
          <div class="asset-name">{{ rec.name || '未命名' }}</div>
          <div class="asset-meta">
            <span class="badge">{{ typeLabels[rec.assetType] || rec.assetType }}</span>
            <span class="text-xs">{{ rec.modelId || '—' }}</span>
          </div>
          <div v-if="rec.errorSummary" class="text-xs text-red">{{ rec.errorSummary }}</div>
          <div v-if="rec.progress != null" class="progress-bar">
            <div class="progress-fill" :style="{ width: rec.progress + '%' }"></div>
          </div>
        </div>
        <div class="asset-actions">
          <el-button v-if="rec.allowedActions?.includes('DOWNLOAD')" size="small" circle @click.stop="downloadAsset(rec)">
            <el-icon><Download /></el-icon>
          </el-button>
          <el-button v-if="rec.allowedActions?.includes('FAVORITE')" size="small" circle @click.stop="toggleFavorite(rec.recordId)">
            <el-icon><StarFilled v-if="rec.favorite" /><Star v-else /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <div v-if="!records.loading && records.items.length === 0" class="center py-xl text-muted">
      暂无资产记录
    </div>

    <div v-if="records.total > 0" class="pagination-row">
      <el-pagination
        :current-page="state.page"
        :page-size="state.pageSize"
        :total="records.total"
        layout="prev, pager, next"
        @current-change="setPage" />
      <span class="text-xs text-muted">共 {{ records.total }} 条</span>
    </div>

    <!-- Detail Drawer -->
    <el-drawer v-model="drawerVisible" :title="detail.data?.name || '详情'" size="480px" @close="closeDetail">
      <div v-if="detail.loading" class="center py-xl">加载中...</div>
      <div v-else-if="detail.data" class="detail-body">
        <div class="detail-section">
          <h4>概览</h4>
          <div class="detail-row"><span>名称</span><span>{{ detail.data.name }}</span></div>
          <div class="detail-row"><span>分类</span><span>{{ typeLabels[detail.data.assetType] }}</span></div>
          <div class="detail-row"><span>状态</span><span>{{ statusLabel(detail.data.status) }}</span></div>
          <div class="detail-row"><span>创建时间</span><span>{{ formatDate(detail.data.createdAt) }}</span></div>
        </div>
        <div v-if="detail.data.activities?.length" class="detail-section">
          <h4>活动记录</h4>
          <div v-for="(act, i) in detail.data.activities" :key="i" class="activity-item">
            <span>{{ act.description }}</span>
            <span class="text-xs text-muted">{{ formatDate(act.createdAt) }}</span>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- Batch bar -->
    <div v-if="selectedUuids.size > 0" class="batch-bar">
      <span>已选 {{ selectedUuids.size }} 项</span>
      <el-button size="small" type="danger" @click="trashAssets([...selectedUuids])">删除</el-button>
      <el-button size="small" @click="restoreAssets([...selectedUuids])">恢复</el-button>
      <el-button size="small" @click="selectedUuids = new Set()">取消选择</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { assetHistoryApi } from '@/api/assetHistory'
import { parseAssetHistoryQuery, serializeAssetHistoryState, mapRecordCard } from '@/views/asset-history/assetHistoryState'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const typeLabels = {
  CHECKPOINT:'底模', LORA:'LoRA', STYLE_PACK:'风格包', PROMPT:'提示词',
  CHARACTER:'角色', SCENE:'场景', PROP:'道具', STORYBOARD:'分镜',
  VOICE:'配音', MUSIC:'音乐', OTHER:'其他'
}

const state = ref(parseAssetHistoryQuery(route.query))
const projects = ref([])
const records = ref({ items:[], facets:null, total:0, loading:false, error:null })
const detail = ref({ data:null, loading:false, error:null })
const selectedUuids = ref(new Set())
const drawerVisible = ref(false)

let requestSeq = 0

async function fetchRecords() {
  const seq = ++requestSeq
  records.value.loading = true
  records.value.error = null
  try {
    const p = { page: state.value.page, pageSize: state.value.pageSize }
    if (state.value.assetType) p.asset_type = state.value.assetType
    if (state.value.keyword) p.keyword = state.value.keyword
    if (state.value.collection) p.collection = state.value.collection
    if (state.value.sort) p.sort = state.value.sort
    const res = await assetHistoryApi.queryRecords(p)
    if (seq !== requestSeq) return
    records.value.items = (res.data?.items || []).map(mapRecordCard)
    records.value.facets = res.data?.facets || null
    records.value.total = res.data?.total || 0
  } catch (e) {
    if (seq !== requestSeq) return
    records.value.error = e.message || '加载失败'
  } finally {
    if (seq === requestSeq) records.value.loading = false
  }
}

function syncQuery() {
  router.replace({ query: serializeAssetHistoryState(state.value) })
}

function setPage(p) { state.value.page = p; syncQuery(); fetchRecords() }
function setPageSize(s) { state.value.pageSize = s; state.value.page = 1; syncQuery(); fetchRecords() }
function onFilterChange() { state.value.page = 1; syncQuery(); fetchRecords() }
function clearFilters() {
  state.value = parseAssetHistoryQuery({})
  syncQuery()
  fetchRecords()
}

async function toggleFavorite(recordId) {
  try {
    const uuid = recordId.replace('asset-', '')
    await assetHistoryApi.favorite(uuid)
    ElMessage.success('已收藏')
    fetchRecords()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

async function downloadAsset(rec) {
  try {
    const uuid = rec.id.replace('asset-', '')
    const res = await assetHistoryApi.getDownloadUrl(uuid)
    window.open(res.data, '_blank')
  } catch (e) { ElMessage.error('下载失败') }
}

async function openDetail(kind, id) {
  drawerVisible.value = true
  detail.value.loading = true
  try {
    const res = await assetHistoryApi.getDetail(kind.replace('asset-', '').replace('task-', ''), id)
    detail.value.data = res.data
  } catch (e) { detail.value.error = e.message }
  finally { detail.value.loading = false }
}

function closeDetail() { detail.value.data = null; drawerVisible.value = false }

async function trashAssets(uuids) {
  try {
    await assetHistoryApi.batchOperate({ assetUuids: uuids, operation: 'TRASH' })
    ElMessage.success(`已删除 ${uuids.length} 项`)
    selectedUuids.value = new Set()
    fetchRecords()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

async function restoreAssets(uuids) {
  try {
    await assetHistoryApi.batchOperate({ assetUuids: uuids, operation: 'RESTORE' })
    ElMessage.success(`已恢复 ${uuids.length} 项`)
    selectedUuids.value = new Set()
    fetchRecords()
  } catch (e) { ElMessage.error(e.message || '恢复失败') }
}

function statusLabel(s) {
  const map = { PENDING:'排队中', RUNNING:'生成中', SUCCEEDED:'已完成', FAILED:'失败', CANCELED:'已取消', ACTIVE:'活跃', TRASHED:'回收站' }
  return map[s?.toUpperCase()] || s || '—'
}

function mediaIcon(m) {
  return { IMAGE:'PictureFilled', VIDEO:'VideoCameraFilled', AUDIO:'Headset', DATA:'Document' }[m] || 'Folder'
}

function formatDate(d) { return d ? new Date(d).toLocaleDateString('zh-CN') : '—' }

let kwTimer = null
watch(() => state.value.keyword, () => { clearTimeout(kwTimer); kwTimer = setTimeout(fetchRecords, 300) })

onMounted(() => fetchRecords())
</script>

<style scoped>
.asset-history-page { padding:24px; background:#0f172a; min-height:100vh; color:#e0e0e0; }
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; flex-wrap:wrap; gap:12px; }
.header-controls { display:flex; gap:8px; align-items:center; flex-wrap:wrap; }
h2 { margin:0; font-size:20px; }
.asset-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(220px, 1fr)); gap:16px; }
.asset-card { background:#1a1a2e; border:1px solid #2a2a3e; border-radius:10px; overflow:hidden; cursor:pointer; transition:transform .15s; position:relative; }
.asset-card:hover { transform:translateY(-2px); border-color:#4f46e5; }
.asset-card.is-task { border-color:#3b82f6; }
.asset-thumb { height:160px; background:#111; display:flex; align-items:center; justify-content:center; position:relative; }
.asset-thumb img { width:100%; height:100%; object-fit:cover; }
.thumb-placeholder { color:#475569; }
.status-tag { position:absolute; top:8px; left:8px; padding:2px 8px; border-radius:4px; font-size:11px; background:#1e293b; color:#94a3b8; }
.status-tag.status-running { background:#1e3a5f; color:#60a5fa; }
.status-tag.status-failed { background:#3b1e1e; color:#f87171; }
.status-tag.status-succeeded, .status-tag.status-active { background:#1e3b1e; color:#4ade80; }
.asset-info { padding:10px; }
.asset-name { font-size:13px; font-weight:600; margin-bottom:4px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.asset-meta { display:flex; justify-content:space-between; align-items:center; }
.asset-actions { position:absolute; top:8px; right:8px; display:flex; gap:4px; opacity:0; transition:opacity .15s; }
.asset-card:hover .asset-actions { opacity:1; }
.badge { padding:1px 6px; border-radius:4px; font-size:10px; background:#1e293b; color:#94a3b8; }
.progress-bar { height:3px; background:#1e293b; border-radius:2px; margin-top:6px; }
.progress-fill { height:100%; background:#4f46e5; border-radius:2px; transition:width .3s; }
.pagination-row { display:flex; justify-content:center; align-items:center; gap:12px; margin-top:24px; }
.batch-bar { position:fixed; bottom:0; left:0; right:0; background:#1e1e3f; border-top:1px solid #2a2a4e; padding:12px 24px; display:flex; align-items:center; gap:12px; z-index:100; }
.detail-body { padding:0 8px; }
.detail-section { margin-bottom:20px; }
.detail-section h4 { font-size:14px; margin-bottom:8px; color:#94a3b8; }
.detail-row { display:flex; justify-content:space-between; padding:6px 0; font-size:13px; border-bottom:1px solid #1e293b; }
.activity-item { display:flex; justify-content:space-between; padding:4px 0; font-size:12px; }
.center { text-align:center; } .py-xl { padding:60px 0; }
.text-muted { color:#a1a1aa; } .text-xs { font-size:11px; } .text-red { color:#f87171; }
.ml-sm { margin-left:8px; }
</style>
