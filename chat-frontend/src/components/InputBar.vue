<script setup>
import { ref } from 'vue'

const props = defineProps({
  loading: Boolean
})
const emit = defineEmits(['send'])

const text = ref('')

function onSend() {
  if (!text.value.trim() || props.loading) return
  emit('send', text.value)
  text.value = ''
}
</script>

<template>
  <div class="input-bar">
    <input
      v-model="text"
      placeholder="输入消息，按回车发送"
      :disabled="loading"
      @keyup.enter="onSend"
    />
    <button :disabled="loading || !text.trim()" @click="onSend">发送</button>
  </div>
</template>
