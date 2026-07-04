<template>
  <div class="enterprise-shell">
    <nav class="enterprise-nav">
      <router-link
        v-for="item in visibleNav"
        :key="item.key"
        :to="item.to"
        class="nav-item"
        active-class="nav-active"
      >
        {{ item.label }}
      </router-link>
    </nav>
    <main class="enterprise-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useWorkspaceStore } from '@/stores/workspace'

const workspace = useWorkspaceStore()

const navItems = [
  { key: 'overview', to: '/enterprise/overview', label: '概览', roles: [] },
  { key: 'organization', to: '/enterprise/organization', label: '组织', roles: ['admin'] },
  { key: 'approvals', to: '/enterprise/approvals', label: '审批', roles: [] },
  { key: 'budgets', to: '/enterprise/budgets', label: '预算', roles: [] },
  { key: 'audit', to: '/enterprise/audit', label: '审计', roles: ['admin'] }
]

const visibleNav = computed(() => {
  const ctx = workspace.membership
  if (!ctx?.visibleMenuKeys) return navItems
  return navItems.filter(item => ctx.visibleMenuKeys.includes(item.key))
})
</script>

<style scoped>
.enterprise-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.enterprise-nav {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--el-border-color-light);
  padding: 0 24px;
  background: var(--el-bg-color);
}
.nav-item {
  padding: 12px 20px;
  text-decoration: none;
  color: var(--el-text-color-regular);
  font-size: 14px;
  border-bottom: 2px solid transparent;
  transition: color .2s, border-color .2s;
}
.nav-item:hover {
  color: var(--el-color-primary);
}
.nav-active {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
  font-weight: 600;
}
.enterprise-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
</style>
