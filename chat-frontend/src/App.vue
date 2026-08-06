<script setup>
import { ref, onMounted } from 'vue'
import UserPanel from './components/UserPanel.vue'
import ConfigPanel from './components/ConfigPanel.vue'
import TokenPanel from './components/TokenPanel.vue'
import ChatContent from './components/ChatContent.vue'
import LogContent from './components/LogContent.vue'
import { listSessions } from './api/auth.js'

const sessions = ref([])
const activeId = ref('')
const tab = ref('chat') // chat | log
const logs = ref([])
// 运行日志持久化到 localStorage，刷新后保留
const savedLogs = localStorage.getItem('run_logs')
if (savedLogs) {
  try {
    const arr = JSON.parse(savedLogs)
    if (Array.isArray(arr)) logs.value = arr
  } catch (e) { /* 忽略损坏数据 */ }
}
// 主题：light / dark（手动切换，持久化到 localStorage）
const theme = ref(localStorage.getItem('theme') || 'light')

function applyTheme() {
  localStorage.setItem('theme', theme.value)
  document.documentElement.classList.toggle('dark', theme.value === 'dark')
}

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  applyTheme()
}

async function refreshSessions() {
  try {
    sessions.value = await listSessions()
  } catch (e) {
    sessions.value = []
  }
}

onMounted(() => {
  applyTheme()
  refreshSessions()
})

function onSelect(sessionId) {
  activeId.value = sessionId
}

function onCreated() {
  refreshSessions()
}

function onDeleted(sessionId) {
  if (activeId.value === sessionId) activeId.value = ''
  refreshSessions()
}

function onLog(log) {
  logs.value.push(log)          // 新日志追加到末尾（按流程从上到下）
  if (logs.value.length > 100) logs.value.shift()
  localStorage.setItem('run_logs', JSON.stringify(logs.value))   // 持久化
}

function clearLogs() {
  logs.value = []
  localStorage.removeItem('run_logs')
}
</script>

<template>
  <div class="console">
    <!-- 背景层：可放动图/视频，见 style.css .bg-layer 注释 -->
    <div class="bg-layer"></div>

    <aside class="sidebar">
      <UserPanel :sessions="sessions" :active-id="activeId" @select="onSelect" @created="onCreated" @deleted="onDeleted" />
      <ConfigPanel :user-id="activeId" />
      <TokenPanel :sessions="sessions" />
    </aside>

    <main class="main">
      <div class="tabbar">
        <button :class="{ active: tab === 'chat' }" @click="tab = 'chat'">💬 对话</button>
        <button :class="{ active: tab === 'log' }" @click="tab = 'log'">🔍 运行日志</button>
        <button
          class="theme-toggle"
          :title="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          @click="toggleTheme"
        >{{ theme === 'dark' ? '☀️' : '🌙' }}</button>
      </div>
      <div class="tab-body">
        <!-- v-show 而非 v-if：切换 tab 不销毁组件，保留对话状态 -->
        <ChatContent
          v-show="tab === 'chat'"
          :sessions="sessions"
          :account="activeId"
          @select="onSelect"
          @log="onLog"
        />
        <LogContent v-show="tab !== 'chat'" :logs="logs" @clear="clearLogs" />
      </div>
    </main>
  </div>
</template>
