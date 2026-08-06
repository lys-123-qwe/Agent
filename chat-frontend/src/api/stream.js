// SSE 流式接收：fetch + ReadableStream 逐段读取
// 事件类型：{delta} 文本增量、{image} 图片(base64/URL)、{log} 运行日志
export async function chatStream({ msg, model = '', userId = 'web', onDelta, onImage, onLog }) {
  const params = new URLSearchParams({ msg, userId })
  if (model) params.set('model', model)

  const resp = await fetch(`/api/chat/stream?${params}`, {
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
            if (obj && obj.delta != null && onDelta) onDelta(obj.delta)
            if (obj && obj.image && onImage) onImage(obj.image)
            if (obj && obj.log && onLog) onLog(obj.log)
          } catch (e) { /* 忽略非 JSON 片段 */ }
        }
      }
    }
  }
}
