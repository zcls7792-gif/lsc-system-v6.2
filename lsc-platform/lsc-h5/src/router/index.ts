import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  { path: '/home', component: () => import('@/views/Home.vue'), meta: { title: '首页' } },
  { path: '/products', component: () => import('@/views/ProductList.vue'), meta: { title: '商品' } },
  { path: '/product/:id', component: () => import('@/views/ProductDetail.vue'), meta: { title: '商品详情', hideTabBar: true } },
  { path: '/orders', component: () => import('@/views/OrderList.vue'), meta: { title: '订单' } },
  { path: '/order/:id', component: () => import('@/views/OrderDetail.vue'), meta: { title: '订单详情', hideTabBar: true } },
  { path: '/account', component: () => import('@/views/Account.vue'), meta: { title: '权益中心', hideTabBar: true } },
  { path: '/mine', component: () => import('@/views/Mine.vue'), meta: { title: '我的' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  if (to.meta.title) document.title = to.meta.title as string
  next()
})

export default router
