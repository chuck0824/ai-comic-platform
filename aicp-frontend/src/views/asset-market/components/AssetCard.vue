<template>
  <div class="card card-hover" style="padding:16px;cursor:pointer" @click="$emit('click')">
    <div class="canvas-mock" style="min-height:100px;margin-bottom:12px;border-radius:8px;display:flex;align-items:center;justify-content:center;color:#94a3b8;font-size:12px">
      {{ item.thumbnailUrl ? '' : 'Preview' }}
    </div>
    <div class="font-semibold">{{ item.name }}</div>
    <p class="text-sm text-muted">
      @{{ item.authorName }} ·
      <el-icon style="vertical-align:-1px;color:#f59e0b"><StarFilled /></el-icon>
      {{ item.rating ?? '-' }} · {{ formatCount(item.useCount) }}使用
    </p>
    <div class="mt-sm">
      <span v-for="tag in (item.tags || []).slice(0, 3)" :key="tag" class="badge badge-neutral" style="margin-right:4px">{{ tag }}</span>
    </div>
    <div class="mt-sm flex items-center gap-sm">
      <span class="badge badge-success">免费</span>
      <span v-if="item.claimed" class="badge" style="background:#e0f2fe;color:#0369a1">已领取</span>
    </div>
  </div>
</template>

<script setup>
defineProps({ item: { type: Object, required: true } })
defineEmits(['click'])

function formatCount(n) {
  if (!n) return '0'
  return n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n)
}
</script>
