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

    <div class="login-divider">— 其他登录方式 —</div>
    <div class="login-wechat">
      <van-button round block icon="chat-o" @click="onWechatLogin">
        微信登录
      </van-button>
    </div>

    <p class="login-tip">开发期：手机号任意，验证码固定 1234</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { loginBySms, getWechatH5AuthUrl } from '@/api/auth'

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

/**
 * 微信 H5 网页授权登录。
 * 开发期（未配置 appid）直接用模拟 code 走回调页，方便本地调试。
 */
async function onWechatLogin() {
  try {
    const callbackUri = window.location.origin + '/wechat/callback'
    const { url } = await getWechatH5AuthUrl(callbackUri, 'redirect=/mine')
    // 检查 appid 是否已配置（URL 中 appid=xxx 不为空）
    const appIdMatch = url.match(/appid=([^&]*)/)
    if (appIdMatch && appIdMatch[1]) {
      window.location.href = url
    } else {
      // 开发期：appid 为空，用模拟 code 直接跳回调页
      showToast('开发期：使用模拟微信 code')
      window.location.href = callbackUri + '?code=dev_wechat_code'
    }
  } catch (e: any) {
    showToast(e?.message || '微信登录失败')
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
.login-divider {
  text-align: center;
  margin: 32px 0 16px;
  color: #c8c9cc;
  font-size: 12px;
}
.login-wechat {
  margin: 0 16px;
}
.login-wechat .van-button {
  background: #07c160;
  border-color: #07c160;
  color: #fff;
}
.login-tip {
  text-align: center;
  margin-top: 24px;
  color: #c8c9cc;
  font-size: 12px;
}
</style>
