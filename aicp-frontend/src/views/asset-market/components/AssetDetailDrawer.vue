<template>
  <el-drawer :model-value="visible" :title="listing?.name ?? '资产详情'" size="480px" @update:model-value="$emit('update:visible', $event)">
    <template v-if="loading">
      <el-skeleton :rows="6" animated />
    </template>
    <template v-else-if="error">
      <el-result icon="error" :title="error">
        <template #extra><el-button @click="$emit('retry')">重试</el-button></template>
      </el-result>
    </template>
    <template v-else-if="listing">
      <div class="mb-md">
        <div class="canvas-mock" style="height:200px;border-radius:8px;display:flex;align-items:center;justify-content:center;color:#94a3b8">预览图</div>
      </div>
      <p class="text-sm text-muted mb-md">@{{ listing.authorName }} · {{ listing.assetType }} · {{ listing.useCount ?? 0 }} 次使用</p>
      <p class="mb-md" v-if="listing.description">{{ listing.description }}</p>
      <div class="mb-md">
        <span v-for="tag in (listing.tags || [])" :key="tag" class="badge badge-neutral" style="margin-right:4px">{{ tag }}</span>
      </div>
      <div class="mb-md" v-if="listing.recommendedParams">
        <h4 class="font-semibold mb-sm">推荐参数</h4>
        <pre class="text-xs" style="background:#f1f5f9;padding:8px;border-radius:4px">{{ JSON.stringify(listing.recommendedParams, null, 2) }}</pre>
      </div>
      <div class="flex gap-md mt-lg">
        <el-button v-if="!listing.claimed" type="primary" @click="$emit('claim', listing.id)">免费领取</el-button>
        <el-button v-else type="success" disabled>已领取</el-button>
        <el-button @click="$emit('favorite', listing.id)">收藏</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
defineProps({
  visible: Boolean,
  listing: Object,
  loading: Boolean,
  error: String
})
defineEmits(['update:visible', 'claim', 'favorite', 'apply', 'retry'])
</script>
