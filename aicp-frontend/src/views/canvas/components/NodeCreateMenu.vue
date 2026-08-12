<template>
  <div v-if="visible" class="node-create-menu" :style="{ left: x + 'px', top: y + 'px' }">
    <div class="menu-header">新建节点</div>
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
import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  x: { type: Number, default: 0 },
  y: { type: Number, default: 0 },
  nodeTypes: { type: Array, default: () => [] }
})

defineEmits(['select'])

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
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
  margin-bottom: 10px;
  padding: 0 4px;
  letter-spacing: .04em;
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
