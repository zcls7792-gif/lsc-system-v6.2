<script setup lang="ts">
import { onLaunch, onShow, onHide, onError } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

onLaunch(() => {
  // 应用启动：尝试恢复登录态
  console.log('[App] onLaunch')
  userStore.restore()
  if (userStore.token) {
    userStore.fetchProfile().catch(() => {
      // token 失效，清理
      userStore.logoutSilent()
    })
  }
})

onShow(() => {
  console.log('[App] onShow')
})

onHide(() => {
  console.log('[App] onHide')
})

onError((err) => {
  console.error('[App] onError:', err)
})
</script>

<style lang="scss">
/* 注意：全局样式必须在 App.vue 中引入，且不能使用 scoped */
@import '@/styles/common.scss';

/* #ifndef APP-NVUE */
page {
  background-color: #f5f6fa;
  color: #333;
  font-size: 28rpx;
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Helvetica,
    Segoe UI, Arial, Roboto, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft Yahei', sans-serif;
}
/* #endif */
</style>
