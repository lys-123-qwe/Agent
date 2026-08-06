<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  loading: Boolean
})
const emit = defineEmits(['send', 'send-image', 'send-file'])

const text = ref('')
const fileInput = ref(null)
const attach = ref(null)  // { type, name, file, dataUrl }
const menuOpen = ref(false)

function onDocClick() { menuOpen.value = false }
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

function pick(type) {
  menuOpen.value = false
  fileInput.value.accept = type === 'image' ? 'image/*' : ''
  fileInput.value.click()
}

function onFileChange(e) {
  const f = e.target.files[0]
  e.target.value = ''
  if (!f) return
  if (f.type.startsWith('image/')) {
    const reader = new FileReader()
    reader.onload = () => {
      attach.value = { type: 'image', name: f.name, file: f, dataUrl: reader.result }
    }
    reader.readAsDataURL(f)
  } else {
    attach.value = { type: 'file', name: f.name, file: f, dataUrl: null }
  }
}

function removeAttach() { attach.value = null }

function onSend() {
  if (props.loading) return
  if (attach.value) {
    if (attach.value.type === 'image') emit('send-image', attach.value.file)
    else emit('send-file', attach.value.file)
    attach.value = null
    return
  }
  if (!text.value.trim()) return
  emit('send', text.value)
  text.value = ''
}
</script>

<template>
  <div class="input-bar">
    <!-- ＋ 号：点开选图片/文件（微信样式） -->
    <div class="plus-wrap" @click.stop>
      <button class="plus-btn" :class="{ open: menuOpen }" title="添加" @click="menuOpen = !menuOpen">＋</button>
      <div v-if="menuOpen" class="plus-menu">
        <div class="plus-item" @click="pick('image')">🖼️ 图片</div>
        <div class="plus-item" @click="pick('file')">📎 文件</div>
      </div>
    </div>
    <input ref="fileInput" type="file" hidden @change="onFileChange" />

    <div class="input-wrap">
      <div v-if="attach" class="attach-preview">
        <img v-if="attach.type === 'image'" :src="attach.dataUrl" alt="预览" />
        <span v-else class="attach-file">📄 {{ attach.name }}</span>
        <button class="attach-del" title="移除" @click="removeAttach">✕</button>
      </div>
      <input
        v-model="text"
        placeholder="输入消息，或点 ＋ 发送图片/文件"
        :disabled="loading"
        @keyup.enter="onSend"
      />
    </div>

    <button class="send-btn" :disabled="loading" @click="onSend">发送</button>
  </div>
</template>
