<template>
  <view class="login">
    <view class="login__bg"></view>

    <view class="login__content">
      <view class="login__logo">
        <text class="login__logo-text">LSC</text>
      </view>
      <text class="login__title">链生通</text>
      <text class="login__subtitle">权益商城 · LSC账户 · 混合支付</text>

      <!-- 登录方式切换 -->
      <view class="login__tabs">
        <text
          class="login__tab"
          :class="{ 'login__tab--active': loginType === 'password' }"
          @click="loginType = 'password'"
        >密码登录</text>
        <text
          class="login__tab"
          :class="{ 'login__tab--active': loginType === 'sms' }"
          @click="loginType = 'sms'"
        >验证码登录</text>
      </view>

      <!-- 表单 -->
      <view class="login__form">
        <view class="login__field">
          <text class="login__field-icon">📱</text>
          <input
            class="login__input"
            v-model="form.phone"
            type="number"
            maxlength="11"
            placeholder="请输入手机号"
          />
        </view>

        <view v-if="loginType === 'password'" class="login__field">
          <text class="login__field-icon">🔒</text>
          <input
            class="login__input"
            v-model="form.password"
            :password="!showPwd"
            placeholder="请输入密码"
          />
          <text class="login__field-action" @click="showPwd = !showPwd">{{ showPwd ? '🙈' : '👁️' }}</text>
        </view>

        <view v-else class="login__field">
          <text class="login__field-icon">💬</text>
          <input
            class="login__input"
            v-model="form.code"
            type="number"
            maxlength="6"
            placeholder="请输入验证码"
          />
          <text
            class="login__field-action"
            :class="{ 'login__field-action--disabled': counting }"
            @click="sendCode"
          >{{ counting ? `${count}s` : '获取验证码' }}</text>
        </view>
      </view>

      <button class="login__btn" :loading="loading" @click="onLogin">登 录</button>

      <view class="login__footer">
        <text class="login__link" @click="goRegister">没有账号？立即注册</text>
        <text class="login__link" @click="goReset">忘记密码</text>
      </view>

      <view class="login__agreement">
        <view class="login__check" :class="{ 'login__check--active': agreed }" @click="agreed = !agreed">
          <text v-if="agreed" class="login__check-icon">✓</text>
        </view>
        <text class="fs-sm text-secondary">
          我已阅读并同意《<text class="login__link" @click.stop="openAgreement">用户协议</text>》《<text class="login__link" @click.stop="openPrivacy">隐私政策</text>》
        </text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { sendSmsCode } from '@/api/user'

const userStore = useUserStore()
const loginType = ref<'password' | 'sms'>('password')
const loading = ref(false)
const agreed = ref(false)
const showPwd = ref(false)
const counting = ref(false)
const count = ref(60)
const redirect = ref('')

const form = reactive({
  phone: '',
  password: '',
  code: '',
})

let timer: ReturnType<typeof setInterval> | null = null

function startCount() {
  counting.value = true
  count.value = 60
  timer = setInterval(() => {
    count.value--
    if (count.value <= 0) {
      counting.value = false
      if (timer) clearInterval(timer)
    }
  }, 1000)
}

async function sendCode() {
  if (counting.value) return
  if (!/^1\d{10}$/.test(form.phone)) {
    uni.showToast({ title: '请输入正确手机号', icon: 'none' })
    return
  }
  try {
    await sendSmsCode(form.phone, 'login')
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    startCount()
  } catch (e) {
    // ignore
  }
}

async function onLogin() {
  if (!agreed.value) {
    uni.showToast({ title: '请先同意用户协议', icon: 'none' })
    return
  }
  if (!/^1\d{10}$/.test(form.phone)) {
    uni.showToast({ title: '请输入正确手机号', icon: 'none' })
    return
  }
  if (loginType.value === 'password' && !form.password) {
    uni.showToast({ title: '请输入密码', icon: 'none' })
    return
  }
  if (loginType.value === 'sms' && !form.code) {
    uni.showToast({ title: '请输入验证码', icon: 'none' })
    return
  }

  loading.value = true
  try {
    if (loginType.value === 'password') {
      await userStore.login({ account: form.phone, password: form.password, loginType: 'password' })
    } else {
      await userStore.loginSms(form.phone, form.code)
    }
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      if (redirect.value) {
        uni.redirectTo({ url: redirect.value, fail: () => uni.switchTab({ url: '/src/pages/home/index' }) })
      } else {
        uni.switchTab({ url: '/src/pages/home/index' })
      }
    }, 800)
  } catch (e) {
    // 已在 request 中提示
  } finally {
    loading.value = false
  }
}

