<template>
  <aside class="card editor-nav" aria-label="作品编辑导航">
    <h3 class="nav-title">作品信息</h3>
    <div class="nav-list">
      <button v-for="item in workInfoItems" :key="item.key" type="button"
        class="nav-item" :class="{ active: activeSection === item.key }"
        @click="$emit('select', item.key)">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
        <span v-if="item.count != null" class="nav-count">{{ item.count }}</span>
      </button>
    </div>
    <div class="settings-nav">
      <h3 class="nav-title">设定</h3>
      <div class="nav-list">
        <button v-for="item in settingItems" :key="item.key" type="button"
          class="nav-item" :class="{ active: activeSection === item.key }"
          @click="$emit('select', item.key)">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <span v-if="item.count != null" class="nav-count">{{ item.count }}</span>
        </button>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { Collection, Edit, Document, User, Picture, OfficeBuilding, Location, Box } from '@element-plus/icons-vue'

const props = defineProps({
  activeSection: { type: String, default: 'tags' },
  settingCounts: { type: Object, default: () => ({}) }
})

defineEmits(['select'])

const workInfoItems = [
  { key: 'tags', label: '标签', icon: Collection, count: null },
  { key: 'synopsis', label: '简介', icon: Edit, count: null },
  { key: 'outline', label: '总纲', icon: Document, count: null }
]

const settingItems = computed(() => [
  { key: 'character', label: '角色', icon: User, count: props.settingCounts?.character ?? 0 },
  { key: 'background', label: '背景', icon: Picture, count: props.settingCounts?.background ?? 0 },
  { key: 'faction', label: '势力', icon: OfficeBuilding, count: props.settingCounts?.faction ?? 0 },
  { key: 'location', label: '地点', icon: Location, count: props.settingCounts?.location ?? 0 },
  { key: 'item', label: '物品', icon: Box, count: props.settingCounts?.item ?? 0 }
])
</script>

<style scoped>
.editor-nav { padding: 16px; }
.nav-title { margin: 0 0 10px; font-size: 13px; font-weight: 700; color: var(--text-secondary); }
.nav-list { display: grid; gap: 6px; }
.nav-item {
  width: 100%; min-height: 38px; display: flex; align-items: center; gap: 8px;
  border: 1px solid transparent; border-radius: 8px; padding: 8px 10px;
  background: transparent; color: var(--text-secondary); font: inherit; font-size: 13px;
  text-align: left; cursor: pointer; transition: all .15s;
}
.nav-item:hover { color: var(--accent); background: var(--accent-bg); }
.nav-item.active { color: var(--accent); background: var(--accent-bg); border-color: var(--accent-border); font-weight: 700; }
.nav-count { margin-left: auto; font-size: 12px; color: var(--text-tertiary); }
.settings-nav { border-top: 1px solid var(--border); margin-top: 20px; padding-top: 16px; }
</style>
