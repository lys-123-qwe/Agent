<script setup>
import { ref, watch, nextTick } from 'vue'
import MessageBubble from './MessageBubble.vue'

const props = defineProps({
  messages: Array,
  loading: Boolean
})

const scrollRef = ref(null)

// 智能滚动：仅当用户本就贴近底部时才自动跟随到底；
// 用户上滑查看历史时不再强制拉回
watch(() => props.messages, async () => {
  await nextTick()
  const el = scrollRef.value
  if (!el) return
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 100
  if (nearBottom) el.scrollTop = el.scrollHeight
}, { deep: true })
</script>

<template>
  <div class="chat-window" ref="scrollRef">
    <MessageBubble
      v-for="(m, i) in messages"
      :key="i"
      :message="m"
      :thinking="loading && i === messages.length - 1 && m.role === 'assistant' && m.content === ''"
    />
  </div>
</template>
