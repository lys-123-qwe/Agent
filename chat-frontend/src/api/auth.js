// 登录 / 账户管理 / 配置 API 封装
export async function startLogin(label) {
  const r = await fetch(`/api/login?label=${encodeURIComponent(label)}`, { method: 'POST' })
  if (!r.ok) throw new Error('登录接口失败 ' + r.status)
  return r.json()
}

export async function loginStatus(sessionId) {
  const r = await fetch(`/api/login/status?sessionId=${encodeURIComponent(sessionId)}`)
  if (!r.ok) throw new Error('状态查询失败 ' + r.status)
  return r.json()
}

export async function listSessions() {
  const r = await fetch('/api/sessions')
  if (!r.ok) throw new Error('账户列表失败 ' + r.status)
  return r.json()
}

export async function deleteSession(sessionId) {
  const r = await fetch(`/api/sessions/${sessionId}`, { method: 'DELETE' })
  if (!r.ok) throw new Error('删除失败 ' + r.status)
  return r.json()
}

// ---- 控制台扩展 ----

export async function tokenStats() {
  const r = await fetch('/api/token-stats')
  if (!r.ok) throw new Error('token 统计失败 ' + r.status)
  return r.json()
}

export async function getPersona(userId) {
  const r = await fetch(`/api/config/persona?userId=${encodeURIComponent(userId)}`)
  if (!r.ok) throw new Error('人设查询失败 ' + r.status)
  return r.json()
}

export async function savePersona(userId, persona) {
  const r = await fetch(
    `/api/config/persona?userId=${encodeURIComponent(userId)}&persona=${encodeURIComponent(persona)}`,
    { method: 'POST' }
  )
  if (!r.ok) throw new Error('人设保存失败 ' + r.status)
  return r.json()
}

export async function addKnowledge(text) {
  const r = await fetch(`/api/config/knowledge?text=${encodeURIComponent(text)}`, { method: 'POST' })
  if (!r.ok) throw new Error('知识添加失败 ' + r.status)
  return r.json()
}
