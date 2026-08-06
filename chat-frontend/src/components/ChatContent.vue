<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import MessageBubble from './MessageBubble.vue'
import InputBar from './InputBar.vue'
import ImageViewer from './ImageViewer.vue'
import { chatStream, clearHistory } from '../api/stream.js'

const props = defineProps({
  sessions: Array,
  model: String,
  account: String   // 当前账户（由左侧面板选中，全局唯一）
})
const emit = defineEmits(['log', 'select'])

// selectedId 跟随 account；对话区下拉选择时也同步回 App
const selectedId = ref(props.account || '')
const lastChat = ref(localStorage.getItem('lastChat') || '')   // 最后聊过的账户（刷新后保留）
const loading = ref(false)
const scrollRef = ref(null)
const viewerSrc = ref('')  // 图片预览
const msgMap = reactive({})   // 按账户隔离消息历史

const MAX_PERSIST = 30  // 每个账户持久化最近 30 条

// ---------- 持久化 ----------
function loadMsgs(accountId) {
  try {
    const raw = localStorage.getItem('chat_msgs_' + accountId)
    if (raw) {
      const arr = JSON.parse(raw)
      if (Array.isArray(arr)) return arr
    }
  } catch (e) { /* 忽略损坏数据 */ }
  return null
}
function persistMsgs(accountId) {
  const msgs = msgMap[accountId]
  if (!msgs) return
  // 只存文本（图片 base64 太大，刷新后不恢复）
  const slim = msgs.slice(-MAX_PERSIST).map(m => ({ role: m.role, content: m.content, time: m.time }))
  localStorage.setItem('chat_msgs_' + accountId, JSON.stringify(slim))
}
// 恢复/初始化某账户消息
function ensureMsgs(accountId) {
  if (!accountId || msgMap[accountId]) return
  const saved = loadMsgs(accountId)
  msgMap[accountId] = (saved && saved.length)
    ? saved
    : [{ role: 'assistant', content: '你好！我是你的智能助手，可以查行情、回测、搜索等，有什么可以帮你？', time: new Date() }]
}

// 消息变化 -> 防抖保存到 localStorage
let saveTimer = null
watch(currentMsgsSource, () => {
  if (!selectedId.value) return
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => persistMsgs(selectedId.value), 400)
}, { deep: true })

function currentMsgsSource() { return msgMap[selectedId.value] || [] }

const currentMsgs = computed(currentMsgsSource)
const recentName = computed(() => {
  const s = props.sessions.find(x => x.sessionId === lastChat.value)
  return s ? (s.label || s.shortId) : ''
})

watch(() => props.account, (v) => {
  selectedId.value = v || ''
  ensureMsgs(v)
})
if (props.account) ensureMsgs(props.account)

function onSelect() {
  emit('select', selectedId.value)
  ensureMsgs(selectedId.value)
}

// 新建对话：清空前端消息 + localStorage + 后端会话历史
async function newChat() {
  if (!selectedId.value) return
  msgMap[selectedId.value] = [
    { role: 'assistant', content: '新对话已创建，有什么可以帮你？', time: new Date() }
  ]
  localStorage.removeItem('chat_msgs_' + selectedId.value)
  try {
    await clearHistory(selectedId.value)
  } catch (e) { /* 忽略 */ }
}

// 智能滚动：仅当用户贴近底部时才自动跟随；上滑查看历史时不强制拉回
watch(currentMsgs, async () => {
  await nextTick()
  const el = scrollRef.value
  if (!el) return
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 100
  if (nearBottom) el.scrollTop = el.scrollHeight
}, { deep: true })

async function send(text) {
  if (!text.trim() || loading.value) return
  if (!selectedId.value) {
    msgMap['__unselected'] = [{ role: 'assistant', content: '请先在顶部选择一个已登录账户再开始聊天。', time: new Date() }]
    return
  }

  lastChat.value = selectedId.value
  localStorage.setItem('lastChat', selectedId.value)

  const msgs = msgMap[selectedId.value]
  msgs.push({ role: 'user', content: text, time: new Date() })
  const aiMsg = reactive({ role: 'assistant', content: '', time: new Date(), image: null, images: [] })
  msgs.push(aiMsg)
  loading.value = true

  try {
    await chatStream({
      msg: text,
      model: props.model,
      userId: selectedId.value,
      onDelta: (d) => { aiMsg.content += d },
      onImage: (img) => { aiMsg.images.push(img) },
      onLog: (raw) => {
        // 兼容旧版后端推送的字符串日志，统一转成结构化对象
        let log = raw
        if (typeof raw === 'string') {
          const p = String(raw).split('|')
          log = { kind: 'tool', model: '', msg: '工具 ' + (p[0] || ''), detail: p.slice(2).join('|'), status: p[1] === 'OK' ? 'OK' : 'FAIL' }
        }
        emit('log', { ...log, ts: Date.now() })
      }
    })
  } catch (e) {
    aiMsg.content = '⚠️ 请求失败：' + e.message
  } finally {
    loading.value = false
    persistMsgs(selectedId.value)
  }
}
</script>

<template>
  <div class="chat-content">
    <!-- 上 1/4：账户选择 + 新建对话 + 最近聊天（上下排列） -->
    <div class="chat-accounts">
      <div class="acc-row">
        <select v-model="selectedId" class="account-select" @change="onSelect">
          <option value="" disabled>选择已登录账户开始聊天</option>
          <option v-for="s in sessions" :key="s.sessionId" :value="s.sessionId">
            {{ s.label || s.shortId }}
          </option>
        </select>
        <button class="btn-new" :disabled="!selectedId" @click="newChat">＋ 新建对话</button>
      </div>
      <div class="recent">最近聊天：{{ recentName || '无' }}</div>
    </div>

    <!-- 下 3/4：对话区 -->
    <div class="chat-lower">
      <div class="chat-window" ref="scrollRef">
        <MessageBubble
          v-for="(m, i) in currentMsgs"
          :key="i"
          :message="m"
          :thinking="loading && i === currentMsgs.length - 1 && m.role === 'assistant' && !m.content"
          @image-click="(src) => viewerSrc = src"
        />
      </div>
      <InputBar :loading="loading" @send="send" />

      <!-- 图片放大查看器 -->
      <ImageViewer v-if="viewerSrc" :src="viewerSrc" @close="viewerSrc = ''" />
    </div>
  </div>
</template>
