<template>
  <div class="asset-market-page">
    <h2 class="text-xl font-bold mb-lg">AI资产与风格模型市场</h2>

    <!-- 频道 Tab -->
    <div class="tabs">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-item"
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </div>
    </div>

    <!-- 面板 -->
    <div class="panel-container mt-lg">
      <PublicMarketPanel v-if="activeTab === 'public'" :market="market" />
      <WorkspaceAssetPanel v-else-if="activeTab === 'library'" :market="market" />
      <PublishRequestPanel v-else-if="activeTab === 'publish'" :market="market" />
      <div v-else-if="activeTab === 'review'" class="card p-xl">
        <PublishReviewPanel :market="market" />
      </div>
    </div>

    <!-- 详情抽屉 -->
    <AssetDetailDrawer
      v-model:visible="detailVisible"
      :listing="currentDetail"
      :loading="market.detail.loading"
      :error="market.detail.error"
      @claim="onClaim"
      @favorite="onFavorite"
      @apply="onApply"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAssetMarket } from './asset-market/useAssetMarket'
import { canReview } from './asset-market/assetMarketState'
import PublicMarketPanel from './asset-market/components/PublicMarketPanel.vue'
import WorkspaceAssetPanel from './asset-market/components/WorkspaceAssetPanel.vue'
import PublishRequestPanel from './asset-market/components/PublishRequestPanel.vue'
import PublishReviewPanel from './asset-market/components/PublishReviewPanel.vue'
import AssetDetailDrawer from './asset-market/components/AssetDetailDrawer.vue'

const route = useRoute()
const router = useRouter()
const market = useAssetMarket()

const activeTab = ref(route.query.tab || 'public')
const detailVisible = ref(false)
const currentDetail = ref(null)

const tabs = computed(() => {
  const items = [
    { key: 'public', label: '公共市场' },
    { key: 'library', label: 'Workspace资产库' },
    { key: 'publish', label: '发布管理' }
  ]
  // Approval tab only visible for users with asset.publish.approve permission
  if (canReview(market.permissions)) {
    items.push({ key: 'review', label: '审批中心' })
  }
  return items
})

// Ensure activeTab is valid
if (!tabs.value.find(t => t.key === activeTab.value)) {
  activeTab.value = 'public'
}

function switchTab(key) {
  activeTab.value = key
  router.replace({ query: { ...route.query, tab: key } })
}

onMounted(() => {
  market.fetchListings()
})

// Watch tab for auto-loading
watch(activeTab, (tab) => {
  if (tab === 'public') market.fetchListings()
  else if (tab === 'library') market.fetchLibrary()
  else if (tab === 'publish' || tab === 'review') market.fetchPublishRequests()
})

// ---- Event handlers ----
function onClaim(listingId) {
  market.claimListing(listingId).then(result => {
    ElMessage.success('领取成功！资产已加入你的 Workspace 资产库')
    detailVisible.value = false
  }).catch(e => ElMessage.error(e.response?.data?.message || '领取失败'))
}

function onFavorite(listingId) {
  market.favoriteListing(listingId).then(() => {
    ElMessage.success('已收藏')
  })
}

function onApply(assetId, projectId) {
  const body = {
    project_id: projectId,
    target_type: 'PROJECT',
    idempotency_key: crypto.randomUUID?.() ?? `${Date.now()}-${assetId}`
  }
  market.applyAsset(assetId, body).then(result => {
    ElMessage.success(result.change_summary || '应用成功')
    detailVisible.value = false
  })
}
</script>
