<script setup>
import { ref, onBeforeUnmount } from 'vue'
import QRCode from 'qrcode'
import { startLogin, loginStatus } from '../api/auth.js'

const emit = defineEmits(['done', 'cancel'])

const label = ref('')
const sessionId = ref('')
const qrImg = ref('')    // 二维码图片（data URL）
const waiting = ref(false)
const error = ref('')
let timer = null

async function doLogin() {
  error.value = ''
  waiting.value = true
  try {
    const resp = await startLogin(label.value || 'web')
    sessionId.value = resp.sessionId
    // qrContent 是一个链接，用 qrcode 库转成二维码图片
    qrImg.value = await QRCode.toDataURL(resp.qrContent, { width: 240, margin: 1 })
    timer = setInterval(poll, 2000)
  } catch (e) {
    error.value = '发起登录失败：' + e.message
    waiting.value = false
  }
}

async function poll() {
  try {
    const st = await loginStatus(sessionId.value)
    if (st.loggedIn) {
      clearInterval(timer)
      timer = null
      waiting.value = false
      emit('done', { sessionId: sessionId.value })
    } else if (st.status === 'NOT_FOUND') {
      clearInterval(timer)
      timer = null
      waiting.value = false
      error.value = '会话不存在，请重新登录'
    }
  } catch (e) {
    // 轮询失败继续等
  }
}

onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="login-view">
    <h2>🔐 扫码登录微信 Bot</h2>
    <p class="tip">登录后即可在网页聊天，并管理你的微信账户</p>

    <div class="login-form">
      <input v-model="label" placeholder="账户标签（可选，默认 web）" :disabled="waiting" />
      <button class="btn-primary" @click="doLogin" :disabled="waiting">
        {{ waiting ? '等待扫码...' : '扫码登录' }}
      </button>
    </div>

    <div v-if="qrImg" class="qr-box">
      <img :src="qrImg" alt="微信登录二维码" />
      <p>请用微信扫一扫，确认登录</p>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>
