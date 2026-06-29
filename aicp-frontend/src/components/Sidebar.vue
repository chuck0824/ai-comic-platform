<template>
  <aside class="sidebar">
    <div class="sidebar-brand">
      <h2>AI漫剧生产工作台</h2>
      <span>V1.5 工业化</span>
    </div>
    <div class="sidebar-section">V7 创作</div>
    <router-link to="/content-projects" class="sidebar-item" :class="{ active: isActive('/content-projects') }">
      <el-icon><FolderAdd /></el-icon> 内容项目
    </router-link>
    <router-link to="/canvas" class="sidebar-item" :class="{ active: isActive('/canvas') }">
      <el-icon><Brush /></el-icon> 画布工作台
    </router-link>

    <div class="sidebar-section">旧版（兼容模式）</div>
    <router-link to="/script-gen" class="sidebar-item" :class="{ active: isActive('/script-gen') }">
      <el-icon><EditPen /></el-icon> 剧本创作（旧）
    </router-link>
    <router-link to="/warehouse" class="sidebar-item" :class="{ active: isActive('/warehouse') }">
      <el-icon><Collection /></el-icon> 剧本仓库（旧）
    </router-link>
    <router-link to="/storyboard/1" class="sidebar-item" :class="{ active: isActive('/storyboard') }">
      <el-icon><Film /></el-icon> 分镜编辑（旧）
    </router-link>
    <router-link to="/tag-editor/1" class="sidebar-item" :class="{ active: isActive('/tag-editor') }">
      <el-icon><PriceTag /></el-icon> 标签编辑
    </router-link>
    <router-link to="/market" class="sidebar-item" :class="{ active: isActive('/market') }">
      <el-icon><ShoppingBag /></el-icon> 交易市场
    </router-link>
    <router-link to="/asset-market" class="sidebar-item" :class="{ active: isActive('/asset-market') }">
      <el-icon><Layers /></el-icon> AI资产市场
    </router-link>

    <div class="sidebar-section">企业</div>
    <router-link to="/enterprise" class="sidebar-item" :class="{ active: isActive('/enterprise') }">
      <el-icon><OfficeBuilding /></el-icon> 企业工作台
    </router-link>
    <router-link to="/sop/1" class="sidebar-item" :class="{ active: isActive('/sop') }">
      <el-icon><CircleCheck /></el-icon> 生产SOP
    </router-link>

    <div class="sidebar-section">V1.5 AI</div>
    <router-link to="/agent" class="sidebar-item" :class="{ active: isActive('/agent') }">
      <el-icon><ChatDotRound /></el-icon> Agent 会话
    </router-link>
    <router-link to="/asset-history" class="sidebar-item" :class="{ active: isActive('/asset-history') }">
      <el-icon><FolderOpened /></el-icon> 资产生成历史
    </router-link>
    <router-link to="/task-monitor" class="sidebar-item" :class="{ active: isActive('/task-monitor') }">
      <el-icon><Monitor /></el-icon> 任务监控
    </router-link>

    <div style="flex:1"></div>
    <div class="sidebar-user">
      <div class="sidebar-user-avatar">{{ auth.user?.nickname?.charAt(0) || '用' }}</div>
      <div class="sidebar-user-info">
        <strong>{{ auth.userDisplayName }}</strong>
        <span>{{ memberLevelText }}</span>
      </div>
      <router-link to="/profile" style="color:#71717a;font-size:18px">
        <el-icon><Setting /></el-icon>
      </router-link>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

const memberLevelText = computed(() => {
  const map = { free: '免费用户', creator: '创作者会员', enterprise: '企业版' }
  return map[auth.memberLevel] || '免费用户'
})

function isActive(path) {
  return route.path.startsWith(path)
}
</script>
