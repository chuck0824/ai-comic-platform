<template>
  <div class="asset-history-page">
    <div class="page-header">
      <h2><el-icon><FolderOpened /></el-icon> 资产生成历史</h2>
      <div class="flex gap-sm">
        <el-input v-model="searchKeyword" placeholder="搜索资产..." size="small" style="width:200px" clearable />
        <el-select v-model="filterType" size="small" clearable placeholder="类型" style="width:120px">
          <el-option label="全部" value="" />
          <el-option label="图片" value="image" />
          <el-option label="视频" value="video" />
          <el-option label="音频" value="audio" />
        </el-select>
      </div>
    </div>

    <div v-if="loading" class="text-center py-xl text-muted">加载中...</div>

    <div v-else class="asset-grid">
      <div v-for="asset in filteredAssets" :key="asset.id || asset.uuid"
           class="asset-card"
           :class="{ favorite: asset.favorite }"
           draggable="true"
           @dragstart="onDragStart($event, asset)"
           @click="previewAsset(asset)">
        <div class="asset-thumb">
          <img v-if="asset.thumbnail_url" :src="asset.thumbnail_url" :alt="asset.name" />
          <div v-else class="thumb-placeholder"><el-icon :size="40"><component :is="typeIcon(asset.type)" /></el-icon></div>
        </div>
        <div class="asset-info">
          <div class="asset-name">{{ asset.name || '未命名' }}</div>
          <div class="asset-meta">
            <span class="badge badge-default">{{ asset.type }}</span>
            <span class="text-xs text-muted">{{ formatDate(asset.created_at) }}</span>
          </div>
          <div v-if="asset.prompt" class="asset-prompt text-xs text-muted">{{ truncate(asset.prompt, 60) }}</div>
        </div>
        <div class="asset-actions">
          <el-button size="small" circle @click.stop="sendToCanvas(asset)"><el-icon><Position /></el-icon></el-button>
          <el-button size="small" circle @click.stop="toggleFavorite(asset)">
            <el-icon><StarFilled v-if="asset.favorite" /><Star v-else /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <div v-if="filteredAssets.length === 0 && !loading" class="text-center py-xl text-muted">
      暂无资产
    </div>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="previewAssetData?.name || '预览'" width="500px">
      <div v-if="previewAssetData">
        <div class="asset-thumb" style="height:300px;margin-bottom:16px">
          <img v-if="previewAssetData.thumbnail_url" :src="previewAssetData.thumbnail_url" :alt="previewAssetData.name" style="max-width:100%;max-height:300px" />
          <div v-else class="thumb-placeholder" style="font-size:80px"><el-icon :size="80"><component :is="typeIcon(previewAssetData.type)" /></el-icon></div>
        </div>
        <div class="text-sm mb-sm"><strong>类型:</strong> {{ previewAssetData.type }}</div>
        <div class="text-sm mb-sm"><strong>模型:</strong> {{ previewAssetData.model_id || '—' }}</div>
        <div v-if="previewAssetData.prompt" class="text-sm mb-sm"><strong>Prompt:</strong> {{ previewAssetData.prompt }}</div>
        <div class="text-sm text-muted"><strong>创建时间:</strong> {{ formatDate(previewAssetData.created_at) }}</div>
      </div>
      <template #footer>
        <el-button size="small" @click="previewVisible = false">关闭</el-button>
        <el-button size="small" type="primary" @click="sendToCanvas(previewAssetData); previewVisible = false">发送到画布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { generationApi } from '@/api/generation'

const assets = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const filterType = ref('')

const filteredAssets = computed(() => {
  let result = assets.value
  if (filterType.value) result = result.filter(a => a.type === filterType.value)
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    result = result.filter(a =>
      (a.name || '').toLowerCase().includes(kw) ||
      (a.prompt || '').toLowerCase().includes(kw))
  }
  return result
})

const previewVisible = ref(false)
const previewAssetData = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    // 不传 userId，后端从 SecurityContext 读取
    const res = await generationApi.getAssetHistory({})
    assets.value = res.data || []
  } catch (e) { loading.value = false }
  finally { loading.value = false }
})

function typeIcon(type) {
  return { image: 'Picture', video: 'VideoCamera', audio: 'Headset' }[type] || 'Folder'
}

function formatDate(d) {
  if (!d) return ''
  return new Date(d).toLocaleDateString('zh-CN')
}

function truncate(text, len) {
  return text && text.length > len ? text.slice(0, len) + '...' : text
}

function onDragStart(e, asset) {
  e.dataTransfer.setData('application/json', JSON.stringify({
    type: 'asset', assetId: asset.id || asset.uuid, assetType: asset.type
  }))
}

function previewAsset(asset) {
  previewAssetData.value = asset
  previewVisible.value = true
}

async function sendToCanvas(asset) {
  try {
    await generationApi.sendAssetToCanvas(asset.id || asset.uuid)
    ElMessage.success('已发送到画布')
  } catch (e) { ElMessage.error('发送失败') }
}

function toggleFavorite(asset) {
  asset.favorite = !asset.favorite
}
</script>

<style scoped>
.asset-history-page { padding: 24px; background: #0f172a; min-height: 100vh; color: #e0e0e0; --text-secondary:#a1a1aa; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
h2 { margin: 0; font-size: 20px; }
.asset-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
.asset-card { background: #1a1a2e; border: 1px solid #2a2a3e; border-radius: 10px; overflow: hidden;
  cursor: pointer; transition: transform 0.15s; position: relative; }
.asset-card:hover { transform: translateY(-2px); border-color: #4f46e5; }
.asset-card.favorite { border-color: #f59e0b; }
.asset-thumb { height: 160px; background: #111; display: flex; align-items: center; justify-content: center; }
.asset-thumb img { width: 100%; height: 100%; object-fit: cover; }
.thumb-placeholder { font-size: 40px; }
.asset-info { padding: 10px; }
.asset-name { font-size: 13px; font-weight: 600; margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.asset-meta { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.asset-prompt { line-height: 1.3; }
.asset-actions { position: absolute; top: 8px; right: 8px; display: flex; gap: 4px; opacity: 0; transition: opacity 0.15s; }
.asset-card:hover .asset-actions { opacity: 1; }
.badge { display: inline-block; padding: 1px 6px; border-radius: 4px; font-size: 10px; }
.badge-default { background: #1e293b; color: #94a3b8; }
.text-center { text-align: center; } .py-xl { padding: 60px 0; }
.text-muted { color: #a1a1aa; } .text-xs { font-size: 11px; }
.flex { display: flex; } .gap-sm { gap: 8px; }
</style>
