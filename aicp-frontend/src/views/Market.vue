<template>
  <div class="market-page">
    <div class="market-header">
      <h2 class="text-2xl font-bold">剧本交易市场</h2>
      <p class="text-muted text-base mt-sm">发现优质剧本，加速内容创作</p>
    </div>

    <!-- 搜索+筛选 -->
    <div class="card mb-lg" style="padding:18px">
      <el-input v-model="filters.keyword" placeholder="搜索剧本、作者、标签…" style="max-width:400px" clearable
        @change="onFilterChange" />
      <div class="mt-md"><span class="text-sm font-semibold">4轴标签筛选</span></div>
      <div class="flex gap-sm flex-wrap mt-sm">
        <span class="text-sm font-semibold" style="min-width:44px;line-height:28px">题材：</span>
        <span v-for="g in genreOptions" :key="g" class="tag"
          :class="{ selected: filters.genre === g || (g === '全部' && !filters.genre) }"
          @click="filters.genre = g === '全部' ? '' : g; onFilterChange()">{{ g }}</span>
      </div>
      <div class="flex gap-sm mt-md">
        <el-select v-model="filters.sort" style="width:140px" @change="onFilterChange">
          <el-option label="最新上架" value="latest" />
          <el-option label="热门推荐" value="popular" />
          <el-option label="销量最高" value="sales" />
          <el-option label="评分最高" value="rating" />
        </el-select>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="listings.loading" class="flex flex-col items-center gap-sm py-xl">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span class="text-muted">加载中…</span>
    </div>

    <!-- Error -->
    <div v-else-if="listings.error" class="empty-state">
      <el-empty description="加载失败">
        <el-button size="small" @click="fetchListings">重试</el-button>
      </el-empty>
    </div>

    <!-- Empty -->
    <div v-else-if="!listings.data || listings.data.items.length === 0" class="empty-state">
      <el-empty :description="filters.keyword ? '未找到匹配的剧本' : '暂无上架剧本'" />
    </div>

    <!-- Grid -->
    <div v-else class="grid4">
      <div v-for="item in listings.data.items" :key="item.id" class="card card-interactive" style="padding:16px"
        @click="goDetail(item.id)">
        <div class="cover-placeholder">
          <el-icon :size="32"><VideoCamera /></el-icon>
          <span>封面图</span>
        </div>
        <div class="font-semibold mt-sm truncate">{{ item.title }}</div>
        <p class="text-sm text-muted">@{{ item.authorDisplayName }}</p>
        <p class="text-sm text-muted mt-sm">
          <span v-for="lic in item.licenses" :key="lic.licenseType"
            :class="lic.priceCents > 0 ? 'badge badge-accent' : 'badge badge-success'"
            style="margin-right:4px">
            {{ licenseLabel(lic.licenseType) }}
            {{ lic.priceCents > 0 ? '¥' + (lic.priceCents / 100).toFixed(2) : '免费' }}
          </span>
        </p>
        <p class="text-sm text-muted mt-sm">
          已售{{ item.salesCount }} · {{ item.episodeCount }}集
        </p>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="listings.data && listings.data.totalPages > 1" class="flex justify-center mt-lg">
      <el-pagination
        v-model:current-page="filters.page"
        :page-size="filters.pageSize"
        :total="listings.data.total"
        layout="prev, pager, next"
        @current-change="onPageChange" />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { VideoCamera, Loading } from '@element-plus/icons-vue'
import { useTradeMarket } from './trade/useTradeMarket'
import { licenseLabel } from './trade/tradeState'

const router = useRouter()
const { listings, filters, fetchListings, syncQuery } = useTradeMarket()

const genreOptions = ['全部', '言情', '悬疑', '科幻', '仙侠', '都市', '古装', '奇幻']

function onFilterChange() {
  filters.page = 1
  syncQuery({ ...filters })
  fetchListings()
}

function onPageChange(page) {
  filters.page = page
  syncQuery({ ...filters })
  fetchListings()
}

function goDetail(id) {
  router.push(`/market/${id}`)
}

onMounted(() => {
  fetchListings()
})
</script>

<style scoped>
.market-page { max-width: 1400px; }
.market-header { margin-bottom: 24px; }
.market-header h2 { margin-bottom: 4px; }
.cover-placeholder {
  min-height: 120px; border-radius: var(--radius-md);
  background: var(--bg-surface-hover); border: 1px dashed var(--border);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 4px; color: var(--text-tertiary); font-size: 13px;
  transition: border-color .2s ease;
}
.card-interactive:hover .cover-placeholder { border-color: var(--accent-border); }
.card-interactive { cursor: pointer; }
</style>
