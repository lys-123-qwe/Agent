// SSE 流式接收：fetch + ReadableStream 逐段读取
export async function chatStream(msg, onDelta) {
  const resp = await fetch(`/api/chat/stream?msg=${encodeURIComponent(msg)}`, {
    headers: { Accept: 'text/event-stream' }
  })
  if (!resp.ok) throw new Error('请求失败 ' + resp.status)

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // 按空行分割 SSE 事件
    const events = buffer.split('\n\n')
    buffer = events.pop() // 最后一段可能不完整

    for (const evt of events) {
      for (const line of evt.split('\n')) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data === '[DONE]') return
          try {
            const obj = JSON.parse(data)
            if (obj && obj.delta) onDelta(obj.delta)
          } catch (e) { /* 忽略非 JSON 片段 */ }
        }
      }
    }
  }
}
