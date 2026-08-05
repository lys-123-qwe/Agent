// 登录 / 账户管理 API 封装
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
