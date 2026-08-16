import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const Layout = () => import('@/layout/index.vue')

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'Odometer', group: '首页' }
      }
    ]
  },
  {
    path: '/merchant',
    component: Layout,
    meta: { title: '商家管理', icon: 'Shop', group: '商家管理' },
    children: [
      {
        path: 'list',
        name: 'MerchantList',
        component: () => import('@/views/merchant/List.vue'),
        meta: { title: '商家列表', group: '商家管理' }
      },
      {
        path: 'audit',
        name: 'MerchantAudit',
        component: () => import('@/views/merchant/Audit.vue'),
        meta: { title: '商家审核', group: '商家管理' }
      },
      {
        path: 'credit',
        name: 'MerchantCredit',
        component: () => import('@/views/merchant/Credit.vue'),
        meta: { title: '信用管理', group: '商家管理' }
      }
    ]
  },
  {
    path: '/product',
    component: Layout,
    meta: { title: '商品管理', icon: 'Goods', group: '商品管理' },
    children: [
      {
        path: 'list',
        name: 'ProductList',
        component: () => import('@/views/product/List.vue'),
        meta: { title: '商品列表', group: '商品管理' }
      },
      {
        path: 'audit',
        name: 'ProductAudit',
        component: () => import('@/views/product/Audit.vue'),
        meta: { title: '商品审核', group: '商品管理' }
      }
    ]
  },
  {
    path: '/order',
    component: Layout,
    meta: { title: '订单管理', icon: 'List', group: '订单管理' },
    children: [
      {
        path: 'list',
        name: 'OrderList',
        component: () => import('@/views/order/List.vue'),
        meta: { title: '订单列表', group: '订单管理' }
      }
    ]
  },
  {
    path: '/b2b',
    component: Layout,
    meta: { title: 'B2B交易', icon: 'Connection', group: 'B2B交易' },
    children: [
      {
        path: 'list',
        name: 'B2BList',
        component: () => import('@/views/b2b/List.vue'),
        meta: { title: 'B2B订单', group: 'B2B交易' }
      },
      {
        path: 'verify',
        name: 'B2BVerify',
        component: () => import('@/views/b2b/Verify.vue'),
        meta: { title: 'B2B核验复核', group: 'B2B交易' }
      }
    ]
  },
  {
    path: '/writeoff',
    component: Layout,
    meta: { title: '核销管理', icon: 'CircleCheck', group: '核销管理' },
    children: [
      {
        path: 'list',
        name: 'WriteoffList',
        component: () => import('@/views/writeoff/List.vue'),
        meta: { title: '核销记录', group: '核销管理' }
      }
    ]
  },
  {
    path: '/release',
    component: Layout,
    meta: { title: '释放管理', icon: 'TrendCharts', group: '释放管理' },
    children: [
      {
        path: 'summary',
        name: 'ReleaseSummary',
        component: () => import('@/views/release/Summary.vue'),
        meta: { title: '释放汇总', group: '释放管理' }
      },
      {
        path: 'config',
        name: 'ReleaseConfig',
        component: () => import('@/views/release/Config.vue'),
        meta: { title: '释放配置', roles: ['super_admin', 'tech_admin'] }
      },
      {
        path: 'predict',
        name: 'ReleasePredict',
        component: () => import('@/views/release/Predict.vue'),
        meta: { title: 'AI趋势预测', group: '释放管理' }
      },
      {
        path: 'simulation',
        name: 'ReleaseSimulation',
        component: () => import('@/views/release/Simulation.vue'),
        meta: { title: '仿真推演', group: '释放管理' }
      }
    ]
  },
  {
    path: '/risk',
    component: Layout,
    meta: { title: '风控管理', icon: 'Warning', group: '风控管理' },
    children: [
      {
        path: 'logs',
        name: 'RiskLogs',
        component: () => import('@/views/risk/Logs.vue'),
        meta: { title: '风控日志', group: '风控管理' }
      },
      {
        path: 'dashboard',
        name: 'RiskDashboard',
        component: () => import('@/views/risk/Dashboard.vue'),
        meta: { title: '风控仪表盘', group: '风控管理' }
      }
    ]
  },
  {
    path: '/evidence',
    component: Layout,
    meta: { title: '存证管理', icon: 'Document', group: '存证管理' },
    children: [
      {
        path: 'list',
        name: 'EvidenceList',
        component: () => import('@/views/evidence/List.vue'),
        meta: { title: '存证记录', group: '存证管理' }
      },
      {
        path: 'verify',
        name: 'EvidenceVerify',
        component: () => import('@/views/evidence/Verify.vue'),
        meta: { title: '存证校验', group: '存证管理' }
      }
    ]
  },
  {
    path: '/reconcile',
    component: Layout,
    meta: { title: '对账管理', icon: 'Money', group: '对账管理' },
    children: [
      {
        path: 'report',
        name: 'ReconcileReport',
        component: () => import('@/views/reconcile/Report.vue'),
        meta: { title: '对账报告', group: '对账管理' }
      }
    ]
  },
  {
    path: '/admin',
    component: Layout,
    meta: { title: '系统管理', icon: 'Setting', group: '系统管理', roles: ['super_admin'] },
    children: [
      {
        path: 'list',
        name: 'AdminList',
        component: () => import('@/views/admin/List.vue'),
        meta: { title: '管理员列表', group: '系统管理', roles: ['super_admin'] }
      },
      {
        path: 'audit',
        name: 'AdminAudit',
        component: () => import('@/views/admin/Audit.vue'),
        meta: { title: '操作审计', group: '系统管理', roles: ['super_admin'] }
      },
      {
        path: 'param',
        name: 'ParamApproval',
        component: () => import('@/views/param/Approval.vue'),
        meta: { title: '参数审批', group: '系统管理', roles: ['super_admin'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const whiteList = ['/login']

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  document.title = (to.meta.title as string) ? `${to.meta.title} - LSC管理后台` : 'LSC平台管理后台'
  if (userStore.token) {
    if (to.path === '/login') {
      next('/')
    } else {
      const roles = to.meta.roles as string[] | undefined
      if (roles && !roles.includes(userStore.role)) {
        next('/dashboard')
      } else {
        next()
      }
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
    }
  }
})

export default router
