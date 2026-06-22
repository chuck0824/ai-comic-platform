<template>
  <div>
    <div class="flex items-center gap-md mb-lg">
      <el-button size="small" @click="$router.back()">← 返回</el-button>
      <h2 class="text-xl font-bold">分镜编辑器 — {{ scriptTitle }}</h2>
      <span :class="['badge', tierBadge]">{{ tierLabel }}</span>
    </div>

    <div class="tabs mb-lg">
      <div class="tab-item" :class="{ active: tier === 'A' }" @click="tier = 'A'">A档 · 编导速看</div>
      <div class="tab-item" :class="{ active: tier === 'B' }" @click="tier = 'B'">B档 · 导演确认 <span class="badge badge-neutral">即将上线</span></div>
      <div class="tab-item" :class="{ active: tier === 'C' }" @click="tier = 'C'">C档 · 生产交付 <span class="badge badge-neutral">即将上线</span></div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="canvas-mock" style="min-height:200px;display:flex;align-items:center;justify-content:center">
      <span class="text-muted">加载分镜数据...</span>
    </div>

    <!-- A档: 分镜表 -->
    <template v-else-if="tier === 'A'">
      <!-- 场景信息 -->
      <div class="grid2 gap-lg mb-lg">
        <div class="card">
          <h3 class="font-bold mb-md">剧本信息</h3>
          <table class="table-wrap"><tbody>
            <tr><td style="width:100px;font-weight:600">标题</td><td>{{ scriptTitle }}</td></tr>
            <tr><td style="font-weight:600">题材</td><td>{{ scriptInfo.genre || '—' }}</td></tr>
            <tr><td style="font-weight:600">集数</td><td>{{ scriptInfo.episodes || 0 }} 集</td></tr>
            <tr><td style="font-weight:600">状态</td><td>{{ statusText(scriptInfo.status) }}</td></tr>
          </tbody></table>
        </div>
        <div class="card" v-if="shots.length">
          <h3 class="font-bold mb-md">分镜概览</h3>
          <p class="text-sm">共 <strong>{{ shots.length }}</strong> 个镜头</p>
          <p class="text-sm text-muted mt-sm">
            景别分布: {{ shotSizeSummary }}
          </p>
          <div class="flex gap-sm mt-md">
            <el-button size="small" @click="ElMessage.info('批量生图功能即将上线')"><el-icon><PictureFilled /></el-icon> 批量生图</el-button>
            <el-button size="small" @click="ElMessage.info('节奏分析功能即将上线')"><el-icon><DataAnalysis /></el-icon> 节奏分析</el-button>
            <el-button type="primary" size="small" @click="goToCanvas">
              <el-icon><Brush /></el-icon> 送入画布工作台
            </el-button>
          </div>
        </div>
      </div>

      <!-- 分镜表 -->
      <div class="card" v-if="shots.length">
        <h3 class="font-bold mb-md">A档 · 分镜表</h3>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>镜号</th>
                <th>时长</th>
                <th>景别</th>
                <th>运镜</th>
                <th>画面内容</th>
                <th>对白</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="shot in shots" :key="shot.id || shot.uuid || shot.shot_no">
                <td><code>{{ shot.shot_no || shot.shotNo || '—' }}</code></td>
                <td>{{ shot.duration ? (shot.duration / 1000).toFixed(1) + 's' : '—' }}</td>
                <td>{{ shot.shot_size || shot.shotSize || '—' }}</td>
                <td>{{ shot.camera_motion || shot.cameraMotion || '—' }}</td>
                <td class="text-sm">{{ shot.visual_description || shot.visualDescription || '—' }}</td>
                <td class="text-sm">{{ getDialogueText(shot) }}</td>
                <td><span :class="['badge', shotStatusBadge(shot)]">{{ shotStatusText(shot) }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="flex gap-sm mt-lg flex-wrap">
          <el-button size="small" @click="ElMessage.info('表格视图')">表格视图</el-button>
          <el-button size="small" @click="ElMessage.info('卡片视图')">卡片视图</el-button>
          <el-button size="small" @click="ElMessage.info('时间轴视图')">时间轴视图</el-button>
          <el-button size="small" @click="ElMessage.info('节奏分析功能即将上线')">节奏分析</el-button>
          <el-button size="small" @click="ElMessage.info('B档功能即将上线')">升档至 B 档</el-button>
          <el-button type="primary" size="small" @click="goToCanvas">
            <el-icon><Brush /></el-icon> 送入画布工作台
          </el-button>
        </div>
      </div>

      <!-- 无分镜 -->
      <div v-else class="canvas-mock" style="min-height:200px;display:flex;align-items:center;justify-content:center;flex-direction:column">
        <p class="text-muted mb-sm">该剧本暂无分镜数据</p>
        <el-button type="primary" size="small" @click="$router.push('/script-gen')">去生成分镜</el-button>
      </div>
    </template>

    <!-- B档 / C档 -->
    <div v-else class="canvas-mock" style="min-height:300px;display:flex;align-items:center;justify-content:center;flex-direction:column">
      <p class="font-bold">{{ tier === 'B' ? 'B档 · 导演确认' : 'C档 · 生产交付' }}</p>
      <p class="text-sm text-muted mt-sm">此功能将在后续版本上线</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Brush } from '@element-plus/icons-vue'
