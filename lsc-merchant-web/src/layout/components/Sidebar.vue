<script setup lang="ts">
// 侧边栏 — 菜单分组: 工作台 / 商品管理 / 订单管理 / B2B交易 / 核销管理 / LSC账户 / 店铺设置 / 信用管理
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Odometer,
  Goods,
  EditPen,
  Files,
  List,
  RefreshLeft,
  Connection,
  Promotion,
  Money,
  Tickets,
  CreditCard,
  DataLine,
  Shop,
  LocationInformation,
  Medal
} from '@element-plus/icons-vue'

interface MenuItem {
  index: string
  title: string
  icon?: any
}
interface MenuGroup {
  group: string
  title: string
  items: MenuItem[]
}

const props = defineProps<{ collapsed: boolean }>()

const route = useRoute()
const activeIndex = computed(() => route.path)

const groups: MenuGroup[] = [
  {
    group: 'dashboard',
    title: '工作台',
    items: [{ index: '/dashboard', title: '工作台', icon: Odometer }]
  },
  {
    group: 'product',
    title: '商品管理',
    items: [
      { index: '/product/list', title: '商品列表', icon: Goods },
      { index: '/product/publish', title: '发布商品', icon: EditPen },
      { index: '/product/category', title: '商品类目', icon: Files }
    ]
  },
  {
    group: 'order',
    title: '订单管理',
    items: [
      { index: '/order/list', title: '订单管理', icon: List },
      { index: '/order/refund', title: '退款管理', icon: RefreshLeft }
    ]
  },
  {
    group: 'b2b',
    title: 'B2B交易',
    items: [
      { index: '/b2b/list', title: 'B2B订单', icon: Connection },
      { index: '/b2b/create', title: '发起B2B交易', icon: Promotion }
    ]
  },
  {
    group: 'writeoff',
    title: '核销管理',
    items: [
      { index: '/writeoff/apply', title: '申请核销', icon: Money },
      { index: '/writeoff/records', title: '核销记录', icon: Tickets }
    ]
  },
  {
    group: 'lsc',
    title: 'LSC账户',
    items: [
      { index: '/lsc/account', title: 'LSC账户', icon: CreditCard },
      { index: '/lsc/transactions', title: 'LSC流水', icon: DataLine }
    ]
  },
  {
    group: 'store',
    title: '店铺设置',
    items: [
      { index: '/store/info', title: '店铺信息', icon: Shop },
      { index: '/store/address', title: '线下地址', icon: LocationInformation }
    ]
  },
  {
    group: 'credit',
    title: '信用管理',
    items: [{ index: '/credit/info', title: '信用信息', icon: Medal }]
  }
]
</script>

<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': props.collapsed }">
    <div class="sidebar__brand">
      <div class="sidebar__logo">
        <span class="sidebar__logo-mark">L</span>
      </div>
      <transition name="fade">
        <div v-show="!props.collapsed" class="sidebar__brand-text">
          <div class="sidebar__brand-title">链盛通</div>
          <div class="sidebar__brand-sub">商家管理后台</div>
        </div>
      </transition>
    </div>

    <nav class="sidebar__nav">
      <template v-for="g in groups" :key="g.group">
        <div v-show="!props.collapsed" class="sidebar__group-label">{{ g.title }}</div>
        <div v-show="props.collapsed" class="sidebar__group-divider" />
        <router-link
          v-for="item in g.items"
          :key="item.index"
          :to="item.index"
          class="sidebar__item"
          :class="{ 'is-active': activeIndex === item.index }"
          :title="props.collapsed ? item.title : ''"
        >
          <el-icon class="sidebar__item-icon" v-if="item.icon">
            <component :is="item.icon" />
          </el-icon>
          <span v-show="!props.collapsed" class="sidebar__item-text">{{ item.title }}</span>
          <span v-if="props.collapsed && !item.icon" class="sidebar__item-dot" />
        </router-link>
      </template>
    </nav>

    <div v-show="!props.collapsed" class="sidebar__footer">
      <div class="sidebar__footer-tip">
        <el-icon><Medal /></el-icon>
        <span>LSC 通证 · 监管可信</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 248px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0f172a 0%, #134e4a 100%);
  color: #cbd5e1;
  z-index: 1001;
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 4px 0 24px rgba(15, 23, 42, 0.08);
}

.sidebar--collapsed {
  width: 76px;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sidebar__logo {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--lsc-primary-400), var(--lsc-gold-500));
  display: grid;
  place-items: center;
  box-shadow: 0 8px 20px rgba(13, 148, 136, 0.4);
  flex-shrink: 0;
}

.sidebar__logo-mark {
  font-family: var(--lsc-font-display);
  font-weight: 800;
  font-size: 20px;
  color: #fff;
}

.sidebar__brand-title {
  font-family: var(--lsc-font-display);
  font-weight: 700;
  font-size: 16px;
  color: #fff;
  letter-spacing: 0.02em;
}

.sidebar__brand-sub {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
  letter-spacing: 0.06em;
}

.sidebar__nav {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px 20px;
}

.sidebar__nav::-webkit-scrollbar {
  width: 4px;
}

.sidebar__nav::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
}

.sidebar__group-label {
  padding: 16px 12px 6px;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  letter-spacing: 0.08em;
}

.sidebar__group-divider {
  height: 1px;
  background: rgba(255, 255, 255, 0.08);
  margin: 12px 8px;
}

.sidebar__item {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 40px;
  padding: 0 12px;
  border-radius: 10px;
  color: #cbd5e1;
  font-size: 13.5px;
  font-weight: 500;
  margin-bottom: 2px;
  transition: all 0.18s ease;
  position: relative;
}

.sidebar__item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.sidebar__item.is-active {
  background: linear-gradient(135deg, rgba(13, 148, 136, 0.35), rgba(20, 184, 166, 0.2));
  color: #fff;
  box-shadow: inset 0 0 0 1px rgba(94, 234, 212, 0.25);
}

.sidebar__item.is-active::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  background: var(--lsc-gold-500);
  border-radius: 0 3px 3px 0;
}

.sidebar__item-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.sidebar--collapsed .sidebar__item {
  justify-content: center;
  padding: 0;
}

.sidebar__footer {
  padding: 12px 18px 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.sidebar__footer-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: #64748b;
  letter-spacing: 0.04em;
}
</style>
