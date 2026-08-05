<script setup>
import { ref, watch, nextTick } from 'vue'
import MessageBubble from './MessageBubble.vue'

const props = defineProps({
  messages: Array,
  loading: Boolean
})

const scrollRef = ref(null)

watch(() => props.messages, async () => {
  await nextTick()
  if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
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
