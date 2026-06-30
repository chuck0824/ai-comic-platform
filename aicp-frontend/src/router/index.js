import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/components/AppLayout.vue'),
    redirect: '/canvas',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '工作台' }
      },
      // === V7 剧本创作（主入口，直接加载V7组件） ===
      {
        path: 'script-gen',
        name: 'ScriptGen',
        component: () => import('@/views/content-project/ContentProjectList.vue'),
        meta: { title: '剧本创作' }
      },
      {
        path: 'script-gen/new',
        name: 'ScriptGenCreate',
        component: () => import('@/views/content-project/ContentProjectCreate.vue'),
        meta: { title: '新建项目' }
      },
      {
        path: 'script-gen/:projectId/workspace',
        name: 'ScriptGenWorkspace',
        component: () => import('@/views/content-project/ContentProjectWorkspace.vue'),
        meta: { title: '流程化创作台' }
      },
      {
        path: 'script-gen/:projectId/edit/:section?',
        name: 'ScriptGenEditor',
        component: () => import('@/views/TagEditor.vue'),
        meta: { title: '作品编辑中心' }
      },
      // === V7 分镜专业编辑器 ===
      {
        path: 'content-projects/:projectId/storyboards/:storyboardId',
        name: 'StoryboardEditorV2',
        component: () => import('@/views/storyboard/StoryboardEditor.vue'),
        meta: { title: '分镜专业编辑器' }
      },
      // === 旧版兼容（仅维护） ===
      {
        path: 'script-gen-legacy',
        name: 'ScriptGenLegacy',
        component: () => import('@/views/ScriptGen.vue'),
        meta: { title: '剧本创作（旧版）' }
      },
      {
        path: 'storyboard/:scriptId',
        name: 'Storyboard',
        component: () => import('@/views/Storyboard.vue'),
        meta: { title: '分镜编辑' }
      },
      {
        path: 'canvas/:projectId?',
        name: 'Canvas',
        component: () => import('@/views/Canvas.vue'),
        meta: { title: '画布工作台', canvasWorkspace: true }
      },
      {
        path: '画布工作台/:projectId?',
        name: 'CanvasWorkbench',
        component: () => import('@/views/Canvas.vue'),
        meta: { title: '画布工作台', canvasWorkspace: true }
      },
      {
        path: 'warehouse',
        name: 'Warehouse',
        component: () => import('@/views/Warehouse.vue'),
        meta: { title: '剧本仓库' }
      },
      {
        path: 'tag-editor/:scriptId',
        name: 'TagEditor',
        component: () => import('@/views/TagEditor.vue'),
        meta: { title: '标签编辑' }
      },
      {
        path: 'market',
        name: 'Market',
        component: () => import('@/views/Market.vue'),
        meta: { title: '交易市场' }
      },
      {
        path: 'asset-market',
        name: 'AssetMarket',
        component: () => import('@/views/AssetMarket.vue'),
        meta: { title: 'AI资产市场' }
      },
      {
        path: 'enterprise',
        name: 'Enterprise',
        component: () => import('@/views/Enterprise.vue'),
        meta: { title: '企业工作台' }
      },
      {
        path: 'sop/:projectId',
        name: 'Sop',
        component: () => import('@/views/Sop.vue'),
        meta: { title: '生产SOP' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心' }
      },
      // === V1.5 新增路由 ===
      {
        path: 'agent',
        name: 'Agent',
        component: () => import('@/views/agent/AgentSession.vue'),
        meta: { title: 'Agent 会话' }
      },
      {
        path: 'asset-history',
        name: 'AssetHistory',
        component: () => import('@/views/generation/AssetHistory.vue'),
        meta: { title: '资产生成历史' }
      },
      {
        path: 'task-monitor',
        name: 'TaskMonitor',
        component: () => import('@/views/generation/TaskMonitor.vue'),
        meta: { title: '任务监控' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    if (authStore.isLoggedIn && to.path === '/login') {
      next('/dashboard')
    } else {
      next()
    }
  } else {
    if (!authStore.isLoggedIn) {
      next('/login')
    } else {
      next()
    }
  }
})

export default router
