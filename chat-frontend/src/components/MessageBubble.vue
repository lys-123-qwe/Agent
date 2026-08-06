<script setup>
import { ref, computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/common'

const props = defineProps({
  message: Object,
  thinking: Boolean
})

const copied = ref(false)

const emit = defineEmits(['image-click'])

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' + hljs.highlight(str, { language: lang, ignoreIllegals: true }).value + '</code></pre>'
      } catch (e) { /* 忽略 */ }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  }
})

function renderMd(content) {
  return md.render(content || '')
}

function fmtTime(d) {
  if (!d) return ''
  const t = typeof d === 'string' ? new Date(d) : d
  return t.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

async function copyText() {
  try {
    await navigator.clipboard.writeText(props.message.content)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch (e) { /* 剪贴板不可用时忽略 */ }
}

const isStreamingEmpty = computed(() =>
  props.thinking && props.message.role === 'assistant' && !props.message.content
)
</script>

<template>
  <div class="msg" :class="message.role">
    <div class="avatar">{{ message.role === 'user' ? '👤' : '🤖' }}</div>
    <div class="msg-body">
      <div class="bubble" :class="{ typing: thinking }">
        <template v-if="message.images && message.images.length">
          <img
            v-for="(img, idx) in message.images"
            :key="idx"
            :src="img"
            class="msg-image"
            alt="图表"
            @click="emit('image-click', img)"
          />
        </template>
        <img
          v-else-if="message.image"
          :src="message.image"
          class="msg-image"
          alt="图表"
          @click="emit('image-click', message.image)"
        />
        <div
          v-if="message.role === 'assistant' && !isStreamingEmpty"
          class="md-body"
          v-html="renderMd(message.content)"
        ></div>
        <div v-else class="plain">
          {{ message.content }}<span v-if="isStreamingEmpty" class="typing-cursor">▍</span>
        </div>
      </div>
      <div v-if="message.time || message.role === 'assistant'" class="msg-meta">
        <span v-if="message.time" class="time">{{ fmtTime(message.time) }}</span>
        <button
          v-if="message.role === 'assistant' && message.content"
          class="copy-btn"
          @click="copyText"
        >{{ copied ? '✓ 已复制' : '复制' }}</button>
      </div>
    </div>
  </div>
</template>
