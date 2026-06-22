<template>
  <div>
    <div class="flex items-center gap-md mb-lg">
      <el-button size="small" @click="$router.push('/warehouse')"><el-icon><ArrowLeft /></el-icon> 返回仓库</el-button>
      <h2 class="text-xl font-bold">标签编辑 — {{ scriptTitle }}</h2>
      <span class="text-sm text-muted">剧本 · 已保存 · 总字数 58,000</span>
    </div>

    <div style="display:grid;grid-template-columns:200px 1fr;gap:24px">
      <!-- 左侧导航 -->
      <div class="card">
        <h3 class="font-bold mb-md" style="font-size:14px">作品信息</h3>
        <div class="flex flex-col gap-sm">
          <span class="badge badge-accent" style="padding:8px 12px;cursor:pointer;width:100%;justify-content:flex-start">
            <el-icon><PriceTag /></el-icon> 标签
          </span>
          <span class="badge badge-neutral" style="padding:8px 12px;cursor:pointer;width:100%;justify-content:flex-start">
            <el-icon><Reading /></el-icon> 简介
          </span>
          <span class="badge badge-neutral" style="padding:8px 12px;cursor:pointer;width:100%;justify-content:flex-start">
            <el-icon><Collection /></el-icon> 总纲
          </span>
        </div>
        <div style="border-top:1px solid var(--border);margin-top:24px;padding-top:20px">
          <h3 class="font-bold mb-sm" style="font-size:14px">设定</h3>
          <div class="flex flex-col gap-sm">
            <span class="badge badge-neutral" style="padding:8px 12px;cursor:pointer;width:100%;justify-content:flex-start"><el-icon><User /></el-icon> 角色</span>
            <span class="badge badge-neutral" style="padding:8px 12px;cursor:pointer;width:100%;justify-content:flex-start"><el-icon><Picture /></el-icon> 背景</span>
            <span class="badge badge-neutral" style="padding:8px 12px;cursor:pointer;width:100%;justify-content:flex-start"><el-icon><Flag /></el-icon> 势力</span>
            <span class="badge badge-neutral" style="padding:8px 12px;cursor:pointer;width:100%;justify-content:flex-start"><el-icon><Location /></el-icon> 地点</span>
            <span class="badge badge-neutral" style="padding:8px 12px;cursor:pointer;width:100%;justify-content:flex-start"><el-icon><Box /></el-icon> 物品</span>
          </div>
        </div>
      </div>

      <!-- 右侧标签网格 -->
      <div class="card">
        <h2 class="text-xl font-bold mb-lg">4轴标签</h2>

        <!-- 题材 1/1 -->
        <div class="mb-lg">
          <div class="flex items-center gap-sm mb-sm">
            <strong style="font-size:16px">题材</strong>
            <span class="text-sm text-muted">{{ selectedGenre ? 1 : 0 }} / 1</span>
          </div>
          <div class="flex gap-sm flex-wrap">
            <span v-for="t in genres" :key="t" class="tag"
                  :class="{ selected: selectedGenre === t, disabled: selectedGenre && selectedGenre !== t }"
                  @click="selectedGenre = selectedGenre === t ? '' : t">{{ t }}</span>
          </div>
        </div>

        <!-- 情节 ≤3 -->
        <div class="mb-lg">
          <div class="flex items-center gap-sm mb-sm">
            <strong style="font-size:16px">情节</strong>
            <span class="text-sm text-muted">{{ selectedPlots.length }} / 3</span>
          </div>
          <div class="flex gap-sm flex-wrap">
            <span v-for="t in plots" :key="t" class="tag"
                  :class="{ selected: selectedPlots.includes(t), disabled: selectedPlots.length >= 3 && !selectedPlots.includes(t) }"
                  @click="toggleArray(selectedPlots, t, 3)">{{ t }}</span>
          </div>
        </div>

        <!-- 情绪 ≤3 -->
        <div class="mb-lg">
          <div class="flex items-center gap-sm mb-sm">
            <strong style="font-size:16px">情绪</strong>
            <span class="text-sm text-muted">{{ selectedTones.length }} / 3</span>
          </div>
          <div class="flex gap-sm flex-wrap">
            <span v-for="t in tones" :key="t" class="tag"
                  :class="{ selected: selectedTones.includes(t), disabled: selectedTones.length >= 3 && !selectedTones.includes(t) }"
                  @click="toggleArray(selectedTones, t, 3)">{{ t }}</span>
          </div>
        </div>

        <!-- 时空 1/1 -->
        <div class="mb-lg">
          <div class="flex items-center gap-sm mb-sm">
            <strong style="font-size:16px">时空</strong>
            <span class="text-sm text-muted">{{ selectedSetting ? 1 : 0 }} / 1</span>
          </div>
          <div class="flex gap-sm flex-wrap">
            <span v-for="t in settings" :key="t" class="tag"
                  :class="{ selected: selectedSetting === t, disabled: selectedSetting && selectedSetting !== t }"
                  @click="selectedSetting = selectedSetting === t ? '' : t">{{ t }}</span>
          </div>
        </div>

        <div class="flex gap-sm">
          <el-button size="small" @click="clearAll">清空标签</el-button>
          <el-button type="primary" size="large" @click="saveTags">
            <el-icon><Check /></el-icon> 保存标签
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, PriceTag, Reading, Collection, Check, User, Picture, Flag, Location, Box } from '@element-plus/icons-vue'
import { scriptApi } from '@/api/script'

