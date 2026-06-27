<template>
  <div class="tag-editor-page">
    <div class="page-header">
      <el-button size="small" @click="$router.push('/warehouse')">
        <el-icon><ArrowLeft /></el-icon>
        返回仓库
      </el-button>
      <div class="page-heading">
        <h2 class="text-xl font-bold">作品编辑 — {{ scriptTitle }}</h2>
        <span class="text-sm text-muted">
          剧本 · {{ headerSaveText }} · 总字数 {{ formatNumber(totalWords) }}
        </span>
      </div>
    </div>

    <div class="editor-layout">
      <aside class="card editor-nav" aria-label="作品编辑导航">
        <h3 class="nav-title">作品信息</h3>
        <div class="nav-list">
          <button
            v-for="item in workInfoItems"
            :key="item.key"
            type="button"
            class="nav-item"
            :class="{ active: activeSection === item.key }"
            :disabled="item.disabled"
            :title="item.disabled ? '该模块尚未接入数据服务' : ''"
            @click="selectSection(item)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
            <span v-if="item.disabled" class="coming-soon">待接入</span>
          </button>
        </div>

        <div class="settings-nav">
          <h3 class="nav-title">设定</h3>
          <div class="nav-list">
            <button
              v-for="item in settingItems"
              :key="item.key"
              type="button"
              class="nav-item"
              disabled
              title="该模块尚未接入数据服务"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
              <span class="coming-soon">待接入</span>
            </button>
          </div>
        </div>
      </aside>

      <main class="card editor-content" v-loading="pageLoading">
        <el-alert
          v-if="loadError"
          class="mb-lg"
          type="warning"
          :title="loadError"
          :closable="false"
          show-icon
        />

        <template v-if="activeSection === 'tags'">
          <div class="content-header">
            <div>
              <h2 class="text-xl font-bold">4轴标签</h2>
              <p class="text-sm text-muted mt-sm">标签修改后会自动保存，也可以手动保存。</p>
            </div>
            <span class="save-state" :class="tagSaveStateClass">
              <el-icon v-if="tagSaveState === 'saving'" class="is-loading"><Loading /></el-icon>
              <el-icon v-else-if="tagSaveState === 'error'"><WarningFilled /></el-icon>
              <el-icon v-else><CircleCheck /></el-icon>
              {{ tagSaveText }}
            </span>
          </div>

          <section class="axis-group" aria-labelledby="genre-title">
            <div class="axis-header">
              <div>
                <strong id="genre-title">题材</strong>
                <span class="axis-help">单选，可直接切换</span>
              </div>
              <span class="axis-counter">{{ selectedGenre ? 1 : 0 }} / 1</span>
            </div>
            <div class="tag-grid">
              <button
                v-for="tag in genreOptions"
                :key="tag"
                type="button"
                class="tag"
                :class="{ selected: selectedGenre === tag }"
                :aria-pressed="selectedGenre === tag"
                @click="toggleSingle('genre', tag)"
              >{{ tag }}</button>
            </div>
          </section>

          <section class="axis-group" aria-labelledby="plot-title">
            <div class="axis-header">
              <div>
                <strong id="plot-title">情节</strong>
                <span class="axis-help">最多选择 3 个</span>
              </div>
              <span class="axis-counter">{{ selectedPlots.length }} / 3</span>
            </div>
            <div class="tag-grid">
              <button
                v-for="tag in plotOptions"
                :key="tag"
                type="button"
                class="tag"
                :class="{ selected: selectedPlots.includes(tag) }"
                :disabled="selectedPlots.length >= 3 && !selectedPlots.includes(tag)"
                :aria-pressed="selectedPlots.includes(tag)"
                @click="toggleArray(selectedPlots, tag, 3)"
              >{{ tag }}</button>
            </div>
          </section>

          <section class="axis-group" aria-labelledby="tone-title">
            <div class="axis-header">
              <div>
                <strong id="tone-title">情绪 / 基调</strong>
                <span class="axis-help">最多选择 3 个</span>
              </div>
              <span class="axis-counter">{{ selectedTones.length }} / 3</span>
            </div>
            <div class="tag-grid">
              <button
                v-for="tag in toneOptions"
                :key="tag"
                type="button"
                class="tag"
                :class="{ selected: selectedTones.includes(tag) }"
                :disabled="selectedTones.length >= 3 && !selectedTones.includes(tag)"
                :aria-pressed="selectedTones.includes(tag)"
                @click="toggleArray(selectedTones, tag, 3)"
              >{{ tag }}</button>
            </div>
          </section>

          <section class="axis-group" aria-labelledby="setting-title">
            <div class="axis-header">
              <div>
                <strong id="setting-title">时空背景</strong>
                <span class="axis-help">单选，可直接切换</span>
              </div>
              <span class="axis-counter">{{ selectedSetting ? 1 : 0 }} / 1</span>
            </div>
            <div class="tag-grid">
              <button
                v-for="tag in settingOptions"
                :key="tag"
                type="button"
                class="tag"
                :class="{ selected: selectedSetting === tag }"
                :aria-pressed="selectedSetting === tag"
                @click="toggleSingle('setting', tag)"
              >{{ tag }}</button>
            </div>
          </section>

          <div class="content-actions">
            <el-button :disabled="!hasSelectedTags || pageLoading" @click="confirmClearTags">
              清空标签
            </el-button>
            <el-button
              type="primary"
              size="large"
              :loading="tagSaveState === 'saving'"
              :disabled="!isTagDirty || pageLoading || !scriptId"
              @click="saveTags({ notify: true })"
            >
              <el-icon><Check /></el-icon>
              保存标签
            </el-button>
          </div>
        </template>

        <template v-else-if="activeSection === 'synopsis'">
          <div class="content-header">
            <div>
              <h2 class="text-xl font-bold">作品简介</h2>
              <p class="text-sm text-muted mt-sm">用于仓库展示、市场上架和 AI 创作上下文。</p>
            </div>
            <span class="save-state" :class="{ dirty: isSynopsisDirty }">
              <el-icon><CircleCheck /></el-icon>
              {{ isSynopsisDirty ? '有未保存修改' : '已保存' }}
            </span>
          </div>
          <el-input
            v-model="synopsis"
            type="textarea"
            :rows="12"
            maxlength="2000"
            show-word-limit
            placeholder="填写故事的核心人物、冲突、目标和主要看点…"
          />
          <div class="content-actions align-end">
            <el-button
              type="primary"
              size="large"
              :loading="synopsisSaving"
              :disabled="!isSynopsisDirty || pageLoading || !scriptId"
              @click="saveSynopsis"
            >
              <el-icon><Check /></el-icon>
              保存简介
            </el-button>
          </div>
        </template>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Box,
  Check,
  CircleCheck,
  Collection,
  Flag,
  Loading,
  Location,
  Picture,
  PriceTag,
  Reading,
  User,
  WarningFilled
} from '@element-plus/icons-vue'
import { scriptApi } from '@/api/script'

