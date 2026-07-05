<template>
  <div class="script-card" :class="{ archived: script.status === 'delisted' }">
    <!-- Header: cover + title -->
    <div class="script-card-header">
      <div class="script-card-cover">
        <el-image v-if="script.coverImageUrl" :src="script.coverImageUrl" fit="cover" class="cover-img">
          <template #error><el-icon :size="28"><Document /></el-icon></template>
        </el-image>
        <el-icon v-else :size="28"><Document /></el-icon>
      </div>
      <div class="script-card-title-wrap">
        <span class="script-card-title">{{ script.title }}</span>
        <span v-if="script.source === 'purchased'" class="purchased-badge">已购</span>
      </div>
    </div>

    <!-- Meta: status + genre + episode count -->
    <div class="script-card-meta">
      <el-tag :type="statusType" size="small">{{ statusLabel }}</el-tag>
      <el-tag v-if="genreLabel" size="small" type="info">{{ genreLabel }}</el-tag>
      <span class="script-card-count">{{ script.episodeCount || 0 }}集</span>
      <span v-if="script.totalWords" class="script-card-words">{{ formatWords(script.totalWords) }}</span>
    </div>

    <!-- Episode progress (draft / editing) -->
    <div v-if="showEpisodeProgress" class="script-card-episodes">
      <el-progress
        :percentage="episodeProgress"
        :stroke-width="6"
        :show-text="false"
        :color="progressColor"
      />
      <span class="episode-text">{{ script.completedEpisodes || 0 }}/{{ script.episodeCount || 0 }} 集已完成</span>
    </div>

    <!-- Listed/Sold: price + sales -->
    <div v-if="isListedOrSold" class="script-card-commerce">
      <span v-if="salesPrice" class="commerce-item">💰 售价 ¥{{ salesPrice }}</span>
      <span v-if="script.salesCount" class="commerce-item">已售 {{ script.salesCount }} 份</span>
    </div>

    <!-- Purchased: license + source -->
    <div v-if="script.status === 'purchased'" class="script-card-license">
      <span class="license-item">授权：普通授权</span>
      <span v-if="script.authorUserId" class="license-item">来源：@用户{{ script.authorUserId }}</span>
    </div>

    <!-- Tags row: show plot + tone tags -->
    <div v-if="plotTags.length || toneTags.length" class="script-card-tags">
      <el-tag v-for="t in plotTags" :key="t" size="small" effect="plain" class="mini-tag">{{ t }}</el-tag>
      <el-tag v-for="t in toneTags" :key="t" size="small" effect="plain" class="mini-tag tone-tag">{{ t }}</el-tag>
    </div>

    <!-- Footer: time + actions -->
    <div class="script-card-footer">
      <span class="script-card-time">{{ timeText }}</span>
      <div class="script-card-actions">
        <!-- Primary action -->
        <el-button v-if="actions.canEdit" size="small" @click="$emit('edit', script)">编辑</el-button>
        <el-button v-if="actions.canView" size="small" @click="$emit('edit', script)">查看</el-button>
        <el-button v-if="actions.canGenerateComic" size="small" type="success" @click="$emit('command', { action: 'generateComic', script })">生成漫剧</el-button>

        <!-- More dropdown -->
        <el-dropdown v-if="hasMenuActions" trigger="click" @command="handleCommand">
          <el-button size="small" :icon="MoreFilled" />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="actions.canList" command="list">上架交易</el-dropdown-item>
              <el-dropdown-item v-if="actions.canDelist" command="delist">下架</el-dropdown-item>
              <el-dropdown-item v-if="actions.canViewSales" command="viewSales">查看售卖</el-dropdown-item>
              <el-dropdown-item v-if="actions.canDownload" command="download">下载剧本</el-dropdown-item>
              <el-dropdown-item v-if="actions.canDelete" command="delete" divided>删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { MoreFilled, Document } from '@element-plus/icons-vue'
