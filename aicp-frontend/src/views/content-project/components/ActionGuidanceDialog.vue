<template>
  <el-dialog :model-value="visible" :title="guidance?.title || '操作提示'" width="420px" @close="emit('close')">
    <p class="guidance-message">{{ guidance?.message || '当前操作暂不可执行。' }}</p>
    <template #footer>
      <el-button @click="emit('close')">知道了</el-button>
      <el-button v-if="guidance?.targetAction" type="primary" @click="emit('target', guidance.targetAction)">
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
.guidance-message { color: var(--text-secondary); line-height: 1.7; margin: 0; }
</style>
