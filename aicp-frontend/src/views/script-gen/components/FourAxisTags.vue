<!-- 4轴标签选择器：题材(单选) / 情节(多选max3) / 情绪(多选max3) / 时空(单选)
     复用于 ScriptGen.vue 和 TagEditor.vue -->
<template>
  <div class="four-axis-tags">
    <!-- 题材 Genre：单选 1/1 -->
    <div class="axis-group">
      <div class="axis-header">
        <strong>题材</strong>
        <span class="axis-counter text-muted">{{ selected.genre ? '1/1' : '0/1' }}</span>
      </div>
      <div class="tag-grid">
        <span v-for="t in axes.genre" :key="t"
              :class="['tag', { selected: selected.genre === t }]"
              @click="selected.genre = selected.genre === t ? '' : t">{{ t }}</span>
      </div>
    </div>

    <!-- 情节 Plot：多选，最多 3 个 -->
    <div class="axis-group">
      <div class="axis-header">
        <strong>情节</strong>
        <span class="axis-counter text-muted">{{ (selected.plots || []).length }} / 3</span>
      </div>
      <div class="tag-grid">
        <span v-for="t in axes.plot" :key="t"
              :class="['tag', { selected: (selected.plots || []).includes(t),
                                disabled: (selected.plots || []).length >= 3 && !(selected.plots || []).includes(t) }]"
              @click="toggleArray('plots', t, 3)">{{ t }}</span>
      </div>
    </div>

    <!-- 情绪 Tone：多选，最多 3 个 -->
    <div class="axis-group">
      <div class="axis-header">
        <strong>情绪/基调</strong>
        <span class="axis-counter text-muted">{{ (selected.tones || []).length }} / 3</span>
      </div>
      <div class="tag-grid">
        <span v-for="t in axes.tone" :key="t"
              :class="['tag', { selected: (selected.tones || []).includes(t),
                                disabled: (selected.tones || []).length >= 3 && !(selected.tones || []).includes(t) }]"
              @click="toggleArray('tones', t, 3)">{{ t }}</span>
      </div>
    </div>

    <!-- 时空 Setting：单选 1/1 -->
    <div class="axis-group">
      <div class="axis-header">
        <strong>时空背景</strong>
        <span class="axis-counter text-muted">{{ selected.setting ? '1/1' : '0/1' }}</span>
      </div>
      <div class="tag-grid">
        <span v-for="t in axes.setting" :key="t"
              :class="['tag', { selected: selected.setting === t }]"
              @click="selected.setting = selected.setting === t ? '' : t">{{ t }}</span>
      </div>
    </div>

    <el-button size="small" text class="mt-sm" @click="clearAll">清空所有标签</el-button>
  </div>
</template>

<script setup>
import { reactive } from 'vue'

// 蛙蛙写作 4 轴标签体系
const axes = {
  genre: ['言情', '现实情感', '悬疑', '惊悚', '科幻', '武侠', '脑洞', '太空歌剧', '赛博朋克', '游戏', '仙侠', '历史'],
  plot: ['权谋', '重生', '穿越', '系统', '规则怪谈', '团宠', '囤物资', '先婚后爱', '追妻火葬场', '破镜重圆',
         '校园', '职场', '娱乐圈', '宫斗宅斗', '犯罪', '探险', '丧尸', '克苏鲁', '争霸', '听心声',
         '读心术', '倒计时文学', '日久生情', '一见钟情', '强取豪夺', '欢喜冤家', '出轨', '婚姻', '家庭', '无系统'],
  tone: ['甜宠', '虐恋', '爽文', '沙雕', '暗恋', '纯爱', '复仇', '反转', '逆袭', '打脸',
         '多视角反转', '励志', '热血', '烧脑', '治愈', '求生', '迪化', 'HE', 'BE', '先虐后甜'],
  setting: ['古代', '现代', '未来', '架空', '民国', '五零年代', '六零年代', '七零年代', '八零年代', '兽世']
}

// v-model 双向绑定
const selected = defineModel({
  type: Object,
  default: () => ({
    genre: '',
    plots: [],
    tones: [],
    setting: ''
  })
})

// 多选标签：最多 max 个
function toggleArray(key, item, max) {
  const arr = selected[key] || []
  const idx = arr.indexOf(item)
  if (idx >= 0) {
    arr.splice(idx, 1)
  } else if (arr.length < max) {
    arr.push(item)
  }
}

// 清空所有标签
function clearAll() {
  selected.genre = ''
  selected.plots = []
  selected.tones = []
  selected.setting = ''
}
</script>

<style scoped>
.four-axis-tags { font-size:12px; }
.axis-group { margin-bottom:14px; }
.axis-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:6px; }
.axis-header strong { font-size:13px; }
.axis-counter { font-size:11px; }
.tag-grid { display:flex; gap:6px; flex-wrap:wrap; }
.tag { display:inline-block; padding:4px 10px; border-radius:6px; font-size:11px;
  background:var(--bg-surface); color:var(--text-secondary); border:1px solid var(--border);
  cursor:pointer; transition:all .15s; user-select:none; }
.tag:hover { border-color:var(--accent); color:var(--accent); background:var(--accent-bg); }
.tag.selected { background:var(--accent-bg); border-color:var(--accent); color:var(--accent); font-weight:600; }
.tag.disabled { opacity:.35; cursor:not-allowed; }
</style>
