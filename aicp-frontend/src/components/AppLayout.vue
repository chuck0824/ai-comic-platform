<template>
  <a href="#main-content" class="skip-link">跳到主内容</a>
  <div :class="['app-layout', { 'canvas-shell': isCanvasRoute }]">
    <!-- Mobile sidebar overlay -->
    <div :class="['sidebar-overlay', { show: sidebarOpen }]" @click="closeSidebar" />

    <Sidebar :open="sidebarOpen" @close="closeSidebar" />

    <div :class="['main-area', { 'canvas-main': isCanvasRoute }]">
      <Topbar v-if="!isCanvasRoute" :title="pageTitle" @toggle-sidebar="toggleSidebar" />
      <div id="main-content" :class="['content', { 'canvas-content': isCanvasRoute }]">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './Sidebar.vue'
import Topbar from './Topbar.vue'

const route = useRoute()
const pageTitle = computed(() => route.meta.title || '工作台')
const isCanvasRoute = computed(() => route.meta.canvasWorkspace === true)

const sidebarOpen = ref(false)

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}

function closeSidebar() {
  sidebarOpen.value = false
}
</script>
