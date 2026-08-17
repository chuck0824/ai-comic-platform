<template>
  <el-config-provider :locale="zhCn">
    <div v-if="isDev" class="dev-env-banner" role="status">
      开发环境 · AI/存储可能走 mock · 勿当作生产结果
    </div>
    <router-view />
  </el-config-provider>
</template>

<script setup>
import zhCn from 'element-plus/es/locale/lang/zh-cn'

const isDev = import.meta.env.DEV
</script>

<style>
.dev-env-banner {
  position: sticky;
  top: 0;
  z-index: 4000;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: .02em;
  color: #78350f;
  background: linear-gradient(90deg, #fde68a, #fcd34d);
  border-bottom: 1px solid #f59e0b;
  padding: 6px 12px;
}
</style>
