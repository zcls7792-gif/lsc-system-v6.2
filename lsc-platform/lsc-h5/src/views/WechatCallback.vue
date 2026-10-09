<template>
  <div class="callback-page">
    <van-loading type="spinner" size="36px">微信登录中...</van-loading>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { loginByWechatH5 } from '@/api/auth'

const route = useRoute()
const router = useRouter()

onMounted(async () => {
  const code = route.query.code as string
  const state = route.query.state as string | undefined
  const redirect = (state && state.startsWith('redirect='))
    ? state.substring(9)
    : '/home'

  if (!code) {
    showToast('微信授权失败：缺少 code')
    setTimeout(() => router.replace('/login'), 1500)
    return
  }

  try {
    const result = await loginByWechatH5(code)
    localStorage.setItem('token', result.token)
    localStorage.setItem('userId', String(result.userId))
    showToast('登录成功')
    setTimeout(() => router.replace(redirect), 800)
  } catch (e: any) {
    showToast(e?.message || '微信登录失败')
    setTimeout(() => router.replace('/login'), 1500)
  }
})
</script>

<style scoped>
.callback-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
}
</style>
