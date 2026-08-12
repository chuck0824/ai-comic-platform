<template>
  <div
    v-if="visible"
    ref="menuRef"
    class="node-create-menu"
    :style="{ left: clampedX + 'px', top: clampedY + 'px' }"
    @mousedown.stop
    @pointerdown.stop
  >
    <div class="menu-header">
      <span>新建节点</span>
      <button type="button" class="menu-close" title="关闭" @click="$emit('close')">×</button>
    </div>
    <div class="menu-groups">
      <div v-for="group in groups" :key="group.name" class="menu-group">
        <div class="group-title">{{ group.name }}</div>
        <button
          v-for="nodeType in group.items"
          :key="nodeType.type"
          type="button"
          class="menu-item"
          :style="{ '--accent': nodeType.accent }"
          @click="$emit('select', nodeType.type)"
        >
          <span class="menu-icon">
            <el-icon :size="16"><component :is="nodeType.icon" /></el-icon>
          </span>
          <span class="menu-copy">
            <strong>{{ nodeType.label }}</strong>
            <small>{{ nodeType.short || nodeType.desc }}</small>
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick, onBeforeUnmount } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  x: { type: Number, default: 0 },
  y: { type: Number, default: 0 },
  nodeTypes: { type: Array, default: () => [] }
})

const emit = defineEmits(['select', 'close'])

const menuRef = ref(null)
const menuSize = ref({ width: 320, height: 420 })

const groups = computed(() => {
  const list = []
  props.nodeTypes.forEach((item) => {
    const name = item.group || '其他'
    let group = list.find(g => g.name === name)
    if (!group) {
      group = { name, items: [] }
      list.push(group)
    }
    group.items.push(item)
  })
  return list
})

const clampedX = computed(() => {
  const max = Math.max(8, window.innerWidth - menuSize.value.width - 8)
  return Math.min(Math.max(8, props.x), max)
})

const clampedY = computed(() => {
  const max = Math.max(8, window.innerHeight - menuSize.value.height - 8)
  return Math.min(Math.max(8, props.y), max)
})

function measureMenu() {
  const el = menuRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  menuSize.value = {
    width: Math.ceil(rect.width) || 320,
    height: Math.ceil(rect.height) || 420,
  }
}

function onDocPointerDown(e) {
  if (!props.visible) return
  const el = menuRef.value
  if (el && el.contains(e.target)) return
  if (e.target?.closest?.('.floating-add')) return
  emit('close')
}

function onDocKeydown(e) {
  if (e.key === 'Escape' && props.visible) {
    e.preventDefault()
    emit('close')
  }
}

watch(() => props.visible, async (visible) => {
  document.removeEventListener('pointerdown', onDocPointerDown, true)
  document.removeEventListener('keydown', onDocKeydown, true)
  if (!visible) return
  await nextTick()
  measureMenu()
  // 延后绑定，避免打开菜单的同一次点击立刻关掉
  requestAnimationFrame(() => {
    document.addEventListener('pointerdown', onDocPointerDown, true)
    document.addEventListener('keydown', onDocKeydown, true)
  })
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointerDown, true)
  document.removeEventListener('keydown', onDocKeydown, true)
})
</script>

<style scoped>
.node-create-menu {
  position: fixed;
  z-index: 1000;
  background: #151925;
  border: 1px solid #2f374c;
  border-radius: 14px;
  padding: 12px;
  width: min(320px, calc(100vw - 24px));
  max-height: min(520px, calc(100vh - 24px));
  overflow: auto;
  box-shadow: 0 18px 48px rgba(0, 0, 0, .55);
  scrollbar-width: thin;
}
.menu-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
  margin-bottom: 10px;
  padding: 0 2px 0 4px;
  letter-spacing: .04em;
}
.menu-close {
  appearance: none;
  border: 0;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  color: #94a3b8;
  background: transparent;
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
}
.menu-close:hover {
  color: #e2e8f0;
  background: rgba(148, 163, 184, .12);
}
.menu-group + .menu-group {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(47, 55, 76, .9);
}
.group-title {
  font-size: 10px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: .08em;
  text-transform: uppercase;
  margin: 0 0 6px 4px;
}
.menu-item {
  width: 100%;
  appearance: none;
  border: 1px solid transparent;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  text-align: left;
  color: inherit;
  transition: background .15s, border-color .15s;
}
.menu-item:hover {
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  border-color: color-mix(in srgb, var(--accent) 35%, transparent);
}
.menu-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 16%, transparent);
}
.menu-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.menu-copy strong {
  font-size: 13px;
  font-weight: 650;
  color: #e2e8f0;
}
.menu-copy small {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
