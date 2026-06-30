<template>
  <div>
    <AssetFilterBar :filters="market.filters" @search="market.fetchListings()" />
    <el-skeleton v-if="market.listings.loading" :rows="4" animated />
    <el-empty v-else-if="market.listings.error" :description="market.listings.error">
      <el-button type="primary" @click="market.fetchListings()">重试</el-button>
    </el-empty>
    <div v-else-if="market.listings.data">
      <div v-if="!market.listings.data.items?.length" class="text-center py-xl">
        <el-empty description="暂无公开资产" />
      </div>
      <div v-else class="grid4">
        <AssetCard
          v-for="item in market.listings.data.items"
          :key="item.id"
          :item="item"
          @click="openDetail(item.id)"
        />
      </div>
      <div v-if="market.listings.data.pagination?.total_pages > 1" class="flex justify-center mt-lg">
        <el-pagination
          v-model:current-page="market.filters.page"
          :page-size="market.filters.pageSize"
          :total="market.listings.data.pagination.total"
          layout="prev, pager, next"
          @current-change="market.fetchListings()"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import AssetFilterBar from './AssetFilterBar.vue'
import AssetCard from './AssetCard.vue'

const props = defineProps({ market: { type: Object, required: true } })
const emit = defineEmits(['openDetail'])

function openDetail(id) {
  props.market.fetchDetail(id)
  emit('openDetail', id)
}
</script>
