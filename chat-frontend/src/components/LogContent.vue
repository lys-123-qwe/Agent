<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  logs: Array
})
const emit = defineEmits(['clear'])

const scrollRef = ref(null)

// 新日志追加在底部，自动滚动到底部
watch(() => props.logs, async () => {
  await nextTick()
  if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
}, { deep: true })

function fmt(ts) {
  const d = new Date(ts)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
function kindIcon(l) {
  if (l.kind === 'req') return '🔄'
  if (l.kind === 'tool') return '🧰'
  if (l.kind === 'reply') return '💬'
  return '❌'
}
function statusClass(s) { return s === 'OK' ? 'ok' : s === 'FAIL' ? 'fail' : 'run' }
function statusText(s) { return s === 'OK' ? '✓ 成功' : s === 'FAIL' ? '✗ 失败' : '处理中' }
</script>

<template>
  <div class="log-content" ref="scrollRef">
    <div class="log-header">
      <span class="log-title">🔍 运行日志</span>
      <button v-if="logs.length" class="btn-clear-log" @click="emit('clear')">清空</button>
    </div>

    <div v-if="!logs.length" class="empty">暂无运行记录。去对话区问一个问题，这里会实时显示请求的模型、调用的工具、响应内容与状态。</div>

    <div v-for="(l, i) in logs" :key="i" class="log-item">
      <span class="log-icon">{{ kindIcon(l) }}</span>
      <div class="log-main">
        <div class="log-line1">
          <span class="log-time">{{ fmt(l.ts) }}</span>
          <span class="log-model">{{ l.model }}</span>
          <span class="log-status" :class="statusClass(l.status)">{{ statusText(l.status) }}</span>
        </div>
        <div class="log-msg">{{ l.msg }}</div>
        <div v-if="l.detail" class="log-detail">{{ l.detail }}</div>
      </div>
    </div>
  </div>
</template>
