<template>
  <header class="topbar">
    <div class="topbar-left">
      <button class="mobile-menu-toggle" @click="$emit('toggleSidebar')" aria-label="打开菜单">
        <el-icon :size="22"><Menu /></el-icon>
      </button>
      <h1 class="topbar-title">{{ title }}</h1>
    </div>
    <div class="topbar-center">
      <el-dropdown trigger="click" @command="handleWorkspaceSwitch">
        <span class="workspace-switcher" aria-label="切换工作区">
          <el-icon><OfficeBuilding /></el-icon>
          <span class="workspace-name">{{ workspaceLabel }}</span>
          <el-icon class="workspace-arrow"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="ws in workspaceItems"
              :key="ws.id"
              :command="ws.id"
              :class="{ 'is-active': ws.id === workspace.activeId }"
            >
              <span>{{ ws.label }}</span>
              <el-tag size="small" :type="ws.type === 'enterprise' ? 'primary' : 'info'">
                {{ ws.type === 'enterprise' ? '企业' : '个人' }}
              </el-tag>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <div class="topbar-actions">
      <span class="badge badge-accent">{{ memberBadge }}</span>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0">
        <el-button circle :icon="Bell" aria-label="通知" />
      </el-badge>
      <el-dropdown trigger="click">
        <span class="sidebar-user-avatar" style="cursor:pointer" :aria-label="`用户菜单：${auth.userDisplayName}`">{{ auth.user?.nickname?.charAt(0) || '用' }}</span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="$router.push('/profile')">个人中心</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/warehouse')">我的仓库</el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ArrowDown, Bell, Menu, OfficeBuilding } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'

defineProps({ title: { type: String, default: '工作台' } })
defineEmits(['toggleSidebar'])

const auth = useAuthStore()
const workspace = useWorkspaceStore()
const unreadCount = ref(2)

const memberBadge = computed(() => {
  const map = { free: '免费用户', creator: '创作者会员', enterprise: '企业版' }
  return map[auth.memberLevel] || '免费用户'
})

const workspaceLabel = computed(() => {
  if (workspace.loading) return '加载中...'
  return workspace.activeId || '个人空间'
})

const workspaceItems = computed(() => {
  const items = [{ id: 'personal', label: '个人空间', type: 'personal' }]
  // Use the full workspace list from store (loaded from 3001 via BFF)
  for (const w of workspace.items) {
    items.push({ id: w.id, label: w.id, type: w.type })
  }
  return items
})

async function handleWorkspaceSwitch(id) {
  if (id === 'personal') {
    workspace.fallbackToPersonal(auth.getUserId())
    location.reload()
  } else {
    await workspace.selectWorkspace(id)
    location.reload()
  }
}

function handleLogout() {
  auth.logout()
}
</script>

<style scoped>
.workspace-switcher {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 13px;
  color: var(--el-text-color-primary);
  transition: background .2s;
}
.workspace-switcher:hover {
  background: var(--el-fill-color);
}
.workspace-arrow {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.is-active {
  font-weight: 600;
}
</style>
