<template>
  <div>
    <div class="content-header">
      <div>
        <h2 class="text-xl font-bold">4轴标签</h2>
        <p class="text-sm text-muted mt-sm">标签修改后自动保存（800ms 防抖）。</p>
      </div>
      <span class="save-state" :class="statusClass">
        <el-icon v-if="saveStatus === 'saving'" class="is-loading"><Loading /></el-icon>
        <el-icon v-else-if="saveStatus === 'error'"><WarningFilled /></el-icon>
        <el-icon v-else><CircleCheck /></el-icon>
        {{ statusText }}
      </span>
    </div>

    <!-- 题材 -->
    <section class="axis-group">
      <div class="axis-header">
        <strong>题材</strong><span class="axis-help">单选</span>
        <span class="axis-counter">{{ genre ? 1 : 0 }} / 1</span>
      </div>
      <div class="tag-grid">
        <button v-for="tag in genreOptions" :key="tag.value" type="button"
          class="tag" :class="{ selected: genre === tag.value }"
          @click="onGenre(tag.value)">{{ tag.label }}</button>
      </div>
    </section>

    <!-- 情节 -->
    <section class="axis-group">
      <div class="axis-header">
        <strong>情节</strong><span class="axis-help">多选，最多 3 个</span>
        <span class="axis-counter">{{ plots.length }} / 3</span>
      </div>
      <div class="tag-grid">
        <button v-for="tag in plotOptions" :key="tag.value" type="button"
          class="tag" :class="{ selected: plots.includes(tag.value) }"
          @click="onPlot(tag.value)">{{ tag.label }}</button>
      </div>
    </section>

    <!-- 情绪 -->
    <section class="axis-group">
      <div class="axis-header">
        <strong>情绪</strong><span class="axis-help">多选，最多 3 个</span>
        <span class="axis-counter">{{ tones.length }} / 3</span>
      </div>
      <div class="tag-grid">
        <button v-for="tag in toneOptions" :key="tag.value" type="button"
          class="tag" :class="{ selected: tones.includes(tag.value) }"
          @click="onTone(tag.value)">{{ tag.label }}</button>
      </div>
    </section>

    <!-- 时空 -->
    <section class="axis-group">
      <div class="axis-header">
        <strong>时空</strong><span class="axis-help">单选</span>
        <span class="axis-counter">{{ setting ? 1 : 0 }} / 1</span>
      </div>
      <div class="tag-grid">
        <button v-for="tag in settingOptions" :key="tag.value" type="button"
          class="tag" :class="{ selected: setting === tag.value }"
          @click="onSetting(tag.value)">{{ tag.label }}</button>
      </div>
    </section>

    <div class="content-actions align-end">
      <el-button size="small" :disabled="!hasSelection" @click="$emit('clear')">清空全部</el-button>
      <el-button size="small" type="primary" :loading="saveStatus === 'saving'" @click="$emit('save')">手动保存</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Loading, WarningFilled, CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({
  genre: String,
  plots: { type: Array, default: () => [] },
  tones: { type: Array, default: () => [] },
  setting: String,
  dictionary: Object,
  saveStatus: { type: String, default: 'idle' }
})

const emit = defineEmits(['update:genre', 'update:plots', 'update:tones', 'update:setting', 'clear', 'save'])

const genreOptions = computed(() => props.dictionary?.genres ?? [])
const plotOptions = computed(() => props.dictionary?.plots ?? [])
const toneOptions = computed(() => props.dictionary?.tones ?? [])
const settingOptions = computed(() => props.dictionary?.settings ?? [])

const hasSelection = computed(() => props.genre || props.plots.length || props.tones.length || props.setting)

const statusClass = computed(() => {
  if (props.saveStatus === 'saving') return 'saving'
  if (props.saveStatus === 'error') return 'error'
  if (props.saveStatus === 'conflict') return 'error'
  return ''
})

const statusText = computed(() => {
  switch (props.saveStatus) {
    case 'saving': return '保存中…'
    case 'saved': return '已保存'
    case 'error': return '保存失败'
    case 'conflict': return '版本冲突'
    default: return '就绪'
  }
})

function onGenre(v) { emit('update:genre', props.genre === v ? '' : v) }
function onPlot(v) {
  const arr = [...props.plots]
  const i = arr.indexOf(v)
  i >= 0 ? arr.splice(i, 1) : arr.length < 3 && arr.push(v)
  emit('update:plots', arr)
}
function onTone(v) {
  const arr = [...props.tones]
  const i = arr.indexOf(v)
  i >= 0 ? arr.splice(i, 1) : arr.length < 3 && arr.push(v)
  emit('update:tones', arr)
}
function onSetting(v) { emit('update:setting', props.setting === v ? '' : v) }
</script>

<style scoped>
.content-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
.save-state { display: inline-flex; align-items: center; gap: 5px; border-radius: 999px; padding: 5px 10px; font-size: 12px; background: var(--success-bg); color: var(--success); }
.save-state.saving, .save-state.dirty { background: var(--warning-bg); color: var(--warning); }
.save-state.error { background: var(--danger-bg); color: var(--danger); }
.axis-group { margin-bottom: 26px; }
.axis-header { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.axis-header strong { font-size: 15px; }
.axis-help { color: var(--text-tertiary); font-size: 12px; }
.axis-counter { margin-left: auto; color: var(--text-secondary); font-size: 12px; }
.tag-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.content-actions { display: flex; align-items: center; gap: 10px; }
.align-end { justify-content: flex-end; margin-top: 20px; }
</style>