const route = useRoute()

const genres = ['言情', '现实情感', '悬疑', '惊悚', '科幻', '武侠', '脑洞', '太空歌剧', '赛博朋克', '游戏', '仙侠', '历史']
const plots = ['权谋', '重生', '穿越', '系统', '规则怪谈', '团宠', '囤物资', '先婚后爱', '追妻火葬场', '破镜重圆',
  '校园', '职场', '娱乐圈', '宫斗宅斗', '犯罪', '探险', '丧尸', '克苏鲁', '争霸', '听心声',
  '读心术', '倒计时文学', '日久生情', '一见钟情', '强取豪夺', '欢喜冤家', '出轨', '婚姻', '家庭', '无系统']
const tones = ['甜宠', '虐恋', '爽文', '沙雕', '暗恋', '纯爱', '复仇', '反转', '逆袭', '打脸',
  '多视角反转', '励志', '热血', '烧脑', '治愈', '求生', '迪化', 'HE', 'BE', '先虐后甜']
const settings = ['古代', '现代', '未来', '架空', '民国', '五零年代', '六零年代', '七零年代', '八零年代', '兽世']

const workInfoItems = [
  { key: 'tags', label: '标签', icon: markRaw(PriceTag) },
  { key: 'synopsis', label: '简介', icon: markRaw(Reading) },
  { key: 'outline', label: '总纲', icon: markRaw(Collection), disabled: true }
]
const settingItems = [
  { key: 'characters', label: '角色', icon: markRaw(User) },
  { key: 'background', label: '背景', icon: markRaw(Picture) },
  { key: 'factions', label: '势力', icon: markRaw(Flag) },
  { key: 'locations', label: '地点', icon: markRaw(Location) },
  { key: 'items', label: '物品', icon: markRaw(Box) }
]