function goRegister() {
  uni.navigateTo({ url: '/src/pages-account/register/index' })
}

function goReset() {
  uni.showModal({
    title: '重置密码',
    content: '将通过短信验证码重置密码，是否继续？',
    success: (res) => {
      if (res.confirm) {
        uni.showToast({ title: '请前往「设置-修改密码」', icon: 'none' })
      }
    },
  })
}

function openAgreement() {
  uni.navigateTo({ url: '/src/pages-ai/assistant/index' })
}

function openPrivacy() {
  uni.navigateTo({ url: '/src/pages-ai/assistant/index' })
}

onLoad((options) => {
  if (options?.redirect) {
    redirect.value = decodeURIComponent(options.redirect)
  }
})
</script>

<style lang="scss" scoped>
.login {
  min-height: 100vh;
  position: relative;

  &__bg {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 480rpx;
    background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  }

  &__content {
    position: relative;
    z-index: 1;
    padding: $spacing-xl $spacing-lg;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding-top: 120rpx;
  }

  &__logo {
    width: 140rpx;
    height: 140rpx;
    border-radius: $radius-xl;
    background: rgba(255, 255, 255, 0.25);
    display: flex;
    align-items: center;
    justify-content: center;
    backdrop-filter: blur(10rpx);
  }

  &__logo-text {
    color: #fff;
    font-size: 56rpx;
    font-weight: 800;
  }

  &__title {
    color: #fff;
    font-size: $font-xxl;
    font-weight: 700;
    margin-top: $spacing-base;
  }

  &__subtitle {
    color: rgba(255, 255, 255, 0.9);
    font-size: $font-sm;
    margin-top: $spacing-xs;
  }

  &__tabs {
    display: flex;
    gap: $spacing-xl;
    margin: $spacing-xl 0 $spacing-base;
    background: #fff;
    border-radius: $radius-lg;
    padding: $spacing-sm $spacing-base;
    width: 100%;
    box-sizing: border-box;
  }

  &__tab {
    flex: 1;
    text-align: center;
    padding: $spacing-sm 0;
    font-size: $font-base;
    color: $text-secondary;
    border-radius: $radius-base;

    &--active {
      background: $primary-bg;
      color: $primary;
      font-weight: 600;
    }
  }

  &__form {
    width: 100%;
    background: #fff;
    border-radius: $radius-lg;
    padding: $spacing-base;
    box-shadow: $shadow-base;
  }

  &__field {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    border-bottom: 1rpx solid $border-color-light;
    padding: $spacing-base 0;

    &:last-child {
      border-bottom: none;
    }
  }

  &__field-icon {
    font-size: 36rpx;
    width: 48rpx;
    text-align: center;
  }

  &__input {
    flex: 1;
    font-size: $font-base;
  }

  &__field-action {
    color: $primary;
    font-size: $font-sm;
    padding: 0 $spacing-sm;

    &--disabled {
      color: $text-placeholder;
    }
  }

  &__btn {
    width: 100%;
    margin-top: $spacing-lg;
    background: linear-gradient(135deg, $primary, $primary-light);
    color: #fff;
    border: none;
    border-radius: 999rpx;
    height: 88rpx;
    line-height: 88rpx;
    font-size: $font-md;
    font-weight: 600;
  }

  &__footer {
    width: 100%;
    display: flex;
    justify-content: space-between;
    margin-top: $spacing-base;
  }

  &__link {
    color: $primary;
    font-size: $font-sm;
  }

  &__agreement {
    width: 100%;
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-top: $spacing-lg;
  }

  &__check {
    width: 32rpx;
    height: 32rpx;
    border: 2rpx solid $border-color;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    &--active {
      background: $primary;
      border-color: $primary;
    }
  }

  &__check-icon {
    color: #fff;
    font-size: 20rpx;
  }
}
</style>
