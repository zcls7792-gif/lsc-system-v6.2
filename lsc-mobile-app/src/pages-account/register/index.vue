<template>
  <view class="register">
    <view class="register__form card">
      <view class="register__field">
        <text class="register__label">手机号</text>
        <input class="register__input" v-model="form.phone" type="number" maxlength="11" placeholder="请输入手机号" />
      </view>

      <view class="register__field">
        <text class="register__label">验证码</text>
        <input class="register__input" v-model="form.code" type="number" maxlength="6" placeholder="短信验证码" />
        <text
          class="register__code-btn"
          :class="{ 'register__code-btn--disabled': counting }"
          @click="sendCode"
        >{{ counting ? `${count}s` : '获取验证码' }}</text>
      </view>

      <view class="register__field">
        <text class="register__label">登录密码</text>
        <input class="register__input" v-model="form.password" password placeholder="6-20位密码" />
      </view>

      <view class="register__field">
        <text class="register__label">确认密码</text>
        <input class="register__input" v-model="form.confirmPassword" password placeholder="再次输入密码" />
      </view>

      <view class="register__field">
        <text class="register__label">推荐人</text>
        <input class="register__input" v-model="form.referrerPhone" type="number" maxlength="11" placeholder="推荐人手机号（选填）" />
      </view>
    </view>

    <view class="register__agreement">
      <view class="register__check" :class="{ 'register__check--active': agreed }" @click="agreed = !agreed">
        <text v-if="agreed" class="register__check-icon">✓</text>
      </view>
      <text class="fs-sm text-secondary">我已阅读并同意《用户协议》《隐私政策》</text>
    </view>

    <button class="register__btn" :loading="loading" @click="onRegister">注 册</button>

    <text class="register__login" @click="goLogin">已有账号？返回登录</text>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useUserStore } from '@/stores/user'
import { sendSmsCode } from '@/api/user'

const userStore = useUserStore()
const loading = ref(false)
const agreed = ref(false)
const counting = ref(false)
const count = ref(60)

const form = reactive({
  phone: '',
  code: '',
  password: '',
  confirmPassword: '',
  referrerPhone: '',
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
    await sendSmsCode(form.phone, 'register')
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    startCount()
  } catch (e) {
    // ignore
  }
}

async function onRegister() {
  if (!agreed.value) {
    uni.showToast({ title: '请先同意用户协议', icon: 'none' })
    return
  }
  if (!/^1\d{10}$/.test(form.phone)) {
    uni.showToast({ title: '请输入正确手机号', icon: 'none' })
    return
  }
  if (!form.code) {
    uni.showToast({ title: '请输入验证码', icon: 'none' })
    return
  }
  if (form.password.length < 6 || form.password.length > 20) {
    uni.showToast({ title: '密码6-20位', icon: 'none' })
    return
  }
  if (form.password !== form.confirmPassword) {
    uni.showToast({ title: '两次密码不一致', icon: 'none' })
    return
  }

  loading.value = true
  try {
    await userStore.register({
      phone: form.phone,
      password: form.password,
      code: form.code,
      referrerPhone: form.referrerPhone || undefined,
    })
    uni.showToast({ title: '注册成功', icon: 'success' })
    setTimeout(() => uni.switchTab({ url: '/src/pages/home/index' }), 800)
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function goLogin() {
  uni.navigateBack({ fail: () => uni.redirectTo({ url: '/src/pages-account/login/index' }) })
}
</script>

<style lang="scss" scoped>
.register {
  padding: $spacing-base;
  min-height: 100vh;

  &__form {
    margin-bottom: $spacing-base;
  }

  &__field {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    padding: $spacing-base 0;
    border-bottom: 1rpx solid $border-color-light;

    &:last-child {
      border-bottom: none;
    }
  }

  &__label {
    width: 140rpx;
    font-size: $font-base;
    color: $text-regular;
    flex-shrink: 0;
  }

  &__input {
    flex: 1;
    font-size: $font-base;
  }

  &__code-btn {
    color: $primary;
    font-size: $font-sm;
    padding: 0 $spacing-sm;
    border-left: 1rpx solid $border-color;
    height: 40rpx;
    line-height: 40rpx;

    &--disabled {
      color: $text-placeholder;
    }
  }

  &__agreement {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: 0 $spacing-sm $spacing-base;
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

  &__btn {
    width: 100%;
    background: linear-gradient(135deg, $primary, $primary-light);
    color: #fff;
    border: none;
    border-radius: 999rpx;
    height: 88rpx;
    line-height: 88rpx;
    font-size: $font-md;
    font-weight: 600;
  }

  &__login {
    display: block;
    text-align: center;
    color: $primary;
    font-size: $font-sm;
    margin-top: $spacing-base;
  }
}
</style>
