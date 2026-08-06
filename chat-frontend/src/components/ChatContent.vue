<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import MessageBubble from './MessageBubble.vue'
import InputBar from './InputBar.vue'
import { chatStream } from '../api/stream.js'

const props = defineProps({
  sessions: Array,
  model: String,
  account: String   // 当前账户（由左侧面板选中，全局唯一）
})
const emit = defineEmits(['log', 'select'])

// selectedId 跟随 account；对话区下拉选择时也同步回 App
const selectedId = ref(props.account || '')
watch(() => props.account, (v) => { selectedId.value = v || '' })

const lastChat = ref('')   // 真正最后发过消息的账户
const loading = ref(false)
const scrollRef = ref(null)
// 按账户隔离消息历史
const msgMap = reactive({})

const currentMsgs = computed(() => msgMap[selectedId.value] || [])
const recentName = computed(() => {
  const s = props.sessions.find(x => x.sessionId === lastChat.value)
  return s ? (s.label || s.shortId) : ''
})

function onSelect() {
  emit('select', selectedId.value)   // 同步回 App.activeId（左侧面板高亮、人设存储都用它）
  if (!msgMap[selectedId.value]) {
    msgMap[selectedId.value] = [
      { role: 'assistant', content: '你好！我是你的智能助手，可以查行情、回测、搜索等，有什么可以帮你？', time: new Date() }
    ]
  }
}

// 新建对话：仅清空当前账户的消息，不影响其他账户
function newChat() {
  if (!selectedId.value) return
  msgMap[selectedId.value] = [
    { role: 'assistant', content: '新对话已创建，有什么可以帮你？', time: new Date() }
  ]
}

watch(currentMsgs, async () => {
  await nextTick()
  if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
}, { deep: true })

async function send(text) {
  if (!text.trim() || loading.value) return
  if (!selectedId.value) {
    msgMap['__unselected'] = [{ role: 'assistant', content: '请先在顶部选择一个已登录账户再开始聊天。', time: new Date() }]
    return
  }

  lastChat.value = selectedId.value   // 记录最后聊天的账户

  const msgs = msgMap[selectedId.value]
  msgs.push({ role: 'user', content: text, time: new Date() })
  const aiMsg = reactive({ role: 'assistant', content: '', time: new Date(), image: null })
  msgs.push(aiMsg)
  loading.value = true

  try {
    await chatStream({
      msg: text,
      model: props.model,
      userId: selectedId.value,
      onDelta: (d) => { aiMsg.content += d },
      onImage: (img) => { aiMsg.image = img },
      onLog: (raw) => {
        // 兼容旧版后端推送的字符串日志（"tool|OK|detail"），统一转成结构化对象
        let log = raw
        if (typeof raw === 'string') {
          const p = String(raw).split('|')
          log = {
            kind: 'tool',
            model: '',
            msg: '工具 ' + (p[0] || ''),
            detail: p.slice(2).join('|'),
            status: p[1] === 'OK' ? 'OK' : 'FAIL'
          }
        }
        emit('log', { ...log, ts: Date.now() })
      }
    })
  } catch (e) {
    aiMsg.content = '⚠️ 请求失败：' + e.message
  } finally {
    loading.value = false
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
        />
      </div>
      <InputBar :loading="loading" @send="send" />
    </div>
  </div>
</template>
