<script setup>
defineProps({
  message: Object,
  thinking: Boolean
})
</script>

<template>
  <div class="msg" :class="message.role">
    <div class="bubble" :class="{ typing: thinking }">
      {{ message.content }}<span v-if="thinking" class="typing"></span>
    </div>
  </div>
</template>
