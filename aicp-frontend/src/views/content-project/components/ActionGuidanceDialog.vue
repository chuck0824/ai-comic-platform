<template>
  <el-dialog :model-value="visible" :title="guidance?.title || '操作提示'" width="460px" @close="emit('close')">
    <p class="guidance-message">{{ guidance?.message || '当前操作暂不可执行。' }}</p>
    <ul v-if="guidance?.items?.length" class="guidance-items">
      <li v-for="(item, index) in guidance.items" :key="item.code || index">
        <strong v-if="item.code">[{{ item.code }}]</strong>
        {{ item.message || item.title || '' }}
      </li>
    </ul>
    <template #footer>
      <el-button @click="emit('close')">知道了</el-button>
      <template v-if="guidance?.actions?.length">
        <el-button
          v-for="action in guidance.actions"
          :key="action.code"
          :type="action.primary ? 'primary' : 'default'"
          @click="emit('target', action.code)"
        >
          {{ action.label }}
        </el-button>
      </template>
      <el-button
        v-else-if="guidance?.targetAction"
        type="primary"
        @click="emit('target', guidance.targetAction)"
      >
        前往处理
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  guidance: { type: Object, default: null }
})

const emit = defineEmits(['close', 'target'])
</script>

<style scoped>
.guidance-message { color: var(--text-secondary); line-height: 1.7; margin: 0; white-space: pre-wrap; }
.guidance-items {
  margin: 12px 0 0;
  padding-left: 1.2em;
  color: var(--text-secondary);
  line-height: 1.6;
}
.guidance-items strong { margin-right: 4px; color: var(--text-primary); }
</style>