const scriptId = ref('')
const scriptTitle = ref('加载中…')
const totalWords = ref(0)
const activeSection = ref('tags')
const pageLoading = ref(false)
const loadError = ref('')
const hydrated = ref(false)

const selectedGenre = ref('')
const selectedPlots = ref([])
const selectedTones = ref([])
const selectedSetting = ref('')
const savedTagsSignature = ref('')
const tagSaveState = ref('idle')
const tagSavedAt = ref(null)

const synopsis = ref('')
const savedSynopsis = ref('')
const synopsisSaving = ref(false)

let autoSaveTimer = null
let tagSavePromise = null
let tagSaveQueued = false
let notifyAfterTagSave = false

const genreOptions = computed(() => appendLegacyOptions(genres, selectedGenre.value))
const plotOptions = computed(() => appendLegacyOptions(plots, selectedPlots.value))
const toneOptions = computed(() => appendLegacyOptions(tones, selectedTones.value))
const settingOptions = computed(() => appendLegacyOptions(settings, selectedSetting.value))

const currentTagsSignature = computed(() => JSON.stringify({
  genre: selectedGenre.value,
  plot: selectedPlots.value,
  tone: selectedTones.value,
  setting: selectedSetting.value
}))
const isTagDirty = computed(() => currentTagsSignature.value !== savedTagsSignature.value)
const hasSelectedTags = computed(() => Boolean(
  selectedGenre.value || selectedPlots.value.length || selectedTones.value.length || selectedSetting.value
))
const isSynopsisDirty = computed(() => synopsis.value !== savedSynopsis.value)

const tagSaveText = computed(() => {
  if (tagSaveState.value === 'saving') return '保存中…'
  if (tagSaveState.value === 'error') return '保存失败，请重试'
  if (isTagDirty.value) return '等待自动保存'
  if (tagSavedAt.value) return `已保存 ${formatTime(tagSavedAt.value)}`
  return '已保存'
})
const tagSaveStateClass = computed(() => ({
  saving: tagSaveState.value === 'saving',
  error: tagSaveState.value === 'error',
  dirty: isTagDirty.value
}))
const headerSaveText = computed(() => activeSection.value === 'tags'
  ? tagSaveText.value
  : (isSynopsisDirty.value ? '简介未保存' : '已保存'))

watch(
  () => route.params.scriptId,
  (id) => loadScript(id),
  { immediate: true }
)

watch(currentTagsSignature, () => {
  if (!hydrated.value || !isTagDirty.value) return
  tagSaveState.value = 'dirty'
  clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => saveTags().catch(() => {}), 800)
})

onBeforeUnmount(() => clearTimeout(autoSaveTimer))

