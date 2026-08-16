<script setup lang="ts">
// 顶部头部 — 折叠按钮 / 面包屑 / LSC 余额快览 / 商家菜单
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Fold, Expand, ArrowDown, SwitchButton, User, Wallet } from '@element-plus/icons-vue'
import { useMerchantStore } from '@/stores/merchant'
import { getLscOverview, type LscOverview } from '@/api/lsc'
import { logout as apiLogout } from '@/api/auth'

defineProps<{ collapsed: boolean }>()
const emit = defineEmits<{ (e: 'toggle'): void }>()

const route = useRoute()
const router = useRouter()
const merchant = useMerchantStore()
const { storeName } = storeToRefs(merchant)

const overview = ref<LscOverview | null>(null)

const currentTitle = computed(() => (route.meta.title as string) || '工作台')

onMounted(async () => {
  try {
    overview.value = await getLscOverview()
  } catch {
    /* 静默失败，避免阻塞布局 */
  }
})

async function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
    try {
      await apiLogout()
    } catch {
      /* 忽略 */
    }
    merchant.logout()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}

function fmt(n: number) {
  return n.toLocaleString('en-US')
}
</script>

<template>
  <header class="header">
    <div class="header__left">
      <button class="header__collapse" @click="emit('toggle')">
        <el-icon v-if="collapsed"><Expand /></el-icon>
        <el-icon v-else><Fold /></el-icon>
      </button>
      <div class="header__crumb">
        <span class="header__crumb-root">商家后台</span>
        <span class="header__crumb-sep">/</span>
        <span class="header__crumb-cur">{{ currentTitle }}</span>
      </div>
    </div>

    <div class="header__right">
      <!-- LSC 余额快览 -->
      <div v-if="overview" class="header__lsc" title="LSC 账户概览">
        <el-icon class="header__lsc-icon"><Wallet /></el-icon>
        <div class="header__lsc-body">
          <span class="header__lsc-label">可用 LSC</span>
          <span class="header__lsc-value lsc-num lsc-gold-text">{{ fmt(overview.totalAvailable) }}</span>
        </div>
        <div class="header__lsc-divider" />
        <div class="header__lsc-body">
          <span class="header__lsc-label">锁定 LSC</span>
          <span class="header__lsc-value lsc-num">{{ fmt(overview.totalLocked) }}</span>
        </div>
      </div>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="header__user">
          <div class="header__avatar">
            <el-icon><User /></el-icon>
          </div>
          <span class="header__user-name">{{ storeName }}</span>
          <el-icon class="header__user-arrow"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :icon="User" disabled>{{ storeName }}</el-dropdown-item>
            <el-dropdown-item :icon="SwitchButton" command="logout" divided>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--lsc-border-soft);
}

.header__left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header__collapse {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid var(--lsc-border);
  border-radius: 9px;
  background: var(--lsc-surface);
  color: var(--lsc-text-regular);
  cursor: pointer;
  transition: all 0.18s ease;
}

.header__collapse:hover {
  background: var(--lsc-primary-50);
  color: var(--lsc-primary-700);
  border-color: var(--lsc-primary-200);
}

.header__crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  color: var(--lsc-text-secondary);
}

.header__crumb-root {
  color: var(--lsc-text-placeholder);
}

.header__crumb-sep {
  color: var(--lsc-text-disabled);
}

.header__crumb-cur {
  color: var(--lsc-text);
  font-weight: 600;
  font-family: var(--lsc-font-display);
}

.header__right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header__lsc {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 14px 6px 12px;
  background: linear-gradient(135deg, var(--lsc-gold-50), #fff7ed);
  border: 1px solid var(--lsc-gold-100);
  border-radius: 12px;
}

.header__lsc-icon {
  color: var(--lsc-gold-600);
  font-size: 18px;
}

.header__lsc-body {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.header__lsc-label {
  font-size: 10.5px;
  color: var(--lsc-text-secondary);
}

.header__lsc-value {
  font-size: 14px;
  font-weight: 700;
}

.header__lsc-divider {
  width: 1px;
  height: 22px;
  background: var(--lsc-gold-200);
}

.header__user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.18s ease;
}

.header__user:hover {
  background: var(--lsc-bg-soft);
}

.header__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--lsc-primary-500), var(--lsc-primary-700));
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 16px;
}

.header__user-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--lsc-text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header__user-arrow {
  color: var(--lsc-text-secondary);
  font-size: 12px;
}
</style>
