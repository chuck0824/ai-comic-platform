<template>
  <div class="module-tabs" data-testid="module-tabs">
    <el-tabs :model-value="activeModule" @tab-click="(tab) => $emit('select', tab.props.name)">
      <el-tab-pane label="镜头编辑" name="shots" />
      <el-tab-pane label="情绪节奏" name="emotion" />
      <el-tab-pane label="提示词模板" name="prompts" />
      <el-tab-pane label="类型修订" name="rules" />
      <el-tab-pane label="人物视觉规范" name="visuals" />
      <el-tab-pane label="版本与审核" name="review" />
    </el-tabs>
  </div>
</template>

<script setup>
defineProps({ activeModule: String, isLocked: Boolean })
defineEmits(['select'])
</script>

<style scoped>
.module-tabs {
  padding: 0 16px;
  background: var(--el-bg-color, #fff);
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
}
.module-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
</style>