const route = useRoute()
const scriptId = ref(route.params.scriptId || '')

// 蛙蛙写作 4 轴标签完整清单
const genres = ['言情','现实情感','悬疑','惊悚','科幻','武侠','脑洞','太空歌剧','赛博朋克','游戏','仙侠','历史']
const plots = ['权谋','重生','穿越','系统','规则怪谈','团宠','囤物资','先婚后爱','追妻火葬场','破镜重圆',
               '校园','职场','娱乐圈','宫斗宅斗','犯罪','探险','丧尸','克苏鲁','争霸','听心声',
               '读心术','倒计时文学','日久生情','一见钟情','强取豪夺','欢喜冤家','出轨','婚姻','家庭','无系统']
const tones = ['甜宠','虐恋','爽文','沙雕','暗恋','纯爱','复仇','反转','逆袭','打脸',
               '多视角反转','励志','热血','烧脑','治愈','求生','迪化','HE','BE','先虐后甜']
const settings = ['古代','现代','未来','架空','民国','五零年代','六零年代','七零年代','八零年代','兽世']

const selectedGenre = ref('')
const selectedPlots = ref([])
const selectedTones = ref([])
const selectedSetting = ref('')
const scriptTitle = ref('加载中...')
const loading = ref(false)

// 加载剧本数据
onMounted(async () => {
  if (!scriptId.value) {
    scriptTitle.value = '未选择剧本'
    return
  }
  try {
    const res = await scriptApi.getScript(scriptId.value)
    const script = res.data
    if (script) {
      scriptTitle.value = script.title || '未命名剧本'
      selectedGenre.value = script.genre_tag || ''
      // 后端存储为 JSON 字符串或数组
      selectedPlots.value = parseTagArray(script.plot_tags)
      selectedTones.value = parseTagArray(script.tone_tags)
      selectedSetting.value = script.setting_tag || ''
    }
  } catch (e) {
    ElMessage.warning('无法加载剧本数据，使用本地编辑模式')
    scriptTitle.value = '剧本 #' + scriptId.value
  }
})

// 解析后端返回的标签（可能是 JSON 字符串、数组、或逗号分隔）
function parseTagArray(val) {
  if (!val) return []
  if (Array.isArray(val)) return val
  if (typeof val === 'string') {
    try { const parsed = JSON.parse(val); return Array.isArray(parsed) ? parsed : [] }
    catch { return val.split(',').map(s => s.trim()).filter(Boolean) }
  }
  return []
}

function toggleArray(arr, item, max) {
  const idx = arr.indexOf(item)
  if (idx >= 0) arr.splice(idx, 1)
  else if (arr.length < max) arr.push(item)
}

function clearAll() {
  selectedGenre.value = ''
  selectedPlots.value = []
  selectedTones.value = []
  selectedSetting.value = ''
}

async function saveTags() {
  loading.value = true
  try {
    await scriptApi.updateTags(scriptId.value, {
      genre: selectedGenre.value,
      plot: selectedPlots.value,
      tone: selectedTones.value,
      setting: selectedSetting.value
    })
    ElMessage.success('标签已保存！')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e?.message || '请重试'))
  } finally {
    loading.value = false
  }
}
</script>
