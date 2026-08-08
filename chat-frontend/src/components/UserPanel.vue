<script setup>
import { ref, onBeforeUnmount } from 'vue'
import QRCode from 'qrcode'
import { startLogin, loginStatus, deleteSession } from '../api/auth.js'

const props = defineProps({
  sessions: Array,
  activeId: String
})
const emit = defineEmits(['select', 'created', 'deleted'])

const creating = ref(false)
const delTarget = ref(null)   // 待删除的账户
const label = ref('')
const qrImg = ref('')
const waiting = ref(false)
const error = ref('')
const currentLogin = ref('')  // 当前创建中的登录会话 id（用于取消）
let timer = null

function openCreate() {
  creating.value = true
  label.value = ''
  qrImg.value = ''
  error.value = ''
  currentLogin.value = ''
}

async function doLogin() {
  error.value = ''
  waiting.value = true
  try {
    const resp = await startLogin(label.value.trim() || '新账户')
    currentLogin.value = resp.sessionId
    const qr = await QRCode.toDataURL(resp.qrContent, { width: 200, margin: 1 })
    qrImg.value = qr
    timer = setInterval(() => poll(resp.sessionId), 2000)
  } catch (e) {
    error.value = '发起登录失败：' + e.message
    waiting.value = false
  }
}

async function poll(sessionId) {
  try {
    const st = await loginStatus(sessionId)
    if (st.loggedIn) {
      clearInterval(timer)
      timer = null
      waiting.value = false
      creating.value = false
      currentLogin.value = ''   // 已登录成功，不再取消
      emit('created')
    } else if (st.status === 'NOT_FOUND') {
      clearInterval(timer)
      timer = null
      waiting.value = false
      error.value = '会话不存在，请重试'
      currentLogin.value = ''
    }
  } catch (e) { /* 继续轮询 */ }
}

// 关闭/取消创建：清轮询 + 删除后端等待中的登录会话，避免残留干扰下次创建
async function cancelCreate() {
  if (timer) { clearInterval(timer); timer = null; }
  const sid = currentLogin.value
  currentLogin.value = ''
  creating.value = false
  qrImg.value = ''
  waiting.value = false
  if (sid) {
    try { await deleteSession(sid); } catch (e) { /* 忽略 */ }
  }
}

function askDelete(sessionId) {
  delTarget.value = props.sessions.find(x => x.sessionId === sessionId) || null
}

async function confirmDelete() {
  if (!delTarget.value) return
  try {
    await deleteSession(delTarget.value.sessionId)
    emit('deleted', delTarget.value.sessionId)
    delTarget.value = null
  } catch (e) {
    alert('删除失败：' + e.message)
  }
}

onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="panel user-panel">
    <div class="panel-title">👥 账户</div>

    <div class="user-list">
      <div
        v-for="s in sessions"
        :key="s.sessionId"
        class="user-item"
        :class="{ active: s.sessionId === activeId }"
        @click="emit('select', s.sessionId)"
      >
        <span class="u-name">{{ s.label || s.shortId }}</span>
        <span class="u-right">
          <span class="u-status" :class="s.status === 'LOGGED_IN' ? 'ok' : 'off'">
            {{ s.status === 'LOGGED_IN' ? '●' : '○' }}
          </span>
          <button class="u-del" title="删除账户" @click.stop="askDelete(s.sessionId)">✕</button>
        </span>
      </div>
      <div v-if="!sessions.length" class="empty">暂无账户，点击下方创建</div>
    </div>

    <button class="btn-create" @click="openCreate">＋ 创建账户</button>

    <!-- 创建账户蒙版（盖住左上区块） -->
    <div v-if="creating" class="panel-mask">
      <div class="create-card">
        <h4>创建账户</h4>
        <input v-model="label" placeholder="账户名字（如：我的微信）" :disabled="waiting" />
        <button class="btn-primary" @click="doLogin" :disabled="waiting">
          {{ waiting ? '等待扫码...' : '生成二维码' }}
        </button>
        <div v-if="qrImg" class="qr-box">
          <img :src="qrImg" alt="微信登录二维码" />
          <p>用微信扫一扫，确认登录</p>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <button class="btn-close" @click="cancelCreate">关闭</button>
      </div>
    </div>

    <!-- 删除确认蒙版（盖住左上区块） -->
    <div v-if="delTarget" class="panel-mask">
      <div class="create-card del-card">
        <h4>删除账户</h4>
        <p class="del-tip">确定删除账户 <b>{{ delTarget.label || delTarget.shortId }}</b> 吗？删除后无法恢复。</p>
        <div class="del-actions">
          <button class="btn-close" @click="delTarget = null">取消</button>
          <button class="btn-danger" @click="confirmDelete">确认删除</button>
        </div>
      </div>
    </div>
  </div>
</template>
