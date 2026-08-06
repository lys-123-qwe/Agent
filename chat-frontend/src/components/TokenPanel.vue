<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { tokenStats } from '../api/auth.js'

const props = defineProps({
  sessions: Array
})

const stats = ref({ accounts: {}, models: {}, sources: {}, grandTotal: 0 })
let timer = null

// 每个模型/来源一个固定颜色
const COLORS = {
  'deepseek-v4-pro': '#3b82f6',
  'deepseek-chat': '#10b981',
  'deepseek-reasoner': '#f59e0b',
  'qwen-plus': '#8b5cf6',
  'qwen-turbo': '#ec4899',
  '金融分析': '#f43f5e',
  'embedding': '#06b6d4'
}
function colorOf(name) { return COLORS[name] || '#64748b' }

// 合并：对话模型消耗 + 工具/服务消耗
const usageList = computed(() => {
  const models = stats.value.models || {}
  const sources = stats.value.sources || {}
  const items = [
    ...Object.entries(models).map(([name, v]) => ({ name, total: v.total })),
    ...Object.entries(sources).map(([name, v]) => ({ name, total: v.total }))
  ]
  const grand = stats.value.grandTotal || 0
  return items
    .sort((a, b) => b.total - a.total)
    .map(i => ({ name: i.name, total: i.total, pct: grand > 0 ? Math.max(2, Math.round(i.total / grand * 100)) : 0 }))
})

function fmtVal(n) {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

async function refresh() {
  try {
    stats.value = await tokenStats()
  } catch (e) { /* 静默 */ }
}

onMounted(() => {
  refresh()
  timer = setInterval(refresh, 5000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="panel token-panel">
    <div class="panel-title">⛽ Token 消耗</div>

    <div class="token-body">
      <!-- 总消耗 -->
      <div class="token-total">
        <span class="tt-label">累计消耗</span>
        <span class="tt-num">{{ stats.grandTotal.toLocaleString() }}</span>
        <span class="tt-unit">tokens</span>
      </div>

      <!-- 对话模型 + 工具/服务消耗列表 -->
      <div class="model-list">
        <div v-for="m in usageList" :key="m.name" class="model-row">
          <span class="m-dot" :style="{ background: colorOf(m.name) }"></span>
          <span class="m-name" :title="m.name">{{ m.name }}</span>
          <div class="m-bar">
            <div
              class="m-bar-fill"
              :style="{ width: m.pct + '%', background: colorOf(m.name) }"
              :title="m.name + '：' + m.total.toLocaleString() + ' tokens'"
            ></div>
          </div>
          <span class="m-val">{{ fmtVal(m.total) }}</span>
        </div>
        <div v-if="!usageList.length" class="empty">暂无消耗记录</div>
      </div>
    </div>
  </div>
</template>
