<template>
  <view class="count-down">
    <slot :text="text" :remain="remain">
      <text>{{ text }}</text>
    </slot>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 结束时间戳（ms） */
    endTime: number
    /** 当前时间戳基准（用于服务端时间同步，默认本地） */
    now?: number
  }>(),
  {},
)

const emit = defineEmits<{ (e: 'finish'): void }>()

const remain = ref<number>(0)
const text = ref<string>('00:00')
let timer: ReturnType<typeof setInterval> | null = null

function tick() {
  const end = props.endTime
  const diff = end - Date.now()
  if (diff <= 0) {
    remain.value = 0
    text.value = '00:00'
    stop()
    emit('finish')
    return
  }
  remain.value = diff
  const totalSec = Math.floor(diff / 1000)
  const min = Math.floor(totalSec / 60)
  const sec = totalSec % 60
  const hour = Math.floor(min / 60)
  if (hour > 0) {
    text.value = `${pad(hour)}:${pad(min % 60)}:${pad(sec)}`
  } else {
    text.value = `${pad(min)}:${pad(sec)}`
  }
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : `${n}`
}

function start() {
  tick()
  timer = setInterval(tick, 1000)
}

function stop() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onMounted(start)
onUnmounted(stop)
</script>

<style lang="scss" scoped>
.count-down {
  display: inline-flex;
  align-items: center;
}
</style>
