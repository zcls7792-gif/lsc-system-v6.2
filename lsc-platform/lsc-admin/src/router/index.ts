import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '工作台', icon: 'Odometer' }
      },
      {
        path: 'products',
        name: 'ProductList',
        component: () => import('@/views/product/ProductListView.vue'),
        meta: { title: '商品管理', icon: 'Goods' }
      },
      {
        path: 'orders',
        name: 'OrderList',
        component: () => import('@/views/order/OrderListView.vue'),
        meta: { title: '订单管理', icon: 'List' }
      },
      {
        path: 'accounts',
        name: 'AccountList',
        component: () => import('@/views/account/AccountListView.vue'),
        meta: { title: '权益账本', icon: 'Wallet' }
      },
      {
        path: 'risk',
        name: 'RiskCases',
        component: () => import('@/views/risk/RiskCaseView.vue'),
        meta: { title: '风控管理', icon: 'Warning' }
      },
      {
        path: 'config',
        name: 'ConfigAudit',
        component: () => import('@/views/config/ConfigAuditView.vue'),
        meta: { title: '配置审计', icon: 'Setting' }
      },
      {
        path: 'supplychain',
        name: 'Supplychain',
        component: () => import('@/views/supplychain/SupplychainView.vue'),
        meta: { title: '供应链', icon: 'Box' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：登录校验
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.isLoggedIn) {
    next('/login')
  } else if (to.path === '/login' && userStore.isLoggedIn) {
    next('/')
  } else {
    next()
  }
})

export default router