onBeforeRouteLeave(async () => {
  clearTimeout(autoSaveTimer)
  if (scriptId.value && isTagDirty.value) {
    await saveTags()
    if (isTagDirty.value) return false
  }

  if (isSynopsisDirty.value) {
    try {
      await ElMessageBox.confirm(
        '作品简介还有未保存的修改，离开后这些修改会丢失。',
        '确认离开？',
        { confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning' }
      )
    } catch {
      return false
    }
  }
  return true
})

async function loadScript(id) {
  clearTimeout(autoSaveTimer)
  hydrated.value = false
  pageLoading.value = true
  loadError.value = ''
  tagSaveState.value = 'idle'
  tagSavedAt.value = null
  scriptId.value = id ? String(id) : ''
  resetForm()

  if (!scriptId.value) {
    scriptTitle.value = '未选择剧本'
    loadError.value = '缺少剧本 ID，请从剧本仓库进入标签编辑。'
    pageLoading.value = false
    hydrated.value = true
    return
  }

  try {
    const res = await scriptApi.getScript(scriptId.value)
    const script = res.data
    if (!script) throw new Error('剧本不存在')

    scriptTitle.value = script.title || '未命名剧本'
    totalWords.value = Number(readField(script, 'total_words', 'totalWords') || 0)
    selectedGenre.value = String(readField(script, 'genre_tag', 'genreTag') || '')
    selectedPlots.value = parseTagArray(readField(script, 'plot_tags', 'plotTags'), 3)
    selectedTones.value = parseTagArray(readField(script, 'tone_tags', 'toneTags'), 3)
    selectedSetting.value = String(readField(script, 'setting_tag', 'settingTag') || '')
    synopsis.value = String(script.synopsis || '')
    savedSynopsis.value = synopsis.value
    savedTagsSignature.value = currentTagsSignature.value
  } catch (error) {
    scriptTitle.value = `剧本 #${scriptId.value}`
    loadError.value = `无法加载剧本数据：${error?.message || '请稍后重试'}`
    savedTagsSignature.value = currentTagsSignature.value
  } finally {
    pageLoading.value = false
    hydrated.value = true
  }
}

function resetForm() {
  scriptTitle.value = '加载中…'
  totalWords.value = 0
  selectedGenre.value = ''
  selectedPlots.value = []
  selectedTones.value = []
  selectedSetting.value = ''
  synopsis.value = ''
  savedSynopsis.value = ''
  savedTagsSignature.value = ''
}

function readField(source, snakeKey, camelKey) {
  return source?.[snakeKey] ?? source?.[camelKey]
}

function parseTagArray(value, max) {
  if (!value) return []
  let parsed = value
  if (typeof value === 'string') {
    try { parsed = JSON.parse(value) }
    catch { parsed = value.split(',') }
  }
  if (!Array.isArray(parsed)) return []
  return [...new Set(parsed.map(item => String(item).trim()).filter(Boolean))].slice(0, max)
}

function appendLegacyOptions(options, selected) {
  const values = Array.isArray(selected) ? selected : [selected]
  return [...new Set([...options, ...values.filter(Boolean)])]
}

function selectSection(item) {
  if (!item.disabled) activeSection.value = item.key
}

function toggleSingle(axis, tag) {
  if (axis === 'genre') selectedGenre.value = selectedGenre.value === tag ? '' : tag
  if (axis === 'setting') selectedSetting.value = selectedSetting.value === tag ? '' : tag
}

function toggleArray(target, item, max) {
  const index = target.indexOf(item)
  if (index >= 0) target.splice(index, 1)
  else if (target.length < max) target.push(item)
}

async function confirmClearTags() {
  if (!hasSelectedTags.value) return
  try {
    await ElMessageBox.confirm(
      '清空后，当前作品的全部 4 轴标签都会被移除并自动保存。',
      '确认清空标签？',
      { confirmButtonText: '确认清空', cancelButtonText: '取消', type: 'warning' }
    )
    selectedGenre.value = ''
    selectedPlots.value = []
    selectedTones.value = []
    selectedSetting.value = ''
  } catch {
    // 用户取消，不改变当前标签。
  }
}

async function saveTags({ notify = false } = {}) {
  clearTimeout(autoSaveTimer)
  if (!scriptId.value || !isTagDirty.value) return

  tagSaveQueued = true
  notifyAfterTagSave = notifyAfterTagSave || notify
  if (tagSavePromise) return tagSavePromise

  tagSavePromise = (async () => {
    while (tagSaveQueued) {
      tagSaveQueued = false
      const signature = currentTagsSignature.value
      const payload = JSON.parse(signature)
      tagSaveState.value = 'saving'
      try {
        await scriptApi.updateTags(scriptId.value, payload)
        savedTagsSignature.value = signature
        tagSavedAt.value = new Date()
        tagSaveState.value = 'saved'
      } catch (error) {
        tagSaveState.value = 'error'
        tagSaveQueued = false
        throw error
      }

      if (currentTagsSignature.value !== savedTagsSignature.value) tagSaveQueued = true
    }
  })()

  try {
    await tagSavePromise
    if (notifyAfterTagSave) ElMessage.success('标签已保存')
  } catch {
    // 请求层已经展示错误信息；页面保留失败状态供用户重试。
  } finally {
    tagSavePromise = null
    notifyAfterTagSave = false
  }
}

async function saveSynopsis() {
  if (!scriptId.value || !isSynopsisDirty.value) return
  synopsisSaving.value = true
  try {
    await scriptApi.updateScript(scriptId.value, { synopsis: synopsis.value })
    savedSynopsis.value = synopsis.value
    ElMessage.success('简介已保存')
  } finally {
    synopsisSaving.value = false
  }
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatTime(date) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(date)
}
</script>

<style scoped>
.tag-editor-page { min-width: 0; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
.page-heading { display: flex; align-items: baseline; gap: 12px; min-width: 0; }
.page-heading h2 { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.editor-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 24px; align-items: start; }
.editor-nav { padding: 16px; }
.nav-title { margin: 0 0 10px; font-size: 13px; font-weight: 700; color: var(--text-secondary); }
.nav-list { display: grid; gap: 6px; }
.nav-item {
  width: 100%; min-height: 38px; display: flex; align-items: center; gap: 8px;
  border: 1px solid transparent; border-radius: 8px; padding: 8px 10px;
  background: transparent; color: var(--text-secondary); font: inherit; font-size: 13px;
  text-align: left; cursor: pointer; transition: all .15s;
}
.nav-item:not(:disabled):hover { color: var(--accent); background: var(--accent-bg); }
.nav-item.active { color: var(--accent); background: var(--accent-bg); border-color: var(--accent-border); font-weight: 700; }
.nav-item:disabled { cursor: not-allowed; opacity: .56; }
.coming-soon { margin-left: auto; font-size: 10px; color: var(--text-tertiary); }
.settings-nav { border-top: 1px solid var(--border); margin-top: 20px; padding-top: 16px; }
.editor-content { min-height: 580px; padding: 24px; }
.content-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
.save-state {
  display: inline-flex; align-items: center; gap: 5px; flex: 0 0 auto;
  border-radius: 999px; padding: 5px 10px; font-size: 12px;
  background: var(--success-bg); color: var(--success);
}
.save-state.dirty, .save-state.saving { background: var(--warning-bg); color: var(--warning); }
.save-state.error { background: var(--danger-bg); color: var(--danger); }
.axis-group { margin-bottom: 26px; }
.axis-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.axis-header strong { font-size: 15px; }
.axis-help { margin-left: 8px; color: var(--text-tertiary); font-size: 12px; font-weight: 400; }
.axis-counter { color: var(--text-secondary); font-size: 12px; font-variant-numeric: tabular-nums; }
.tag-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.tag { font-family: inherit; line-height: 1; }
.tag:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.tag:disabled { opacity: .35; cursor: not-allowed; }
.tag:disabled:hover { border-color: var(--border); background: var(--bg-surface); color: var(--text-secondary); }
.content-actions { display: flex; align-items: center; gap: 10px; padding-top: 4px; }
.content-actions.align-end { justify-content: flex-end; margin-top: 20px; }

@media (max-width: 860px) {
  .page-heading { display: block; }
  .editor-layout { grid-template-columns: 1fr; }
  .editor-nav { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
  .settings-nav { border-top: 0; border-left: 1px solid var(--border); margin: 0; padding: 0 0 0 16px; }
}

@media (max-width: 560px) {
  .page-header, .content-header { align-items: flex-start; }
  .page-heading { min-width: 0; }
  .page-heading .text-muted { display: block; margin-top: 4px; }
  .editor-nav { grid-template-columns: 1fr; }
  .settings-nav { border-left: 0; border-top: 1px solid var(--border); padding: 16px 0 0; }
  .editor-content { padding: 18px; }
  .axis-help { display: block; margin: 3px 0 0; }
}
</style>