import { scriptApi } from '@/api/script'
import { canvasApi } from '@/api/canvas'

const route = useRoute()
const tier = ref('A')
const loading = ref(true)
const shots = ref([])

const scriptId = computed(() => route.params.scriptId || '')
const scriptTitle = ref('加载中...')
const scriptInfo = reactive({
  genre: '',
  episodes: 0,
  status: ''
})

// 分镜大小分布摘要
const shotSizeSummary = computed(() => {
  const count = {}
  shots.value.forEach(s => {
    const sz = s.shot_size || s.shotSize || '未知'
    count[sz] = (count[sz] || 0) + 1
  })
  return Object.entries(count).map(([k, v]) => `${k}×${v}`).join(' · ') || '—'
})

const tierLabel = computed(() => tier.value === 'A' ? 'A档·编导速看' : tier.value === 'B' ? 'B档·导演确认' : 'C档·生产交付')
const tierBadge = computed(() => tier.value === 'A' ? 'badge-accent' : 'badge-neutral')

function getDialogueText(shot) {
  if (!shot) return '—'
  if (typeof shot.dialogue === 'string') {
    try { const d = JSON.parse(shot.dialogue); return d.text || d.character + ': ' + d.text || shot.dialogue }
    catch { return shot.dialogue }
  }
  return shot.dialogue_text || shot.dialogueText || '—'
}

function statusText(s) { return { draft: '草稿', listed: '已上架', sold: '已售出' }[s] || s || '—' }
function shotStatusText(s) {
  const img = s.image_status || s.imageStatus
  const vid = s.video_status || s.videoStatus
  if (vid === 'completed') return '视频完成'
  if (img === 'completed') return '图片完成'
  if (img === 'generating') return '生成中'
  return '待生成'
}
function shotStatusBadge(s) {
  const vid = s.video_status || s.videoStatus
  const img = s.image_status || s.imageStatus
  if (vid === 'completed' || img === 'completed') return 'badge-success'
  if (img === 'generating' || vid === 'generating') return 'badge-warning'
  return 'badge-neutral'
}

function goToCanvas() {
  const id = scriptId.value
  if (id) ElMessage.success('正在打开画布...')
  // 如果有 projectId，尝试用 projectId；否则用 scriptId
  // Canvas route accepts projectId param
  $router.push('/canvas/' + id)
}

// 从 Vue Router
import { useRouter } from 'vue-router'
const $router = useRouter()

async function loadStoryboard() {
  loading.value = true
  try {
    const sid = scriptId.value
    if (!sid) {
      ElMessage.warning('缺少剧本ID')
      loading.value = false
      return
    }

    // 获取剧本详情
    try {
      const scriptRes = await scriptApi.getScript(sid)
      const s = scriptRes.data
      if (s) {
        scriptTitle.value = s.title || '未命名剧本'
        scriptInfo.genre = s.genre_tag || ''
        scriptInfo.episodes = s.episode_count || 0
        scriptInfo.status = s.status || ''
      }
    } catch {
      scriptTitle.value = '剧本 #' + sid
    }

    // 获取分镜数据：尝试从 canvas API 获取（分镜属于画布项目）
    try {
      // 先尝试从 script API 获取（如果有分镜端点）
      const res = await canvasApi.getShots(sid)
      shots.value = res.data || []
    } catch {
      // 降级：如果 scriptId 同时也是 projectId，尝试通过 canvas API
      ElMessage.info('该剧本暂无分镜数据，请先在画布中创建脚本节点并生成分镜')
      shots.value = []
    }
  } catch (e) {
    ElMessage.warning('无法加载分镜数据')
    shots.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadStoryboard)
</script>
