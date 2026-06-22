<template>
  <div v-if="visible" class="node-create-menu" :style="{ left: x + 'px', top: y + 'px' }">
    <div class="menu-header">新建节点</div>
    <div class="menu-grid">
      <div v-for="nodeType in nodeTypes" :key="nodeType.type"
           class="menu-item" @click="$emit('select', nodeType.type)">
        <span class="menu-icon">{{ nodeType.icon }}</span>
        <div>
          <div class="menu-label">{{ nodeType.label }}</div>
          <div class="menu-desc">{{ nodeType.desc }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  x: { type: Number, default: 0 },
  y: { type: Number, default: 0 },
  nodeTypes: { type: Array, default: () => [] }
})
defineEmits(['select'])
</script>

<style scoped>
.node-create-menu {
  position: fixed;
  z-index: 1000;
  background: #1a1a2e;
  border: 1px solid #333;
  border-radius: 12px;
  padding: 12px;
  min-width: 280px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.5);
}
.menu-header {
  font-size: 13px;
  font-weight: 600;
  color: #888;
  margin-bottom: 10px;
  padding: 0 4px;
}
.menu-grid {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 400px;
  overflow-y: auto;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}
.menu-item:hover { background: rgba(79, 70, 229, 0.2); }
.menu-icon { font-size: 22px; width: 32px; text-align: center; }
.menu-label { font-size: 13px; font-weight: 600; color: #e0e0e0; }
.menu-desc { font-size: 11px; color: #888; }
</style>
