<template>
  <div>
    <div class="flex items-center justify-between mb-lg">
      <h2 class="text-xl font-bold">我的剧本仓库</h2>
      <div class="flex gap-sm">
        <el-input v-model="search" placeholder="搜索剧本…" style="width:200px" clearable />
        <el-select v-model="filterStatus" placeholder="全部状态" style="width:130px" clearable>
          <el-option label="草稿" value="draft" />
          <el-option label="已上架" value="listed" />
          <el-option label="已售出" value="sold" />
        </el-select>
        <el-button size="small" type="primary" @click="$router.push('/script-gen')">
          <el-icon><Plus /></el-icon> 新建剧本
        </el-button>
      </div>
    </div>

    <div class="flex gap-sm mb-lg flex-wrap items-center">
      <span class="text-sm font-semibold">标签筛选：</span>
      <span v-for="t in ['全部','言情','重生','甜宠','现代','爽文']" :key="t"
            class="tag" :class="{ selected: activeTag === t || (t === '全部' && !activeTag) }"
            @click="activeTag = t === '全部' ? '' : t">{{ t }}</span>
    </div>

    <div class="flex flex-col gap-md">
      <div v-for="s in filteredScripts" :key="s.id" class="card card-hover" style="padding:16px">
        <div class="flex items-center justify-between">
          <div>
            <span class="font-semibold">{{ s.title }}</span>
            <span :class="['badge', s.status==='draft'?'badge-warning':s.status==='listed'?'badge-success':'badge-accent']" style="margin-left:12px">{{ statusText(s.status) }}</span>
          </div>
          <div class="flex gap-sm">
            <el-button size="small" @click="$router.push('/script-gen')"><el-icon><Edit /></el-icon> 编辑</el-button>
            <el-button type="primary" size="small" @click="$router.push('/canvas/' + s.id)"><el-icon><Brush /></el-icon> 进入画布</el-button>
            <el-button size="small" @click="$router.push('/tag-editor/' + s.id)"><el-icon><PriceTag /></el-icon> 标签</el-button>
          </div>
        </div>
        <p class="text-sm text-muted mt-sm">
          {{ s.episodes }}集 · <span v-for="t in s.tags" :key="t" class="tag selected" style="margin-right:4px">{{ t }}</span>
          <span v-if="s.price" class="badge badge-accent">¥{{ s.price }}</span>
          <span v-if="s.soldCount">已售{{ s.soldCount }}份</span>
          · {{ s.words }} · {{ s.time }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Brush, PriceTag } from '@element-plus/icons-vue'
import { scriptApi } from '@/api/script'

const search = ref('')
const filterStatus = ref('')
const activeTag = ref('')
const scripts = ref([])
const loading = ref(false)

// 加载剧本列表
async function loadScripts() {
  loading.value = true
  try {
    const params = { page: 1, page_size: 50 }
    if (search.value) params.keyword = search.value
    if (filterStatus.value) params.status = filterStatus.value
    if (activeTag.value) params.genre = activeTag.value

    const res = await scriptApi.getScripts(params)
    const items = res.data?.items || res.data?.records || res.data || []
    scripts.value = items.map(s => ({
      id: s.id || s.uuid,
      uuid: s.uuid,
      title: s.title || '未命名',
      status: s.status || 'draft',
      episodes: s.episode_count || 0,
      tags: extractTags(s),
      words: formatWords(s.total_words || 0),
      price: s.price,
      soldCount: s.sales_count || s.soldCount || 0,
      time: formatTime(s.created_at || s.updated_at || s.createdAt)
    }))
  } catch (e) {
    ElMessage.warning('无法加载剧本列表，请确认已登录')
  } finally {
    loading.value = false
  }
}

// 从前端实体提取标签
function extractTags(s) {
  const tags = []
  if (s.genre_tag) tags.push(s.genre_tag)
  const plots = typeof s.plot_tags === 'string' ? safeParse(s.plot_tags) : s.plot_tags
  if (Array.isArray(plots)) tags.push(...plots.slice(0, 2))
  return [...new Set(tags)]
}

function safeParse(val) {
  try { return JSON.parse(val) } catch { return val }
}

function formatWords(n) {
  if (n >= 10000) return Math.round(n / 10000) + '万字'
  if (n >= 1000) return Math.round(n / 1000) + '千字'
  return n + '字'
}

function formatTime(t) {
  if (!t) return ''
  const diff = Date.now() - new Date(t).getTime()
  const mins = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (mins < 60) return mins + '分钟前'
  if (hours < 24) return hours + '小时前'
  return days + '天前'
}

const filteredScripts = computed(() => scripts.value.filter(s => {
  if (search.value && !s.title.includes(search.value)) return false
  if (filterStatus.value && s.status !== filterStatus.value) return false
  return true
}))

function statusText(s) { return { draft: '草稿', listed: '已上架', sold: '已售出' }[s] || s }

onMounted(loadScripts)
</script>