import {
  STATUS_LABELS, STATUS_TYPES, GENRE_LABELS,
  scriptActions, formatTimeAgo, parseTags
} from './scriptWarehouseViewModel.js'

const props = defineProps({
  script: { type: Object, required: true }
})

const emit = defineEmits(['edit', 'command'])

const statusLabel = computed(() => STATUS_LABELS[props.script.status] || props.script.status || '草稿')
const statusType = computed(() => STATUS_TYPES[props.script.status] || '')
const genreLabel = computed(() => GENRE_LABELS[props.script.genreTag] || props.script.genreTag)
const actions = computed(() => scriptActions(props.script))
const hasMenuActions = computed(() =>
  actions.value.canList || actions.value.canDelist ||
  actions.value.canViewSales || actions.value.canDownload ||
  actions.value.canDelete
)
const timeText = computed(() => formatTimeAgo(props.script.updatedAt))

const isListedOrSold = computed(() =>
  props.script.status === 'listed' || props.script.status === 'sold'
)

// Episode progress for draft scripts
const showEpisodeProgress = computed(() =>
  props.script.episodeCount > 0 &&
  (props.script.status === 'draft' || props.script.status === 'pending_review')
)
const episodeProgress = computed(() => {
  if (!props.script.episodeCount) return 0
  return Math.round(((props.script.completedEpisodes || 0) / props.script.episodeCount) * 100)
})
const progressColor = computed(() => {
  if (episodeProgress.value >= 100) return '#67c23a'
  if (episodeProgress.value >= 50) return '#409eff'
  return '#e6a23c'
})

// Tags
const plotTags = computed(() => parseTags(props.script.plotTags))
const toneTags = computed(() => parseTags(props.script.toneTags))

// Price: derive from script data or placeholder
const salesPrice = computed(() => {
  // Script entity doesn't have a direct price field,
  // but salesCount exists; price could come from listing data
  return props.script.price || null
})

function formatWords(count) {
  if (!count) return ''
  if (count >= 10000) return `${(count / 10000).toFixed(1)}万字`
  return `${count}字`
}

function handleCommand(cmd) {
  emit('command', { action: cmd, script: props.script })
}
</script>

<style scoped>
.script-card { border: 1px solid #e4e7ed; border-radius: 8px; padding: 16px; transition: box-shadow .2s; }
.script-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.08); }
.script-card.archived { opacity: .6; }

.script-card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.script-card-cover {
  width: 40px; height: 40px; border-radius: 6px; background: #f4f4f5;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  overflow: hidden;
}
.cover-img { width: 100%; height: 100%; }
.script-card-title-wrap { display: flex; align-items: center; gap: 6px; min-width: 0; }
.script-card-title { font-weight: 600; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.purchased-badge { font-size: 11px; color: #e6a23c; background: #fdf6ec; padding: 1px 6px; border-radius: 4px; flex-shrink: 0; }

.script-card-meta { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; flex-wrap: wrap; }
.script-card-count { color: #71717a; font-size: 12px; }
.script-card-words { color: #a1a1aa; font-size: 12px; }

.script-card-episodes { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.script-card-episodes .el-progress { flex: 1; }
.episode-text { color: #71717a; font-size: 12px; white-space: nowrap; }

.script-card-commerce { display: flex; gap: 16px; margin-bottom: 8px; }
.commerce-item { color: #e6a23c; font-size: 12px; font-weight: 500; }

.script-card-license { display: flex; gap: 16px; margin-bottom: 8px; }
.license-item { color: #71717a; font-size: 12px; }

.script-card-tags { display: flex; gap: 4px; margin-bottom: 10px; flex-wrap: wrap; }
.mini-tag { font-size: 11px; }
.tone-tag { --el-tag-bg-color: #f0f5ff; --el-tag-border-color: #d6e4ff; --el-tag-text-color: #3b82f6; }

.script-card-footer { display: flex; justify-content: space-between; align-items: center; }
.script-card-time { color: #a1a1aa; font-size: 12px; }
.script-card-actions { display: flex; gap: 4px; }
</style>
