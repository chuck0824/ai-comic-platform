<template>
  <aside :class="['sidebar', { open }]">
    <div class="sidebar-brand">
      <h2>AI漫剧生产工作台</h2>
      <span>V7 工业化</span>
    </div>

    <router-link to="/home" class="sidebar-item" :class="{ active: isActive('/home') }" @click="onNavClick">
      <el-icon><HomeFilled /></el-icon> 首页
    </router-link>

    <div class="sidebar-section">创作与生产</div>
    <router-link to="/script-gen" class="sidebar-item" :class="{ active: isActive('/script-gen') || isActive('/content-projects') }" @click="onNavClick">
      <el-icon><EditPen /></el-icon> 剧本创作
    </router-link>
    <router-link to="/canvas-projects" class="sidebar-item" :class="{ active: isActive('/canvas-projects') }" @click="onNavClick">
      <el-icon><Brush /></el-icon> 画布视频工作台
    </router-link>

    <div class="sidebar-section">内容与交易</div>
    <router-link to="/warehouse" class="sidebar-item" :class="{ active: isActive('/warehouse') }" @click="onNavClick">
      <el-icon><Collection /></el-icon> 剧本仓库
    </router-link>
    <router-link to="/market" class="sidebar-item" :class="{ active: isActive('/market') }" @click="onNavClick">
      <el-icon><ShoppingBag /></el-icon> 剧本交易市场
    </router-link>
    <router-link to="/asset-market" class="sidebar-item" :class="{ active: isActive('/asset-market') }" @click="onNavClick">
      <el-icon><MagicStick /></el-icon> AI资产市场
    </router-link>

    <div class="sidebar-section">智能生产</div>
    <router-link to="/agent" class="sidebar-item" :class="{ active: isActive('/agent') && !isActive('/agent-config') }" @click="onNavClick">
      <el-icon><ChatDotRound /></el-icon> Agent 会话
    </router-link>
    <router-link to="/agent-config" class="sidebar-item" :class="{ active: isActive('/agent-config') }" @click="onNavClick">
      <el-icon><Setting /></el-icon> Agent 配置中心
    </router-link>
    <router-link to="/asset-history" class="sidebar-item" :class="{ active: isActive('/asset-history') }" @click="onNavClick">
      <el-icon><FolderOpened /></el-icon> 资产生成工作台
    </router-link>

    <div class="sidebar-section">企业</div>
    <router-link to="/enterprise" class="sidebar-item" :class="{ active: isActive('/enterprise') }" @click="onNavClick">
      <el-icon><OfficeBuilding /></el-icon> 企业中心
    </router-link>
    <router-link to="/sop" class="sidebar-item" :class="{ active: isActive('/sop') }" @click="onNavClick">
      <el-icon><CircleCheck /></el-icon> 工业化生产SOP
    </router-link>

    <div style="flex:1"></div>
    <div class="sidebar-user">
      <div class="sidebar-user-avatar">{{ auth.user?.nickname?.charAt(0) || '用' }}</div>
      <div class="sidebar-user-info">
        <strong>{{ auth.userDisplayName }}</strong>
        <span>{{ memberLevelText }}</span>
      </div>
      <router-link to="/profile" style="color:#71717a;font-size:18px" @click="onNavClick">
        <el-icon><Setting /></el-icon>
      </router-link>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

defineProps({
  open: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])

const route = useRoute()
const auth = useAuthStore()

const memberLevelText = computed(() => {
  const map = { free: '免费用户', creator: '创作者会员', enterprise: '企业版' }
  return map[auth.memberLevel] || '免费用户'
})

function isActive(path) {
  return route.path.startsWith(path)
}

function onNavClick() {
  // Close sidebar on mobile after navigation
  emit('close')
}
</script>
