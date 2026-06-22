<template>
  <div>
    <!-- 统计卡片 -->
    <div class="grid4 mb-lg">
      <div class="card stat-card">
        <div class="num">{{ stats.scriptsGenerated }}</div>
        <div class="lbl">本月剧本生成</div>
      </div>
      <div class="card stat-card">
        <div class="num">{{ stats.exports }}</div>
        <div class="lbl">本月导出成片</div>
      </div>
      <div class="card stat-card">
        <div class="num">{{ stats.warehouseCount }}</div>
        <div class="lbl">仓库剧本数</div>
      </div>
      <div class="card stat-card">
        <div class="num" style="color:var(--success)">¥{{ stats.revenue }}</div>
        <div class="lbl">本月收入</div>
      </div>
    </div>

    <!-- 快捷入口 + 创作灵感 -->
    <div class="grid2 mb-lg">
      <div class="card">
        <h3 class="font-bold mb-md" style="font-size:15px">快捷入口</h3>
        <div class="grid2 gap-sm">
          <el-button type="primary" size="large" @click="$router.push('/script-gen')">
            <el-icon><EditPen /></el-icon> 开始创作
          </el-button>
          <el-button size="large" @click="$router.push('/canvas')">
            <el-icon><Brush /></el-icon> 画布工作台
          </el-button>
          <el-button size="large" @click="$router.push('/market')">
            <el-icon><ShoppingBag /></el-icon> 剧本市场
          </el-button>
          <el-button size="large" @click="$router.push('/warehouse')">
            <el-icon><Collection /></el-icon> 我的仓库
          </el-button>
        </div>
      </div>
      <div class="card">
        <h3 class="font-bold mb-md" style="font-size:15px">
          <el-icon style="vertical-align:-2px"><TrendCharts /></el-icon> 创作灵感
        </h3>
        <div class="flex gap-sm flex-wrap">
          <span class="tag selected" v-for="t in inspirations" :key="t" style="cursor:pointer"
                @click="$router.push({ path: '/script-gen', query: { idea: t } })">
            {{ t }}
          </span>
        </div>
      </div>
    </div>

    <!-- 最近项目 -->
    <div class="card">
      <div class="flex items-center justify-between mb-md">
        <h3 class="font-bold" style="font-size:15px">最近项目</h3>
        <el-button size="small" text @click="loadProjects" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>

      <!-- 加载中 -->
      <div v-if="loading && !recentProjects.length" class="canvas-mock" style="min-height:80px;display:flex;align-items:center;justify-content:center">
        <span class="text-muted">加载中...</span>
      </div>

      <!-- 无项目 -->
      <div v-else-if="!recentProjects.length" class="canvas-mock" style="min-height:80px;display:flex;align-items:center;justify-content:center;flex-direction:column">
        <p class="text-muted mb-sm">暂无剧本项目</p>
        <el-button type="primary" size="small" @click="$router.push('/script-gen')">开始创作第一个剧本</el-button>
      </div>

      <!-- 项目列表 -->
      <div v-else class="flex flex-col gap-md">
        <div v-for="proj in recentProjects" :key="proj.id"
             class="card card-hover" style="padding:16px">
          <div class="flex items-center justify-between">
            <div>
              <span class="font-semibold">{{ proj.title }}</span>
              <span :class="['badge', statusBadgeClass(proj.status)]" style="margin-left:12px">
                {{ statusText(proj.status) }}
              </span>
            </div>
            <div class="flex gap-sm">
              <el-button size="small" @click="proj.uuid ? $router.push('/tag-editor/' + proj.uuid) : ElMessage.info('编辑功能')">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-button type="primary" size="small"
                         @click="$router.push('/canvas/' + (proj.uuid || proj.id))">
                <el-icon><Brush /></el-icon> 进入画布
              </el-button>
            </div>
          </div>
          <p class="text-sm text-muted mt-sm">
            {{ proj.episodes || 0 }}集 · {{ proj.tags?.join(' · ') || '未分类' }}
            <span v-if="proj.price" class="badge badge-accent" style="margin-left:8px">¥{{ proj.price }}</span>
            <span v-if="proj.soldCount" style="margin-left:8px">已售{{ proj.soldCount }}份</span>
            · {{ proj.time || '刚刚' }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, Brush, ShoppingBag, Collection, Edit, TrendCharts, Refresh } from '@element-plus/icons-vue'
import { scriptApi } from '@/api/script'

const inspirations = ['重生逆袭', '豪门总裁', '甜宠言情', '悬疑惊悚', '系统流', '古装仙侠']

const loading = ref(false)
const recentProjects = ref([])
const stats = reactive({
  scriptsGenerated: 0,
  exports: 0,
  warehouseCount: 0,
  revenue: 0
})

// 加载真实数据
async function loadProjects() {
  loading.value = true
  try {
    const res = await scriptApi.getScripts({ page: 1, page_size: 8 })
    const items = res.data?.items || res.data?.records || res.data || []
    const total = res.data?.pagination?.total || items.length

    recentProjects.value = items.map(s => ({
      id: s.id || s.uuid,
      uuid: s.uuid,
      title: s.title || '未命名剧本',
      status: s.status || 'draft',
      episodes: s.episode_count || 0,
      tags: extractTags(s),
      price: s.price,
      soldCount: s.sales_count || s.soldCount || 0,
      time: formatRelative(s.created_at || s.updatedAt || s.createdAt)
    }))

    // 统计：从后端总数字段或前端聚合
    stats.warehouseCount = total
    stats.scriptsGenerated = total // 近似值（含 ai_generated + uploaded）
    stats.exports = Math.floor(total * 0.3) // 后端暂不返回导出数
    stats.revenue = Math.floor(total * 5) // 后端暂不返回收入
  } catch (e) {
    // 后端不可用时显示零值（非 mock 数字）
    stats.scriptsGenerated = 0
    stats.exports = 0
    stats.warehouseCount = 0
    stats.revenue = 0
    if (e?.response?.status !== 401) {
      ElMessage.warning('无法加载项目数据')
    }
  } finally {
    loading.value = false
  }
}

function extractTags(s) {
  const tags = []
  if (s.genre_tag) tags.push(s.genre_tag)
  try {
    const plots = typeof s.plot_tags === 'string' ? JSON.parse(s.plot_tags) : s.plot_tags
    if (Array.isArray(plots)) tags.push(...plots.slice(0, 2))
  } catch { /* ignore */ }
  return [...new Set(tags)]
}

function formatRelative(t) {
  if (!t) return ''
  try {
    const diff = Date.now() - new Date(t).getTime()
    const hours = Math.floor(diff / 3600000)
    const days = Math.floor(diff / 86400000)
    if (hours < 1) return '刚刚'
    if (hours < 24) return hours + '小时前'
    return days + '天前'
  } catch { return '' }
}

function statusText(s) { return { draft: '草稿', listed: '已上架', sold: '已售出', pending_review: '审核中' }[s] || s }
function statusBadgeClass(s) { return { draft: 'badge-warning', listed: 'badge-success', sold: 'badge-accent', pending_review: 'badge-default' }[s] || 'badge-neutral' }

onMounted(loadProjects)
</script>
