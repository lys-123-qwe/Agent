<script setup>
import { ref, onMounted } from 'vue'
import { listSessions, deleteSession } from '../api/auth.js'

const emit = defineEmits(['back', 'loggedIn'])

const sessions = ref([])
const msg = ref('')

onMounted(load)

async function load() {
  try {
    sessions.value = await listSessions()
  } catch (e) {
    msg.value = '加载失败：' + e.message
  }
}

async function del(sessionId) {
  if (!confirm('确认删除该账户？')) return
  try {
    await deleteSession(sessionId)
    msg.value = '已删除'
    await load()
  } catch (e) {
    msg.value = '删除失败：' + e.message
  }
}

function goLogin() {
  emit('add')
}
</script>

<template>
  <div class="accounts-view">
    <div class="acc-header">
      <h2>📋 账户管理</h2>
      <div>
        <button class="btn-secondary" @click="emit('back')">← 返回</button>
        <button class="btn-primary" @click="goLogin">+ 添加账户</button>
      </div>
    </div>

    <div class="acc-list">
      <div v-for="s in sessions" :key="s.sessionId" class="acc-item">
        <div class="acc-info">
          <strong>{{ s.label }}</strong>
          <span class="acc-id">{{ s.shortId }}</span>
        </div>
        <span class="acc-status" :class="s.listening ? 'ok' : 'off'">
          {{ s.listening ? '● 在线' : s.status }}
        </span>
        <button class="btn-del" @click="del(s.sessionId)">删除</button>
      </div>
      <p v-if="!sessions.length" class="empty">暂无账户，点击「+ 添加账户」扫码登录</p>
    </div>

    <p v-if="msg" class="msg">{{ msg }}</p>
  </div>
</template>
