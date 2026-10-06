<template>
  <div class="login-page">
    <van-nav-bar title="登录" />
    <div class="login-logo">
      <van-icon name="gold-pay" size="64" color="#ff976a" />
      <h2>链盛通</h2>
      <p>消费回馈权益平台</p>
    </div>

    <van-form @submit="onLogin">
      <van-cell-group inset>
        <van-field
          v-model="phone"
          name="phone"
          label="手机号"
          placeholder="请输入手机号"
          :rules="[{ required: true, message: '请输入手机号' }]"
        />
        <van-field
          v-model="code"
          name="code"
          label="验证码"
          placeholder="开发期验证码: 1234"
          :rules="[{ required: true, message: '请输入验证码' }]"
        >
          <template #button>
            <van-button size="small" type="primary" @click.prevent="onSendCode">
              发送验证码
            </van-button>
          </template>
        </van-field>
      </van-cell-group>
      <div class="login-submit">
        <van-button round block type="primary" native-type="submit" :loading="loading">
          登录
        </van-button>
      </div>
    </van-form>

    <p class="login-tip">开发期：手机号任意，验证码固定 1234</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { loginBySms } from '@/api/auth'

const router = useRouter()
const phone = ref('')
const code = ref('')
const loading = ref(false)

function onSendCode() {
  if (!phone.value) {
    showToast('请输入手机号')
    return
  }
  showToast({ message: '验证码已发送（开发期: 1234）', type: 'success' })
}

async function onLogin() {
  loading.value = true
  try {
    const res = await loginBySms(phone.value, code.value)
    localStorage.setItem('token', res.token)
    localStorage.setItem('userId', String(res.userId))
    showToast({ message: '登录成功', type: 'success' })
    router.replace('/mine')
  } catch (e: any) {
    // 错误已在拦截器 toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #f7f8fa;
}
.login-logo {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 0 24px;
}
.login-logo h2 {
  margin: 12px 0 4px;
  font-size: 24px;
  color: #323233;
}
.login-logo p {
  margin: 0;
  color: #969799;
  font-size: 13px;
}
.login-submit {
  margin: 24px 16px 0;
}
.login-tip {
  text-align: center;
  margin-top: 24px;
  color: #c8c9cc;
  font-size: 12px;
}
</style>
