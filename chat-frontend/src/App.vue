<script setup>
import { ref, onMounted } from 'vue'
import ChatWindow from './components/ChatWindow.vue'
import InputBar from './components/InputBar.vue'
import LoginView from './components/LoginView.vue'
import AccountsView from './components/AccountsView.vue'
import { chatStream } from './api/stream.js'
import { listSessions } from './api/auth.js'

const view = ref('login') // login | chat | accounts
const messages = ref([
  { role: 'assistant', content: '你好！我是你的智能助手，可以查股票、看天气、搜新闻等，有什么可以帮你？' }
])
const loading = ref(false)

onMounted(async () => {
  try {
    const sessions = await listSessions()
    const logged = sessions.some(s => s.listening || s.status === 'LOGGED_IN')
    view.value = logged ? 'chat' : 'login'
  } catch (e) {
    view.value = 'login'
  }
})

function onLoginDone() { view.value = 'chat' }
function showAccounts() { view.value = 'accounts' }
function showLogin() { view.value = 'login' }
function backToChat() { view.value = 'chat' }

async function send(text) {
  if (!text.trim() || loading.value) return

  messages.value.push({ role: 'user', content: text })
  const aiMsg = ref({ role: 'assistant', content: '' })
  messages.value.push(aiMsg.value)
  loading.value = true

  try {
    await chatStream(text, (delta) => {
      aiMsg.value.content += delta
    })
  } catch (e) {
    aiMsg.value.content = '⚠️ 请求失败：' + e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="chat-app">
    <header class="chat-header">
      <h1>🤖 Claw 智能助手</h1>
      <div class="header-actions">
        <span v-if="view === 'chat'" class="status" :class="{ online: !loading }">
          {{ loading ? '思考中...' : '● 在线' }}
        </span>
        <button v-if="view === 'chat'" class="btn-accounts" @click="showAccounts">账户管理</button>
      </div>
    </header>

    <LoginView v-if="view === 'login'" @done="onLoginDone" />
    <AccountsView v-else-if="view === 'accounts'" @back="backToChat" @add="showLogin" />
    <template v-else>
      <ChatWindow :messages="messages" :loading="loading" />
      <InputBar :loading="loading" @send="send" />
    </template>
  </div>
</template>
