<script setup lang="ts">
// 商家登录 — 手机号 + 密码
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useMerchantStore } from '@/stores/merchant'

const route = useRoute()
const router = useRouter()
const merchant = useMerchantStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  mobile: '',
  password: ''
})

const rules: FormRules = {
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await login(form)
      merchant.setAuth(res.token, {
        userId: res.merchant.userId,
        mobile: res.merchant.mobile,
        storeName: res.merchant.storeName,
        nickname: res.merchant.nickname,
        avatarUrl: res.merchant.avatarUrl,
        auditStatus: res.merchant.auditStatus,
        isSignedSupervision: res.merchant.isSignedSupervision
      })
      ElMessage.success('登录成功')
      const redirect = (route.query.redirect as string) || '/dashboard'
      router.replace(redirect)
    } catch (e: any) {
      /* 错误已由 request 拦截器提示 */
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login" data-testid="merchant-login-page">
    <div class="login__bg">
      <div class="login__bg-grid"></div>
      <div class="login__bg-glow login__bg-glow--teal"></div>
      <div class="login__bg-glow login__bg-glow--gold"></div>
    </div>

    <div class="login__container">
      <div class="login__brand">
        <div class="login__brand-mark" data-testid="merchant-login-brand-mark">
          <span>L</span>
        </div>
        <div class="login__brand-text">
          <h1 class="login__brand-title" data-testid="merchant-login-brand-title">链盛通</h1>
          <p class="login__brand-sub">LSC 商家管理后台</p>
        </div>
      </div>

      <div class="login__card">
        <div class="login__card-head">
          <h2 class="login__card-title" data-testid="merchant-login-title">商家登录</h2>
          <p class="login__card-desc">输入手机号与密码进入商家管理后台</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          label-position="top"
          @keyup.enter="handleLogin"
          data-testid="merchant-login-form"
        >
          <el-form-item prop="mobile" data-testid="merchant-login-mobile-item">
            <el-input
              v-model="form.mobile"
              placeholder="请输入手机号"
              :prefix-icon="User"
              maxlength="11"
              clearable
              data-testid="merchant-login-mobile-input"
            />
          </el-form-item>

          <el-form-item prop="password" data-testid="merchant-login-password-item">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              data-testid="merchant-login-password-input"
            />
          </el-form-item>

          <el-button
            type="primary"
            class="login__submit"
            :loading="loading"
            @click="handleLogin"
            data-testid="merchant-login-submit-btn"
          >
            登 录
          </el-button>
        </el-form>

        <div class="login__foot">
          <div class="login__tags">
            <span class="login__tag">监管可信</span>
            <span class="login__tag login__tag--gold">AI 审核保障</span>
            <span class="login__tag">链上存证</span>
          </div>
          <p class="login__hint">尚未入驻？请联系平台开通商家账户</p>
        </div>
      </div>

      <p class="login__copy">© 2026 链盛通 LSC · 商家管理后台</p>
    </div>
  </div>
</template>

<style scoped>
.login {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #0b1220;
}

.login__bg {
  position: absolute;
  inset: 0;
  background: radial-gradient(120% 120% at 80% 10%, #0f766e 0%, #0b1220 55%);
}

.login__bg-grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(94, 234, 212, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(94, 234, 212, 0.06) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(120% 80% at 50% 0%, #000 30%, transparent 80%);
}

.login__bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.55;
}

.login__bg-glow--teal {
  width: 480px;
  height: 480px;
  background: #14b8a6;
  top: -120px;
  right: -100px;
}

.login__bg-glow--gold {
  width: 360px;
  height: 360px;
  background: #d97706;
  bottom: -120px;
  left: -80px;
  opacity: 0.35;
}

.login__container {
  position: relative;
  z-index: 1;
  width: 416px;
  max-width: calc(100vw - 32px);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.login__brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}

.login__brand-mark {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, #2dd4bf, #0f766e);
  display: grid;
  place-items: center;
  font-family: var(--lsc-font-display);
  font-weight: 800;
  font-size: 24px;
  color: #fff;
  box-shadow: 0 10px 28px rgba(20, 184, 166, 0.5);
}

.login__brand-title {
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.login__brand-sub {
  color: #94a3b8;
  font-size: 12px;
  letter-spacing: 0.1em;
  margin: 2px 0 0;
}

.login__card {
  width: 100%;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(20px);
  border-radius: 22px;
  padding: 32px 30px 24px;
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.login__card-head {
  margin-bottom: 22px;
}

.login__card-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--lsc-text);
  letter-spacing: -0.01em;
}

.login__card-desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--lsc-text-secondary);
}

.login__submit {
  width: 100%;
  height: 44px;
  margin-top: 6px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.08em;
  background: linear-gradient(135deg, var(--lsc-primary-500), var(--lsc-primary-700));
  border-color: var(--lsc-primary-700);
}

.login__foot {
  margin-top: 18px;
  text-align: center;
}

.login__tags {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.login__tag {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background: var(--lsc-primary-50);
  color: var(--lsc-primary-700);
  border: 1px solid var(--lsc-primary-100);
}

.login__tag--gold {
  background: var(--lsc-gold-50);
  color: var(--lsc-gold-700);
  border-color: var(--lsc-gold-100);
}

.login__hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--lsc-text-placeholder);
}

.login__copy {
  margin-top: 24px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 11.5px;
  letter-spacing: 0.04em;
}

:deep(.el-input__wrapper) {
  border-radius: 10px !important;
  padding: 4px 12px;
}
</style>
