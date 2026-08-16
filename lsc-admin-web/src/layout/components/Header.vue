<template>
  <div class="header-wrapper">
    <div class="header-left">
      <el-icon class="collapse-btn" @click="appStore.toggleSidebar()">
        <Fold v-if="!collapsed" />
        <Expand v-else />
      </el-icon>
    </div>
    <div class="header-right">
      <el-dropdown @command="handleCommand">
        <span class="user-info">
          <el-avatar :size="32" class="user-avatar">
            {{ avatarText }}
          </el-avatar>
          <span class="user-name">{{ userInfo?.realName || userInfo?.username || '管理员' }}</span>
          <el-tag size="small" :type="roleTagType" effect="plain" class="role-tag">
            {{ roleLabel }}
          </el-tag>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="info">个人信息</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { logout as logoutApi } from '@/api/admin'

const router = useRouter()
const appStore = useAppStore()
const { sidebarCollapsed: collapsed } = storeToRefs(appStore)
const userStore = useUserStore()
const { userInfo } = storeToRefs(userStore)

const roleMap: Record<string, string> = {
  super_admin: '超级管理员',
  ops_admin: '运营管理员',
  tech_admin: '技术管理员',
  finance_admin: '财务管理员'
}

const roleLabel = computed(() => roleMap[userStore.role] || '管理员')
const roleTagType = computed(() => {
  return userStore.role === 'super_admin' ? 'danger' : 'primary'
})
const avatarText = computed(() => {
  const name = userInfo.value?.realName || userInfo.value?.username || 'A'
  return name.charAt(0).toUpperCase()
})

async function handleCommand(command: string) {
  if (command === 'info') {
    ElMessage.info('个人信息功能开发中')
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      try {
        await logoutApi()
      } catch (e) {
        /* 忽略退出接口错误 */
      }
      userStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch (e) {
      /* 取消 */
    }
  }
}
</script>

<style scoped>
.header-wrapper {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #5a5e66;
}
.collapse-btn:hover {
  color: #409eff;
}
.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  gap: 8px;
}
.user-avatar {
  background-color: #409eff;
  color: #fff;
}
.user-name {
  font-size: 14px;
  color: #303133;
}
.role-tag {
  margin-left: 4px;
}
</style>
