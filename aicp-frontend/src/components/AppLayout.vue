<template>
  <a href="#main-content" class="skip-link">跳到主内容</a>
  <div :class="['app-layout', { 'canvas-shell': isCanvasRoute }]">
    <Sidebar />
    <div :class="['main-area', { 'canvas-main': isCanvasRoute }]">
      <Topbar v-if="!isCanvasRoute" :title="pageTitle" />
      <div id="main-content" :class="['content', { 'canvas-content': isCanvasRoute }]">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './Sidebar.vue'
import Topbar from './Topbar.vue'

const route = useRoute()
const pageTitle = computed(() => route.meta.title || '工作台')
const isCanvasRoute = computed(() => route.meta.canvasWorkspace === true)
</script>
