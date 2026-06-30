<template>
  <div>
    <div class="flex justify-between items-center mb-lg">
      <div class="flex gap-sm">
        <el-select v-model="sourceFilter" placeholder="来源" clearable style="width:120px" @change="load">
          <el-option label="全部" value="" />
          <el-option label="我创建的" value="CREATED" />
          <el-option label="市场领取" value="MARKET_CLAIMED" />
          <el-option label="项目生成" value="PROJECT_GENERATED" />
        </el-select>
        <el-select v-model="typeFilter" placeholder="类型" clearable style="width:120px" @change="load">
          <el-option v-for="cat in activeCategories" :key="cat.key" :label="cat.label" :value="cat.key" />
        </el-select>
      </div>
      <el-button type="primary" @click="showCreate = true">创建资产</el-button>
    </div>

    <el-skeleton v-if="market.library.loading" :rows="4" animated />
    <el-empty v-else-if="market.library.error" :description="market.library.error">
      <el-button type="primary" @click="load">重试</el-button>
    </el-empty>
    <div v-else-if="market.library.data">
      <el-empty v-if="!market.library.data.items?.length" description="资产库为空，从公共市场领取或创建新资产" />
      <div v-else class="grid4">
        <div v-for="item in market.library.data.items" :key="item.id" class="card" style="padding:16px">
          <div class="font-semibold">{{ item.name }}</div>
          <p class="text-sm text-muted mt-sm">{{ item.assetType }} · {{ sourceTypeLabel(item.sourceType) }}</p>
          <div class="mt-sm flex gap-sm">
            <el-button size="small" @click="market.editAsset(item.id, { name: item.name, rowVersion: item.rowVersion })">编辑</el-button>
            <el-button size="small" type="success" @click="onPublish(item)">发布</el-button>
            <el-button size="small" type="danger" @click="market.archiveAsset(item.id, item.rowVersion).then(load)">归档</el-button>
          </div>
        </div>
      </div>
      <div v-if="market.library.data.pagination?.total_pages > 1" class="flex justify-center mt-lg">
        <el-pagination v-model:current-page="page" :page-size="20" :total="market.library.data.pagination.total"
                       layout="prev, pager, next" @current-change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ACTIVE_CATEGORIES, assetTypeLabel, sourceTypeLabel } from '../assetMarketState'

const props = defineProps({ market: { type: Object, required: true } })
const sourceFilter = ref('')
const typeFilter = ref('')
const page = ref(1)
const showCreate = ref(false)

const activeCategories = ACTIVE_CATEGORIES.map(k => ({ key: k, label: assetTypeLabel(k) }))

function load() {
  props.market.fetchLibrary({ sourceType: sourceFilter.value || undefined, assetType: typeFilter.value || undefined, page: page.value })
}

function onPublish(item) {
  const body = {
    version_id: item.currentVersionId,
    name: item.name,
    tags: item.tags || [],
    author_name: '用户',
    row_version: item.rowVersion
  }
  // Enterprise workspaces require approval flow
  const wsType = props.market.getWorkspaceType()
  const promise = wsType === 'enterprise'
    ? props.market.requestPublish(item.id, body)
    : props.market.publishAsset(item.id, body)
  promise.then(() => load())
}

load()
</script>
