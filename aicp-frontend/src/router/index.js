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
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'dashboard',
        redirect: '/home'
      },
      // === 剧本创作启动台 ===
      {
        path: 'script-gen',
        name: 'ScriptGen',
        component: () => import('@/views/content-project/ScriptCreationHome.vue'),
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
      // === 旧版兼容 → 重定向到统一创作台 ===
      {
        path: 'script-gen-legacy',
        redirect: '/script-gen'
      },
      {
        path: 'canvas-projects',
        name: 'CanvasProjectCenter',
        component: () => import('@/views/canvas-project/CanvasProjectCenter.vue'),
        meta: { title: '画布视频工作台' }
      },
      {
        path: 'canvas',
        redirect: '/canvas-projects'
      },
      {
        path: 'canvas/:projectId',
        name: 'Canvas',
        component: () => import('@/views/Canvas.vue'),
        meta: { title: '画布编辑器', canvasWorkspace: true }
      },
      {
        path: 'canvas/:projectId/shot-units/:shotUnitId/director',
        name: 'DirectorWorkspace',
        component: () => import('@/views/canvas/director/DirectorWorkspace.vue'),
        meta: { title: '导演台', directorV2: true }
      },
      {
        path: '画布工作台/:projectId?',
        redirect: to => `/canvas-projects`
      },
      {
        path: 'warehouse',
        name: 'Warehouse',
        component: () => import('@/views/Warehouse.vue'),
        meta: { title: '剧本仓库' }
      },
      {
        path: 'warehouse/:projectId',
        name: 'WarehouseProjectDetail',
        component: () => import('@/views/content-project/ContentProjectDetail.vue'),
        meta: { title: '剧本详情' }
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
        path: 'market/:listingId',
        name: 'MarketDetail',
        component: () => import('@/views/trade/ScriptMarketDetail.vue'),
        meta: { title: '剧本详情' }
      },
      {
        path: 'trade/orders',
        name: 'MyOrders',
        component: () => import('@/views/trade/MyPurchases.vue'),
        meta: { title: '我的订单' }
      },
      {
        path: 'trade/orders/:orderNo',
        name: 'OrderDetail',
        component: () => import('@/views/trade/MyPurchases.vue'),
        meta: { title: '订单详情' }
      },
      {
        path: 'trade/checkout/:listingId',
        name: 'TradeCheckout',
        component: () => import('@/views/trade/TradeCheckout.vue'),
        meta: { title: '确认订单' }
      },
      {
        path: 'wallet/topup',
        name: 'WalletTopUp',
        component: () => import('@/views/trade/WalletTopUp.vue'),
        meta: { title: '充值' }
      },
      {
        path: 'trade/seller',
        name: 'SellerTradeCenter',
        component: () => import('@/views/trade/SellerTradeCenter.vue'),
        meta: { title: '卖家中心' }
      },
      {
        path: 'trade/enterprise',
        name: 'EnterprisePurchaseCenter',
        component: () => import('@/views/trade/EnterprisePurchaseCenter.vue'),
        meta: { title: '企业采购' }
      },
      {
        path: 'asset-market',
        name: 'AssetMarket',
        component: () => import('@/views/AssetMarket.vue'),
        meta: { title: 'AI资产与风格模型市场' }
      },
      {
        path: 'enterprise',
        component: () => import('@/views/enterprise/EnterpriseShell.vue'),
        redirect: '/enterprise/overview',
        children: [
          {
            path: 'overview',
            name: 'EnterpriseOverview',
            component: () => import('@/views/enterprise/EnterpriseOverview.vue'),
            meta: { title: '企业中心' }
          },
          {
            path: 'organization',
            name: 'EnterpriseOrganization',
            component: () => import('@/views/enterprise/EnterpriseOrganization.vue'),
            meta: { title: '组织与成员' }
          },
          {
            path: 'approvals',
            name: 'EnterpriseApprovals',
            component: () => import('@/views/enterprise/EnterpriseApprovals.vue'),
            meta: { title: '统一审批' }
          },
          {
            path: 'budgets',
            name: 'EnterpriseBudgets',
            component: () => import('@/views/enterprise/EnterpriseBudgets.vue'),
            meta: { title: '预算与用量' }
          },
          {
            path: 'audit',
            name: 'EnterpriseAudit',
            component: () => import('@/views/enterprise/EnterpriseAudit.vue'),
            meta: { title: '审计记录' }
          }
        ]
      },
      {
        path: 'sop/:projectId',
        name: 'Sop',
        component: () => import('@/views/Sop.vue'),
        meta: { title: '工业化生产SOP' }
      },
      {
        path: 'sop',
        name: 'SopProjectList',
        component: () => import('@/views/sop/SopProjectList.vue'),
        meta: { title: '工业化生产SOP' }
      },
      {
        path: 'content-projects/:projectId/sop',
        name: 'SopWorkspace',
        component: () => import('@/views/sop/SopWorkspace.vue'),
        meta: { title: 'SOP工作台' }
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
        path: 'agent-config',
        name: 'AgentConfig',
        component: () => import('@/views/agent-config/AgentConfigCenter.vue'),
        meta: { title: 'Agent 配置中心' }
      },
      {
        path: 'asset-history',
        name: 'AssetHistory',
        component: () => import('@/views/generation/AssetHistory.vue'),
        meta: { title: '资产生成工作台' }
      },
      {
        path: 'task-monitor',
        name: 'TaskMonitor',
        redirect: '/asset-history?status=pending,running,failed',
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
