<template>
  <view class="ai-chat">
    <!-- 消息列表 -->
    <scroll-view
      scroll-y
      class="ai-chat__messages"
      :scroll-into-view="scrollInto"
      :scroll-with-animation="true"
    >
      <view class="ai-chat__welcome">
        <view class="ai-chat__avatar ai-chat__avatar--ai">AI</view>
        <view class="ai-chat__welcome-text">
          <text class="fw-bold">您好，我是链生通 AI 客服助手</text>
          <text class="fs-sm text-secondary">可以为您解答 LSC、订单、支付、实名认证等问题</text>
        </view>
      </view>

      <!-- 快捷问题 -->
      <view v-if="!messages.length" class="ai-chat__quick">
        <text class="fs-sm text-secondary">猜你想问：</text>
        <view class="ai-chat__quick-list">
          <text
            v-for="(q, idx) in quickQuestions"
            :key="idx"
            class="ai-chat__quick-item"
            @click="sendQuick(q)"
          >{{ q }}</text>
        </view>
      </view>

      <view
        v-for="(msg, idx) in messages"
        :key="idx"
        class="ai-chat__msg"
        :class="msg.role === 'user' ? 'ai-chat__msg--user' : 'ai-chat__msg--ai'"
      >
        <view class="ai-chat__avatar" :class="msg.role === 'user' ? 'ai-chat__avatar--user' : 'ai-chat__avatar--ai'">
          <text>{{ msg.role === 'user' ? '我' : 'AI' }}</text>
        </view>
        <view class="ai-chat__bubble">
          <text>{{ msg.content }}</text>
        </view>
      </view>

      <view v-if="thinking" class="ai-chat__msg ai-chat__msg--ai">
        <view class="ai-chat__avatar ai-chat__avatar--ai"><text>AI</text></view>
        <view class="ai-chat__bubble ai-chat__bubble--typing">
          <text class="ai-chat__dot"></text>
          <text class="ai-chat__dot"></text>
          <text class="ai-chat__dot"></text>
        </view>
      </view>

      <view id="ai-chat-bottom" style="height: 20rpx"></view>
    </scroll-view>

    <!-- 输入栏 -->
    <view class="ai-chat__input footer-bar">
      <input
        class="ai-chat__input-box"
        v-model="input"
        placeholder="请输入您的问题"
        confirm-type="send"
        @confirm="onSend"
      />
      <button class="ai-chat__send" :disabled="!input.trim() || thinking" @click="onSend">发送</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { chatWithAi, getAiQuickQuestions, type ChatMessage } from '@/api'

const messages = ref<ChatMessage[]>([])
const input = ref('')
const thinking = ref(false)
const scrollInto = ref('')
const quickQuestions = ref<string[]>([
  '什么是 LSC？',
  '如何进行实名认证？',
  '混合支付怎么计算？',
  '订单退款多久到账？',
  '如何获得推广奖励？',
])

async function loadQuick() {
  try {
    const qs = await getAiQuickQuestions()
    if (qs?.length) quickQuestions.value = qs
  } catch (e) {
    // ignore
  }
}

function scrollToBottom() {
  nextTick(() => {
    scrollInto.value = ''
    nextTick(() => {
      scrollInto.value = 'ai-chat-bottom'
    })
  })
}

async function sendQuick(q: string) {
  input.value = q
  await onSend()
}

async function onSend() {
  const text = input.value.trim()
  if (!text || thinking.value) return

  messages.value.push({ role: 'user', content: text, createTime: now() })
  input.value = ''
  scrollToBottom()

  thinking.value = true
  try {
    const res = await chatWithAi({
      message: text,
      history: messages.value.slice(-10),
    })
    messages.value.push({
      role: 'assistant',
      content: res.reply,
      createTime: res.createTime || now(),
    })
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，服务开小差了，请稍后再试或联系人工客服 🎧',
      createTime: now(),
    })
  } finally {
    thinking.value = false
    scrollToBottom()
  }
}

function now(): string {
  const d = new Date()
  const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(loadQuick)
</script>

<style lang="scss" scoped>
.ai-chat {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: $bg-page;

  &__messages {
    flex: 1;
    height: 0;
    padding: $spacing-base;
  }

  &__welcome {
    display: flex;
    gap: $spacing-base;
    align-items: center;
    background: #fff;
    border-radius: $radius-lg;
    padding: $spacing-base;
    margin-bottom: $spacing-base;
  }

  &__welcome-text {
    display: flex;
    flex-direction: column;
    gap: 4rpx;
  }

  &__quick {
    background: #fff;
    border-radius: $radius-lg;
    padding: $spacing-base;
    margin-bottom: $spacing-base;
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__quick-list {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-sm;
  }

  &__quick-item {
    font-size: $font-sm;
    padding: $spacing-xs $spacing-base;
    background: $primary-bg;
    color: $primary;
    border-radius: 999rpx;
  }

  &__msg {
    display: flex;
    gap: $spacing-sm;
    margin-bottom: $spacing-base;
    align-items: flex-start;

    &--user {
      flex-direction: row-reverse;
    }
  }

  &__avatar {
    width: 64rpx;
    height: 64rpx;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: $font-xs;
    font-weight: 700;
    flex-shrink: 0;

    &--ai {
      background: linear-gradient(135deg, $lsc-color, $lsc-color-light);
      color: #fff;
    }

    &--user {
      background: linear-gradient(135deg, $primary, $primary-light);
      color: #fff;
    }
  }

  &__bubble {
    max-width: 70%;
    background: #fff;
    padding: $spacing-sm $spacing-base;
    border-radius: $radius-lg;
    font-size: $font-base;
    color: $text-primary;
    line-height: 1.5;
    box-shadow: $shadow-sm;

    .ai-chat__msg--user & {
      background: linear-gradient(135deg, $primary, $primary-light);
      color: #fff;
      border-top-right-radius: 4rpx;
    }

    .ai-chat__msg--ai & {
      border-top-left-radius: 4rpx;
    }

    &--typing {
      display: flex;
      gap: 8rpx;
      align-items: center;
    }
  }

  &__dot {
    width: 12rpx;
    height: 12rpx;
    border-radius: 50%;
    background: $text-placeholder;
    animation: ai-bounce 1.2s infinite;

    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }

  &__input {
    gap: $spacing-sm;
  }

  &__input-box {
    flex: 1;
    height: 72rpx;
    background: $bg-gray;
    border-radius: 999rpx;
    padding: 0 $spacing-base;
    font-size: $font-base;
  }

  &__send {
    background: linear-gradient(135deg, $primary, $primary-light);
    color: #fff;
    border: none;
    border-radius: 999rpx;
    height: 72rpx;
    line-height: 72rpx;
    padding: 0 $spacing-lg;
    font-size: $font-base;
    margin: 0;

    &[disabled] {
      opacity: 0.5;
    }
  }
}

@keyframes ai-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
  30% { transform: translateY(-8rpx); opacity: 1; }
}
</style>
