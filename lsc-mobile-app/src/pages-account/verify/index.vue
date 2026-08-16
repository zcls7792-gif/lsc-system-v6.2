<template>
  <view class="verify">
    <view class="verify__tips card">
      <text class="fw-bold">实名认证</text>
      <text class="fs-sm text-secondary">为保障账户资金安全，使用 LSC 支付、提现等功能前需完成实名认证。信息仅用于身份核验，不会公开。</text>
    </view>

    <view v-if="profile?.verifyStatus === 1" class="verify__result card verify__result--ok">
      <text class="verify__result-icon">✓</text>
      <view>
        <text class="fw-bold text-success">已完成实名认证</text>
        <text class="fs-sm text-secondary">{{ profile.realName }} · {{ maskedIdCard }}</text>
      </view>
    </view>

    <view v-else-if="profile?.verifyStatus === 2" class="verify__result card verify__result--pending">
      <text class="verify__result-icon">⏳</text>
      <view>
        <text class="fw-bold text-warning">认证审核中</text>
        <text class="fs-sm text-secondary">请耐心等待，通常 1 个工作日内完成</text>
      </view>
    </view>

    <view v-else-if="profile?.verifyStatus === 3" class="verify__result card verify__result--reject">
      <text class="verify__result-icon">!</text>
      <view>
        <text class="fw-bold text-danger">认证未通过</text>
        <text class="fs-sm text-secondary">请核对信息后重新提交</text>
      </view>
    </view>

    <template v-if="profile?.verifyStatus !== 1 && profile?.verifyStatus !== 2">
      <view class="verify__form card">
        <view class="verify__field">
          <text class="verify__label">真实姓名</text>
          <input class="verify__input" v-model="form.realName" placeholder="请输入真实姓名" />
        </view>
        <view class="verify__field">
          <text class="verify__label">身份证号</text>
          <input class="verify__input" v-model="form.idCard" maxlength="18" placeholder="请输入身份证号" />
        </view>
      </view>

      <!-- 人脸识别 -->
      <view class="verify__face card" @click="onFaceVerify">
        <view class="verify__face-icon">{{ faceVerified ? '🙂' : '📷' }}</view>
        <view class="verify__face-info">
          <text class="fw-bold">人脸识别</text>
          <text class="fs-sm text-secondary">{{ faceVerified ? '已完成采集' : '点击进行人脸识别' }}</text>
        </view>
        <text class="verify__face-arrow">›</text>
      </view>

      <view style="height: 160rpx"></view>

      <view class="verify__footer footer-bar">
        <button class="verify__btn btn-primary" :loading="loading" @click="onSubmit">提交认证</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const profile = computed(() => userStore.profile)
const loading = ref(false)
const faceVerified = ref(false)
const faceToken = ref('')

const form = reactive({
  realName: '',
  idCard: '',
})

const maskedIdCard = computed(() => {
  const id = profile.value?.idCard
  if (!id || id.length < 8) return id || ''
  return id.slice(0, 4) + '********' + id.slice(-4)
})

function onFaceVerify() {
  // #ifdef APP-PLUS || MP-WEIXIN
  uni.showActionSheet({
    itemList: ['拍摄人脸照片', '从相册选择'],
    success: (res) => {
      const source = res.tapIndex === 0 ? 'camera' : 'album'
      uni.chooseImage({
        count: 1,
        sourceType: [source],
        success: () => {
          // 实际项目中应上传至活体检测服务，返回 faceToken
          faceVerified.value = true
          faceToken.value = 'face_token_' + Date.now()
          uni.showToast({ title: '人脸采集成功', icon: 'success' })
        },
      })
    },
  })
  // #endif
  // #ifndef APP-PLUS || MP-WEIXIN
  faceVerified.value = true
  faceToken.value = 'face_token_' + Date.now()
  uni.showToast({ title: '人脸采集成功', icon: 'success' })
  // #endif
}

async function onSubmit() {
  if (!form.realName) {
    uni.showToast({ title: '请输入真实姓名', icon: 'none' })
    return
  }
  if (!/^\d{17}[\dXx]$/.test(form.idCard)) {
    uni.showToast({ title: '请输入正确身份证号', icon: 'none' })
    return
  }
  if (!faceVerified.value) {
    uni.showToast({ title: '请完成人脸识别', icon: 'none' })
    return
  }

  loading.value = true
  try {
    await userStore.doVerify({
      realName: form.realName,
      idCard: form.idCard,
      faceToken: faceToken.value,
    })
    uni.showToast({ title: '提交成功，等待审核', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

onShow(async () => {
  if (userStore.isLoggedIn) {
    try {
      await userStore.fetchProfile()
    } catch (e) {
      // ignore
    }
  }
})
</script>

<style lang="scss" scoped>
.verify {
  padding: $spacing-base;
  min-height: 100vh;

  &__tips {
    display: flex;
    flex-direction: column;
    gap: $spacing-xs;
    margin-bottom: $spacing-base;
  }

  &__result {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    margin-bottom: $spacing-base;

    &--ok { border-left: 8rpx solid $success; }
    &--pending { border-left: 8rpx solid $warning; }
    &--reject { border-left: 8rpx solid $danger; }
  }

  &__result-icon {
    width: 64rpx;
    height: 64rpx;
    border-radius: 50%;
    background: $bg-gray;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 36rpx;
    font-weight: 700;
  }

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
    width: 160rpx;
    font-size: $font-base;
    color: $text-regular;
    flex-shrink: 0;
  }

  &__input {
    flex: 1;
    font-size: $font-base;
  }

  &__face {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    margin-bottom: $spacing-base;
  }

  &__face-icon {
    width: 96rpx;
    height: 96rpx;
    border-radius: $radius-lg;
    background: $primary-bg;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 48rpx;
  }

  &__face-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;
  }

  &__face-arrow {
    color: $text-placeholder;
    font-size: $font-md;
  }

  &__btn {
    width: 100%;
  }
}
</style>
