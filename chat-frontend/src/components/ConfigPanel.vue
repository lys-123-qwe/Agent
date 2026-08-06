<script setup>
import { ref, watch } from 'vue'
import { addKnowledge, getPersona, savePersona } from '../api/auth.js'

const props = defineProps({
  userId: String
})

const knowledge = ref('')
const persona = ref('')
const kMsg = ref('')
const pMsg = ref('')

watch(() => props.userId, async (id) => {
  if (!id) return
  try {
    const r = await getPersona(id)
    persona.value = r.persona || ''
  } catch (e) { persona.value = '' }
})

async function onAddKnowledge() {
  if (!knowledge.value.trim()) return
  try {
    await addKnowledge(knowledge.value.trim())
    kMsg.value = '✓ 已加入知识库'
    knowledge.value = ''
    setTimeout(() => (kMsg.value = ''), 2000)
  } catch (e) {
    kMsg.value = '✗ ' + e.message
  }
}

async function onSavePersona() {
  if (!props.userId) {
    pMsg.value = '请先在右上角选择一个账户'
    return
  }
  try {
    await savePersona(props.userId, persona.value)
    pMsg.value = '✓ 人设已保存'
    setTimeout(() => (pMsg.value = ''), 2000)
  } catch (e) {
    pMsg.value = '✗ ' + e.message
  }
}
</script>

<template>
  <div class="panel config-panel">
    <div class="panel-title">⚙️ 知识库 / 人设</div>

    <div class="cfg-section">
      <label>📚 知识库</label>
      <textarea v-model="knowledge" rows="2" placeholder="输入一条知识，回车添加进知识库"></textarea>
      <div class="cfg-actions">
        <button class="btn-mini" @click="onAddKnowledge">添加知识</button>
        <span class="cfg-msg">{{ kMsg }}</span>
      </div>
    </div>

    <div class="cfg-section">
      <label>🤖 人设</label>
      <textarea v-model="persona" rows="3" placeholder="例如：你是一位幽默风趣的理财顾问，回答多用比喻"></textarea>
      <div class="cfg-actions">
        <button class="btn-mini" @click="onSavePersona">保存人设</button>
        <span class="cfg-msg">{{ pMsg }}</span>
      </div>
    </div>
  </div>
</template>
