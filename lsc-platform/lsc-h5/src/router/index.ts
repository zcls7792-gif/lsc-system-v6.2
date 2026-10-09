import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  { path: '/login', component: () => import('@/views/Login.vue'), meta: { title: '登录', public: true } },
  { path: '/wechat/callback', component: () => import('@/views/WechatCallback.vue'), meta: { title: '微信登录', public: true } },
  { path: '/home', component: () => import('@/views/Home.vue'), meta: { title: '首页', public: true } },
  { path: '/products', component: () => import('@/views/ProductList.vue'), meta: { title: '商品', public: true } },
  { path: '/product/:id', component: () => import('@/views/ProductDetail.vue'), meta: { title: '商品详情', hideTabBar: true, public: true } },
  { path: '/orders', component: () => import('@/views/OrderList.vue'), meta: { title: '订单', requiresAuth: true } },
  { path: '/order/:id', component: () => import('@/views/OrderDetail.vue'), meta: { title: '订单详情', hideTabBar: true, requiresAuth: true } },
  { path: '/account', component: () => import('@/views/Account.vue'), meta: { title: '权益中心', hideTabBar: true, requiresAuth: true } },
  { path: '/mine', component: () => import('@/views/Mine.vue'), meta: { title: '我的' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  if (to.meta.title) document.title = to.meta.title as string
  // 需要鉴权的页面：无 token 跳登录
  if (to.meta.requiresAuth && !localStorage.getItem('token')) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

export default router
