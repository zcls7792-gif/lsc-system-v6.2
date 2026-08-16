import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useMerchantStore } from '@/stores/merchant'

/**
 * 链盛通 LSC 商家管理后台 · 路由表
 *
 * /login                       商家登录
 * / Layout
 *   /dashboard                 工作台
 *   /product/list              商品列表
 *   /product/publish           发布商品
 *   /product/category          商品类目
 *   /order/list                订单管理
 *   /order/refund              退款管理
 *   /b2b/list                  B2B订单
 *   /b2b/create                发起B2B交易
 *   /writeoff/apply            申请核销
 *   /writeoff/records          核销记录
 *   /lsc/account               LSC账户
 *   /lsc/transactions          LSC流水
 *   /store/info                店铺信息
 *   /store/address             线下地址
 *   /credit/info               信用信息
 */

const Layout = () => import('@/layout/index.vue')

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '商家登录', public: true }
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
        meta: { title: '工作台', icon: 'Odometer' }
      },
      {
        path: 'product/list',
        name: 'ProductList',
        component: () => import('@/views/product/List.vue'),
        meta: { title: '商品列表', icon: 'Goods', group: 'product' }
      },
      {
        path: 'product/publish',
        name: 'ProductPublish',
        component: () => import('@/views/product/Publish.vue'),
        meta: { title: '发布商品', icon: 'EditPen', group: 'product' }
      },
      {
        path: 'product/category',
        name: 'ProductCategory',
        component: () => import('@/views/product/Category.vue'),
        meta: { title: '商品类目', icon: 'Files', group: 'product' }
      },
      {
        path: 'order/list',
        name: 'OrderList',
        component: () => import('@/views/order/List.vue'),
        meta: { title: '订单管理', icon: 'List', group: 'order' }
      },
      {
        path: 'order/refund',
        name: 'OrderRefund',
        component: () => import('@/views/order/Refund.vue'),
        meta: { title: '退款管理', icon: 'RefreshLeft', group: 'order' }
      },
      {
        path: 'b2b/list',
        name: 'B2BList',
        component: () => import('@/views/b2b/List.vue'),
        meta: { title: 'B2B订单', icon: 'Connection', group: 'b2b' }
      },
      {
        path: 'b2b/create',
        name: 'B2BCreate',
        component: () => import('@/views/b2b/Create.vue'),
        meta: { title: '发起B2B交易', icon: 'Promotion', group: 'b2b' }
      },
      {
        path: 'writeoff/apply',
        name: 'WriteOffApply',
        component: () => import('@/views/writeoff/Apply.vue'),
        meta: { title: '申请核销', icon: 'Money', group: 'writeoff' }
      },
      {
        path: 'writeoff/records',
        name: 'WriteOffRecords',
        component: () => import('@/views/writeoff/Records.vue'),
        meta: { title: '核销记录', icon: 'Tickets', group: 'writeoff' }
      },
      {
        path: 'lsc/account',
        name: 'LscAccount',
        component: () => import('@/views/lsc/Account.vue'),
        meta: { title: 'LSC账户', icon: 'CreditCard', group: 'lsc' }
      },
      {
        path: 'lsc/transactions',
        name: 'LscTransactions',
        component: () => import('@/views/lsc/Transactions.vue'),
        meta: { title: 'LSC流水', icon: 'DataLine', group: 'lsc' }
      },
      {
        path: 'store/info',
        name: 'StoreInfo',
        component: () => import('@/views/store/Info.vue'),
        meta: { title: '店铺信息', icon: 'Shop', group: 'store' }
      },
      {
        path: 'store/address',
        name: 'StoreAddress',
        component: () => import('@/views/store/Address.vue'),
        meta: { title: '线下地址', icon: 'LocationInformation', group: 'store' }
      },
      {
        path: 'credit/info',
        name: 'CreditInfo',
        component: () => import('@/views/credit/Info.vue'),
        meta: { title: '信用信息', icon: 'Medal', group: 'credit' }
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
  routes,
  scrollBehavior: () => ({ top: 0 })
})

// 全局前置守卫：未登录跳转登录页
router.beforeEach((to, _from, next) => {
  const merchant = useMerchantStore()
  const isPublic = to.meta.public === true
  document.title = to.meta.title
    ? `${to.meta.title} · 链盛通商家后台`
    : '链盛通 · 商家管理后台'

  if (isPublic) {
    if (merchant.token && to.name === 'Login') {
      next({ path: '/dashboard' })
    } else {
      next()
    }
    return
  }

  if (!merchant.token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  next()
})

export default router
