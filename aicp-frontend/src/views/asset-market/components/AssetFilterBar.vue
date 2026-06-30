<template>
  <div class="filter-bar flex gap-md items-center mb-lg">
    <el-input v-model="filters.keyword" placeholder="搜索资产名称、作者或标签..." clearable
              style="width:280px" @clear="emitSearch" @keyup.enter="emitSearch">
      <template #prefix><el-icon><Search /></el-icon></template>
    </el-input>
    <el-select v-model="filters.type" placeholder="全部分类" clearable style="width:140px" @change="emitSearch">
      <el-option v-for="cat in activeCategories" :key="cat.key" :label="cat.label" :value="cat.key" />
    </el-select>
    <el-select v-model="filters.sort" style="width:120px" @change="emitSearch">
      <el-option label="最新" value="latest" />
      <el-option label="最热" value="popular" />
      <el-option label="评分" value="rating" />
    </el-select>
    <el-button type="primary" @click="emitSearch" :icon="Search">搜索</el-button>
  </div>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'
import { ACTIVE_CATEGORIES, assetTypeLabel } from '../assetMarketState'

const props = defineProps({ filters: { type: Object, required: true } })
const emit = defineEmits(['search'])

const activeCategories = ACTIVE_CATEGORIES.map(k => ({ key: k, label: assetTypeLabel(k) }))

function emitSearch() {
  emit('search')
}
</script>
