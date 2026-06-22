<template>
  <header class="topbar">
    <h1 class="topbar-title">{{ title }}</h1>
    <div class="topbar-actions">
      <span class="badge badge-accent">{{ memberBadge }}</span>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0">
        <el-button circle :icon="Bell" />
      </el-badge>
      <el-dropdown trigger="click">
        <span class="sidebar-user-avatar" style="cursor:pointer">{{ auth.user?.nickname?.charAt(0) || '用' }}</span>
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
import { Bell } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

defineProps({ title: { type: String, default: '工作台' } })

const auth = useAuthStore()
const unreadCount = ref(2)

const memberBadge = computed(() => {
  const map = { free: '免费用户', creator: '创作者会员', enterprise: '企业版' }
  return map[auth.memberLevel] || '免费用户'
})

function handleLogout() {
  auth.logout()
}
</script>
